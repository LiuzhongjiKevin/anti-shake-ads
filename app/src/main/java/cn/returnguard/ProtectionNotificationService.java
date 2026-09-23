package cn.returnguard;
import android.app.*;
import android.content.*;
import android.os.*;
import android.content.pm.ServiceInfo;
import java.lang.ref.WeakReference;

/** User-visible protection session and controls. Does not acquire wakelocks or hide itself. */
public final class ProtectionNotificationService extends Service implements SharedPreferences.OnSharedPreferenceChangeListener {
 static final String CHANNEL="protection";
 static final String PAUSE="cn.returnguard.PAUSE",STOP="cn.returnguard.STOP";
 private static WeakReference<ProtectionNotificationService> instance;
 private final Handler handler=new Handler(Looper.getMainLooper());
 private final Runnable refresh=this::refresh;
 private Prefs prefs;
 private boolean foreground;
 static boolean running(){return instance!=null&&instance.get()!=null&&instance.get().foreground;}
 static boolean start(Context c){
  if(!new Prefs(c).enabled())return false;
  try{c.startForegroundService(new Intent(c,ProtectionNotificationService.class));return true;}
  catch(RuntimeException e){return false;}
 }
 static void stop(Context c){c.stopService(new Intent(c,ProtectionNotificationService.class));}
 static void statusChanged(){ProtectionNotificationService s=instance==null?null:instance.get();if(s!=null)s.refresh();}
 @Override public void onCreate(){super.onCreate();prefs=new Prefs(this);instance=new WeakReference<>(this);
  NotificationChannel ch=new NotificationChannel(CHANNEL,"保护运行状态",NotificationManager.IMPORTANCE_LOW);ch.setDescription("显示保护、暂停与服务断开状态");ch.setShowBadge(false);getSystemService(NotificationManager.class).createNotificationChannel(ch);
  prefs.data.registerOnSharedPreferenceChangeListener(this);
 }
 @Override public int onStartCommand(Intent i,int flags,int id){
  // Promote immediately even if a stale pending action arrives after the user stopped.
  show();
  if(i!=null&&STOP.equals(i.getAction())){prefs.stop();}
  if(!prefs.enabled()){stopForeground(STOP_FOREGROUND_REMOVE);foreground=false;stopSelf();return START_NOT_STICKY;}
  if(i!=null&&PAUSE.equals(i.getAction())){if(prefs.paused())prefs.resume();else prefs.pause();}
  refresh();return START_STICKY;
 }
 private PendingIntent action(String a,int id){return PendingIntent.getService(this,id,new Intent(this,ProtectionNotificationService.class).setAction(a),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
 private void show(){
  PendingIntent home=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
  Notification.Builder b=new Notification.Builder(this,CHANNEL).setSmallIcon(R.drawable.ic_notification).setContentTitle(ProtectionStatus.title(prefs)).setContentText(ProtectionStatus.detail(prefs)).setStyle(new Notification.BigTextStyle().bigText(ProtectionStatus.detail(prefs))).setContentIntent(home).setOngoing(true).setOnlyAlertOnce(true).setShowWhen(false).setCategory(Notification.CATEGORY_SERVICE);
  b.addAction(new Notification.Action.Builder(null,prefs.paused()?"恢复保护":"暂停一分钟",action(PAUSE,1)).build());
  b.addAction(new Notification.Action.Builder(null,"关闭保护",action(STOP,2)).build());
  if(Build.VERSION.SDK_INT>=34)startForeground(7,b.build(),ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);else startForeground(7,b.build());foreground=true;
 }
 private void refresh(){handler.removeCallbacks(refresh);if(!prefs.enabled()){stopForeground(STOP_FOREGROUND_REMOVE);foreground=false;stopSelf();return;}show();if(prefs.paused())handler.postDelayed(refresh,Math.max(100,prefs.data.getLong("pauseUntil",0)-SystemClock.elapsedRealtime()+50));}
 @Override public void onSharedPreferenceChanged(SharedPreferences p,String key){refresh();}
 @Override public void onDestroy(){handler.removeCallbacksAndMessages(null);prefs.data.unregisterOnSharedPreferenceChangeListener(this);foreground=false;instance=null;super.onDestroy();}
 @Override public IBinder onBind(Intent i){return null;}
}
