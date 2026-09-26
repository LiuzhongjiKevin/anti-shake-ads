package cn.returnguard;

import android.content.Context;
import android.os.*;
import android.view.accessibility.*;
import cn.returnguard.core.CtripProbe;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;

/** Opt-in metadata-only observer. It cannot execute Back or launch an Activity. */
final class DiagnosticRecorder {
 private static DiagnosticRecorder instance;
 private static final int LIMIT=800;
 private static final long DURATION=120000;
 private final Context context;
 private final Handler handler=new Handler(Looper.getMainLooper());
 private final ArrayDeque<JSONObject> rows=new ArrayDeque<>();
 private CtripProbe probe=new CtripProbe();
 private boolean recording;
 private String session="",stopReason="not_started";
 private long started,wallStarted,lastNode=-1;
 private int dropped,candidates,containers,unattributed;
 private final Runnable timeout=()->stop("time_limit");
 private DiagnosticRecorder(Context c){context=c.getApplicationContext();}
 static synchronized DiagnosticRecorder get(Context c){if(instance==null)instance=new DiagnosticRecorder(c);return instance;}
 synchronized void start(){
  handler.removeCallbacks(timeout);rows.clear();probe=new CtripProbe();dropped=candidates=containers=unattributed=0;lastNode=-1;
  started=SystemClock.elapsedRealtime();wallStarted=System.currentTimeMillis();session=UUID.randomUUID().toString();stopReason="";recording=true;
  add("session_start",new JSONObject());handler.postDelayed(timeout,DURATION);
 }
 synchronized boolean active(){if(recording&&SystemClock.elapsedRealtime()-started>=DURATION)stop("time_limit");return recording;}
 synchronized void stop(String reason){if(recording){recording=false;stopReason=reason;handler.removeCallbacks(timeout);add("session_stop",new JSONObject());}}
 private static String identifier(CharSequence text){if(text==null)return "";String s=text.toString();return s.substring(0,Math.min(s.length(),240));}
 private JSONObject status() throws JSONException {
  Prefs p=new Prefs(context);return new JSONObject().put("protectionEnabled",p.enabled()).put("protectionActive",p.active())
   .put("ctripSelected",p.sources().contains(CtripProbe.PACKAGE)).put("protectionWindowMs",p.window())
   .put("serviceConnected",GuardService.running()).put("foregroundServiceRunning",ProtectionNotificationService.running());
 }
 private void add(String kind,JSONObject row){try{
  row.put("sequence",dropped+rows.size()+1).put("kind",kind).put("elapsedRealtimeMs",SystemClock.elapsedRealtime()).put("wallTimeMs",System.currentTimeMillis());
  if(rows.size()==LIMIT){rows.removeFirst();dropped++;}rows.addLast(row);
 }catch(JSONException ignored){}}
 synchronized void window(AccessibilityEvent event){
  if(!active()||event==null)return;
  int type=event.getEventType();if(type!=AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED&&type!=AccessibilityEvent.TYPE_WINDOWS_CHANGED)return;
  if(event.getPackageName()==null){unattributed++;return;}
  if(!CtripProbe.PACKAGE.contentEquals(event.getPackageName()))return;
  try{
   long now=SystemClock.elapsedRealtime();String cls=identifier(event.getClassName());Prefs p=new Prefs(context);
   boolean container=CtripProbe.CONTAINER.equals(cls);
   boolean candidate=probe.observe(CtripProbe.PACKAGE,cls,p.sources().contains(CtripProbe.PACKAGE),p.active(),now,p.window());
   if(container)containers++;if(candidate)candidates++;
   JSONObject row=status().put("package",CtripProbe.PACKAGE).put("eventType",type).put("eventClass",cls)
    .put("windowId",event.getWindowId()).put("eventUptimeMs",event.getEventTime()).put("containerClassObserved",container)
    .put("candidate",candidate).put("classification","candidate_only_not_confirmed_ad");
   // Never traverse page text, descriptions, URLs, or screenshots. One source node at most per 250 ms.
   if(lastNode<0||now-lastNode>=250){lastNode=now;AccessibilityNodeInfo node=null;
    try{node=event.getSource();if(node!=null){row.put("sourceNodeClass",identifier(node.getClassName())).put("sourceNodeId",identifier(node.getViewIdResourceName()));}}
    catch(RuntimeException ex){row.put("nodeUnavailable",true);}finally{if(node!=null)node.recycle();}
   }
   add("window",row);
  }catch(RuntimeException|JSONException ignored){add("window_record_error",new JSONObject());}
 }
 synchronized void action(String source,String target,String kind,int attempt,String foreground,Boolean accepted){
  if(!active()||!CtripProbe.PACKAGE.equals(source))return;
  try{add("existing_cross_app_action",new JSONObject().put("action",kind).put("engineAttempt",attempt)
   .put("targetPackage",identifier(target)).put("foregroundPackage",identifier(foreground))
   .put("acceptedBySystem",accepted==null?JSONObject.NULL:accepted));}catch(JSONException ignored){}
 }
 synchronized void serviceState(String state){if(active())try{add("service_state",new JSONObject().put("state",state));}catch(JSONException ignored){}}
 synchronized JSONObject snapshot(){
  active();JSONObject result=new JSONObject();try{
   JSONArray events=new JSONArray();for(JSONObject row:rows)events.put(row);
   result.put("schemaVersion",1).put("appVersion","0.2.2-diagnostic").put("mode","observe_only").put("sessionId",session)
    .put("recording",recording).put("stopReason",stopReason).put("startedWallTimeMs",wallStarted).put("startedElapsedRealtimeMs",started)
    .put("durationLimitMs",DURATION).put("eventLimit",LIMIT).put("droppedEvents",dropped).put("candidateCount",candidates)
    .put("containerClassEventCount",containers).put("unattributedWindowEvents",unattributed)
    .put("stateAtExport",status()).put("sdk",Build.VERSION.SDK_INT).put("events",events)
    .put("limitations","Candidate counts count events, not ads. eventClass may be a View class, not an Activity. No page text, URL, screenshot or full system log. Buffer is memory-only and lost on process death. Existing cross-app protection remains independent; no same-app navigation is performed.");
  }catch(JSONException ignored){}return result;
 }
 static void write(JSONObject data,OutputStream out)throws IOException{try{out.write(data.toString(2).getBytes(StandardCharsets.UTF_8));out.flush();}catch(JSONException e){throw new IOException(e);}}
}
