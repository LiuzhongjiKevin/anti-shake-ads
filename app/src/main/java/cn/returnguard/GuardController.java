package cn.returnguard;
import android.accessibilityservice.AccessibilityService;
import android.app.KeyguardManager;
import android.content.*;
import android.os.*;
import android.view.accessibility.*;
import cn.returnguard.core.GuardEngine;
import java.util.*;
/** Serialized on the main thread. Never reads page text. */
final class GuardController implements SharedPreferences.OnSharedPreferenceChangeListener {
 private final GuardService service;private final Prefs prefs;private final EventLog log;
 private final Handler handler=new Handler(Looper.getMainLooper());private Set<String> excluded;private GuardEngine engine;
 private boolean closed,queued;private String pendingSource,pendingTarget;private long pendingAt;private int pendingAttempts;
 private final Runnable sample=this::sample;
 private final BroadcastReceiver screenReceiver=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){cancel();}};
 GuardController(GuardService s){service=s;prefs=new Prefs(s);log=new EventLog(s);excluded=AppCatalog.exclusions(s);engine=new GuardEngine(prefs.window(),prefs.fallback());prefs.data.registerOnSharedPreferenceChangeListener(this);IntentFilter f=new IntentFilter(Intent.ACTION_SCREEN_OFF);if(Build.VERSION.SDK_INT>=33)s.registerReceiver(screenReceiver,f,Context.RECEIVER_NOT_EXPORTED);else s.registerReceiver(screenReceiver,f);}
 void onWindowEvent(AccessibilityEvent e){if(!closed&&(e.getEventType()==AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED||e.getEventType()==AccessibilityEvent.TYPE_WINDOWS_CHANGED))queue(40);}
 private void queue(long delay){if(!queued&&!closed){queued=true;handler.postDelayed(sample,delay);}}
 private String activePackage(){
  try{
   KeyguardManager km=(KeyguardManager)service.getSystemService(Context.KEYGUARD_SERVICE);if(km!=null&&km.isKeyguardLocked())return null;
   PowerManager pm=(PowerManager)service.getSystemService(Context.POWER_SERVICE);if(pm!=null&&!pm.isInteractive())return null;
   String active=null;
   for(AccessibilityWindowInfo w:service.getWindows()){
    if(w.isFocused()&&w.getType()!=AccessibilityWindowInfo.TYPE_APPLICATION)return null;
    if(!w.isActive()||!w.isFocused()||w.getType()!=AccessibilityWindowInfo.TYPE_APPLICATION)continue;
    if(w.isInPictureInPictureMode())return null;
    AccessibilityNodeInfo root=w.getRoot();if(root==null)return null;CharSequence name=root.getPackageName();String p=name==null?null:name.toString();root.recycle();
    if(p==null||(active!=null&&!active.equals(p)))return null;active=p;
   }return active;
  }catch(RuntimeException e){return null;}
 }
 private void sample(){queued=false;if(closed)return;if(!prefs.active()){cancel();return;}String p=activePackage();handle(engine.update(p,p!=null&&excluded.contains(p),p!=null&&prefs.sources().contains(p),prefs.allowed(engine.source(),p),SystemClock.elapsedRealtime()));if(engine.recovering())queue(100);}
 private void handle(GuardEngine.Action a){
  if(a==GuardEngine.Action.NONE)return;long now=SystemClock.elapsedRealtime();
  if(a==GuardEngine.Action.BACK||a==GuardEngine.Action.RELAUNCH){
   if(pendingSource==null){pendingSource=engine.source();pendingTarget=engine.target();pendingAt=now;}pendingAttempts=engine.attempts();
   String current=activePackage();if(!prefs.active()||current==null||excluded.contains(current)){cancel();return;}
   if(!current.equals(engine.target())){handle(engine.tick(current,now));return;}
   try{if(a==GuardEngine.Action.BACK)service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK);
    else{Intent launch=service.getPackageManager().getLaunchIntentForPackage(engine.source());if(launch==null){finish("重新打开失败：来源无启动入口");engine.reset();return;}launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);service.startActivity(launch);}
   }catch(RuntimeException e){finish("系统未执行返回／重新打开");engine.reset();}return;
  }
  switch(a){
   case RETURNED:finish("已回到原应用（页面需人工确认）");break;
   case REOPENED:finish("已重新打开来源（不代表恢复原页面）");break;
   case FAILED:if(pendingSource==null){pendingSource=engine.source();pendingTarget=engine.target();pendingAt=now;pendingAttempts=engine.attempts();}finish("未恢复：已达到返回上限");break;
   case CANCELLED:finish("已停止：窗口变化、超时或保护暂停");break;
   default:break;
  }
 }
 private void finish(String result){if(pendingSource!=null)log.add(pendingSource,pendingTarget,result,pendingAttempts,SystemClock.elapsedRealtime()-pendingAt);pendingSource=pendingTarget=null;pendingAttempts=0;}
 void cancel(){handler.removeCallbacks(sample);queued=false;finish("已停止：保护取消或系统窗口介入");engine.reset();}
 @Override public void onSharedPreferenceChanged(SharedPreferences p,String key){cancel();engine=new GuardEngine(prefs.window(),prefs.fallback());excluded=AppCatalog.exclusions(service);}
 void close(){closed=true;cancel();prefs.data.unregisterOnSharedPreferenceChangeListener(this);try{service.unregisterReceiver(screenReceiver);}catch(IllegalArgumentException ignored){}}
}
