package cn.returnguard;
// Adapted from TouchHelperService.java, Copyright (c) 2020 Zhengfa Dang, MIT.
// See licenses/Android-Touch-Helper-MIT.txt.
import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.view.accessibility.AccessibilityEvent;
import java.lang.ref.WeakReference;
public final class GuardService extends AccessibilityService {
 private static WeakReference<GuardService> ref;private GuardController controller;
 @Override protected void onServiceConnected(){super.onServiceConnected();ref=new WeakReference<>(this);DiagnosticRecorder.get(this).serviceState("connected");if(controller!=null)controller.close();controller=new GuardController(this);if(new Prefs(this).enabled())ProtectionNotificationService.start(this);ProtectionNotificationService.statusChanged();}
 @Override public void onAccessibilityEvent(AccessibilityEvent e){DiagnosticRecorder.get(this).window(e);if(controller!=null&&e!=null)controller.onWindowEvent(e);}
 @Override public void onInterrupt(){if(controller!=null)controller.cancel();}
 @Override public boolean onUnbind(Intent i){cleanup();return super.onUnbind(i);}
 @Override public void onDestroy(){cleanup();super.onDestroy();}
 private void cleanup(){DiagnosticRecorder.get(this).serviceState("disconnected");if(controller!=null){controller.close();controller=null;}ref=null;ProtectionNotificationService.statusChanged();}
 static boolean running(){GuardService s=ref==null?null:ref.get();return s!=null&&s.controller!=null;}
}
