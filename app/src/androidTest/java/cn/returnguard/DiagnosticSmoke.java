package cn.returnguard;
import android.app.*;
import android.os.*;
import android.view.accessibility.*;
import org.json.*;
import java.io.*;
/** Synthetic recorder/export checks, not proof of real Ctrip accessibility visibility. */
public final class DiagnosticSmoke extends Instrumentation {
 @Override public void onCreate(Bundle b){super.onCreate(b);start();}
 private void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 private AccessibilityEvent event(String cls){AccessibilityEvent e=AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);e.setPackageName("ctrip.android.view");e.setClassName(cls);e.getText().add("SECRET_PAGE_TEXT");e.setContentDescription("SECRET_DESCRIPTION");return e;}
 @Override public void onStart(){Bundle result=new Bundle();try{
  android.view.accessibility.AccessibilityManager manager=getTargetContext().getSystemService(android.view.accessibility.AccessibilityManager.class);
  boolean metadataFound=false;
  for(android.accessibilityservice.AccessibilityServiceInfo info:manager.getInstalledAccessibilityServiceList()){
   if("cn.returnguard".equals(info.getResolveInfo().serviceInfo.packageName)){metadataFound=true;check((info.flags&android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS)!=0,"effective Android service config requests node IDs");}
  }
  check(metadataFound,"installed accessibility service metadata exists");
  Prefs prefs=new Prefs(getTargetContext());prefs.data.edit().clear().putBoolean("enabled",true).putStringSet("sources",java.util.Collections.singleton("ctrip.android.view")).commit();
  DiagnosticRecorder recorder=DiagnosticRecorder.get(getTargetContext());
  AccessibilityEvent home=event("ctrip.android.publicproduct.home.view.CtripHomeActivity"),ad=event("ctrip.android.ad.webview.MktH5ContainerV2");
  recorder.window(home);check(recorder.snapshot().getJSONArray("events").length()==0,"off by default");
  recorder.start();recorder.window(home);recorder.window(ad);
  JSONObject data=recorder.snapshot();check(data.getInt("candidateCount")>0,"candidate captured");
  check(prefs.active(),"diagnosis does not alter protection state");
  String json=data.toString();check(!json.contains("SECRET"),"page text and descriptions never exported");
  AccessibilityEvent other=event("OtherPrivateClass");other.setPackageName("private.other.app");recorder.window(other);check(!recorder.snapshot().toString().contains("OtherPrivateClass"),"unrelated app excluded");
  for(int i=0;i<900;i++)recorder.window(ad);
  check(recorder.snapshot().getInt("droppedEvents")>0,"overflow disclosed");check(recorder.snapshot().getJSONArray("events").length()<=800,"bounded buffer");
  recorder.stop("test");int count=recorder.snapshot().getJSONArray("events").length();recorder.window(home);check(recorder.snapshot().getJSONArray("events").length()==count,"stop prevents recording");
  ByteArrayOutputStream out=new ByteArrayOutputStream();DiagnosticRecorder.write(recorder.snapshot(),out);check(new JSONObject(out.toString("UTF-8")).getString("mode").equals("observe_only"),"export JSON readable");
  recorder.start();check(recorder.snapshot().getInt("candidateCount")==0,"new session clears old candidate counts");recorder.stop("test_done");
  result.putString("stream","DIAGNOSTIC_SMOKE_PASSED: recorder, privacy, limits, export; synthetic events only\n");finish(Activity.RESULT_OK,result);
 }catch(Throwable e){result.putString("stream","DIAGNOSTIC_SMOKE_FAILED: "+android.util.Log.getStackTraceString(e));finish(Activity.RESULT_CANCELED,result);}}
}
