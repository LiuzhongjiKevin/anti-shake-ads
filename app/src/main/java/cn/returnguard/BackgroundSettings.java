package cn.returnguard;
import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import java.util.Locale;

/** OEM settings are best-effort shortcuts. Their permission state is never guessed. */
final class BackgroundSettings {
 static boolean xiaomi(){String n=(Build.MANUFACTURER+" "+Build.BRAND).toLowerCase(Locale.ROOT);return n.contains("xiaomi")||n.contains("redmi")||n.contains("poco");}
 static boolean huawei(){return Build.MANUFACTURER.equalsIgnoreCase("huawei")||Build.BRAND.equalsIgnoreCase("honor");}
 static boolean batteryExempt(Context c){PowerManager p=c.getSystemService(PowerManager.class);return p!=null&&p.isIgnoringBatteryOptimizations(c.getPackageName());}
 static boolean notifications(Context c){NotificationManager n=c.getSystemService(NotificationManager.class);NotificationChannel channel=n.getNotificationChannel(ProtectionNotificationService.CHANNEL);return n.areNotificationsEnabled()&&(channel==null||channel.getImportance()!=NotificationManager.IMPORTANCE_NONE);}
 static boolean accessibilityEnabled(Context c){String services=Settings.Secure.getString(c.getContentResolver(),Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);if(services==null)return false;ComponentName ours=new ComponentName(c,GuardService.class);for(String s:services.split(":")){if(ours.equals(ComponentName.unflattenFromString(s)))return true;}return false;}
 static Intent details(Context c){return new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+c.getPackageName()));}
 static boolean open(Activity a,Intent intent){try{a.startActivity(intent);return true;}catch(ActivityNotFoundException|SecurityException e){return false;}}
 static void openDetails(Activity a){if(!open(a,details(a)))open(a,new Intent(Settings.ACTION_SETTINGS));}
 static void accessibility(Activity a){open(a,new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));}
 static void notification(Activity a){if(!open(a,new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,a.getPackageName()).putExtra(Settings.EXTRA_CHANNEL_ID,ProtectionNotificationService.CHANNEL)))openDetails(a);}
 static void battery(Activity a){
  if(xiaomi()&&open(a,new Intent().setComponent(new ComponentName("com.miui.powerkeeper","com.miui.powerkeeper.ui.HiddenAppsConfigActivity")).putExtra("package_name",a.getPackageName()).putExtra("package_label",a.getString(R.string.app_name))))return;
  if(!open(a,new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)))openDetails(a);
 }
 static void autostart(Activity a){
  if(xiaomi()&&open(a,new Intent().setComponent(new ComponentName("com.miui.securitycenter","com.miui.permcenter.autostart.AutoStartManagementActivity"))))return;
  if(huawei()&&open(a,new Intent().setComponent(new ComponentName("com.huawei.systemmanager","com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"))))return;
  openDetails(a);
 }
}
