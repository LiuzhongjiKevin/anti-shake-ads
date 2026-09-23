package cn.returnguard;
import android.content.*;
public final class BootReceiver extends BroadcastReceiver {
 @Override public void onReceive(Context c,Intent i){String a=i.getAction();if((Intent.ACTION_BOOT_COMPLETED.equals(a)||Intent.ACTION_MY_PACKAGE_REPLACED.equals(a))&&new Prefs(c).enabled()&&BackgroundSettings.accessibilityEnabled(c))ProtectionNotificationService.start(c);}
}
