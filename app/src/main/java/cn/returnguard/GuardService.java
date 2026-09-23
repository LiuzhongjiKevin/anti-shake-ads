package cn.returnguard;
// Adapted from TouchHelperService.java, Copyright (c) 2020 Zhengfa Dang, MIT.
// See licenses/Android-Touch-Helper-MIT.txt.
import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.view.accessibility.AccessibilityEvent;
import java.lang.ref.WeakReference;
public final class GuardService extends AccessibilityService {
 private static WeakReference<GuardService> ref;private GuardController controller;
 @Override protected void onServiceConnected(){super.onServiceConnected();ref=new WeakReference<>(this);if(controller!=null)controller.close();controller=new GuardController(this);}
 @Override public void onAccessibilityEvent(AccessibilityEvent e){if(controller!=null&&e!=null)controller.onWindowEvent(e);}
 @Override public void onInterrupt(){if(controller!=null)controller.cancel();}
 @Override public boolean onUnbind(Intent i){cleanup();return super.onUnbind(i);}
 @Override public void onDestroy(){cleanup();super.onDestroy();}
 private void cleanup(){if(controller!=null){controller.close();controller=null;}ref=null;}
 static boolean running(){GuardService s=ref==null?null:ref.get();return s!=null&&s.controller!=null;}
}
