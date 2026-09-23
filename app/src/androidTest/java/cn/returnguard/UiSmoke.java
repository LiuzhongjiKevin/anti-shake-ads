package cn.returnguard;
import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/** Platform-only instrumentation; shell grants are test setup, never production behavior. */
public final class UiSmoke extends Instrumentation {
 private MainActivity activity;private Prefs prefs;private UiAutomation automation;
 private final StringBuilder evidence=new StringBuilder();
 @Override public void onCreate(Bundle b){super.onCreate(b);start();}
 private void check(boolean value,String label){if(!value)throw new AssertionError(label);evidence.append("PASS ").append(label).append('\n');}
 private void shell(String command)throws Exception{try(ParcelFileDescriptor fd=automation.executeShellCommand(command);InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(fd)){byte[] bytes=new byte[2048];while(in.read(bytes)!=-1){}}}
 private void idle(){waitForIdleSync();SystemClock.sleep(450);}
 private void waitFor(java.util.function.BooleanSupplier condition,String label){long end=SystemClock.elapsedRealtime()+15000;while(SystemClock.elapsedRealtime()<end){if(condition.getAsBoolean()){check(true,label);return;}SystemClock.sleep(200);}check(false,label);}
 private void tap(){runOnMainSync(()->activity.findViewById(R.id.protection_button).performClick());idle();}
 private String notification(){NotificationManager m=getTargetContext().getSystemService(NotificationManager.class);for(android.service.notification.StatusBarNotification n:m.getActiveNotifications())if(n.getId()==7)return String.valueOf(n.getNotification().extras.getCharSequence(Notification.EXTRA_TITLE));return "";}
 @Override public void onStart(){Bundle result=new Bundle();try{
  automation=getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
  shell("settings put secure enabled_accessibility_services null");shell("settings put secure accessibility_enabled 0");
  if(Build.VERSION.SDK_INT>=33){shell("pm grant cn.returnguard android.permission.POST_NOTIFICATIONS");shell("appops set cn.returnguard ACCESS_RESTRICTED_SETTINGS allow");}
  prefs=new Prefs(getTargetContext());prefs.data.edit().clear().putBoolean("background_guide_seen",true).commit();
  activity=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));idle();
  check(!prefs.enabled(),"fresh install is off");tap();SystemClock.sleep(1000);sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);idle();
  check(!prefs.enabled()&&!prefs.data.getBoolean("setup_requested",false),"cancel app chooser leaves protection off");
  runOnMainSync(()->prefs.saveSources(new HashSet<>(Arrays.asList("cn.returnguard.fixture.source"))));tap();sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);idle();
  check(!prefs.enabled()&&!prefs.data.getBoolean("setup_requested",false),"cancel accessibility explanation does not enable protection");
  shell("settings put secure enabled_accessibility_services cn.returnguard/.GuardService");shell("settings put secure accessibility_enabled 1");waitFor(GuardService::running,"accessibility service connects");
  tap();waitFor(ProtectionNotificationService::running,"main button starts foreground session");check(prefs.enabled(),"enable preference persists");check(notification().equals("保护已开启"),"notification reports connected protection");
  getTargetContext().startService(new Intent(getTargetContext(),ProtectionNotificationService.class).setAction(ProtectionNotificationService.PAUSE));idle();check(prefs.paused()&&notification().equals("保护已暂停"),"notification pause action pauses protection");
  tap();idle();check(!prefs.paused()&&prefs.enabled(),"main button resumes paused protection");
  getTargetContext().startService(new Intent(getTargetContext(),ProtectionNotificationService.class).setAction(ProtectionNotificationService.STOP));waitFor(()->!ProtectionNotificationService.running(),"notification stop removes foreground service");check(!prefs.enabled(),"stop persists disabled preference");
  getTargetContext().startForegroundService(new Intent(getTargetContext(),ProtectionNotificationService.class));idle();check(!ProtectionNotificationService.running()&&!prefs.enabled(),"stale restart cannot override user stop");
  tap();waitFor(ProtectionNotificationService::running,"main button re-enables protection");
  if(Build.VERSION.SDK_INT>=33){shell("appops set cn.returnguard POST_NOTIFICATION ignore");idle();check(!BackgroundSettings.notifications(getTargetContext())&&ProtectionNotificationService.running(),"denied notification permission does not masquerade as visible notification");shell("appops set cn.returnguard POST_NOTIFICATION allow");}
  shell("settings put secure enabled_accessibility_services null");shell("settings put secure accessibility_enabled 0");waitFor(()->!GuardService.running(),"accessibility disconnect detected");idle();check(notification().equals("需要开启无障碍"),"disconnected service is not reported as protected");
  runOnMainSync(()->prefs.data.edit().putBoolean("setup_requested",true).apply());
  getTargetContext().startService(new Intent(getTargetContext(),ProtectionNotificationService.class).setAction(ProtectionNotificationService.STOP));waitFor(()->!ProtectionNotificationService.running(),"stop works during accessibility repair");
  check(!prefs.data.getBoolean("setup_requested",false),"stop clears pending permission repair");
  shell("settings put secure enabled_accessibility_services cn.returnguard/.GuardService");shell("settings put secure accessibility_enabled 1");waitFor(GuardService::running,"accessibility can reconnect");SystemClock.sleep(1500);
  check(!prefs.enabled()&&!ProtectionNotificationService.running(),"permission reconnect cannot undo explicit stop");tap();waitFor(ProtectionNotificationService::running,"explicit enable after repair works");
  runOnMainSync(()->prefs.data.edit().putInt("seconds",30).apply());idle();
  result.putString("stream",evidence+"UI_LIFECYCLE_PASSED\n");finish(Activity.RESULT_OK,result);
 }catch(Throwable e){result.putString("stream",evidence+"UI_LIFECYCLE_FAILED: "+android.util.Log.getStackTraceString(e));finish(Activity.RESULT_CANCELED,result);}}
}
