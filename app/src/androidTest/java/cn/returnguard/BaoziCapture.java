package cn.returnguard;
import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.os.*;
import android.view.accessibility.*;
import java.io.*;
import java.util.*;
import org.json.*;
/** One-off, explicitly requested emulator observation. Not included in the production APK. */
public final class BaoziCapture extends Instrumentation {
 private Bundle args;
 @Override public void onCreate(Bundle b){super.onCreate(b);args=b;start();}
 private UiAutomation ui;
 private void shell(String cmd)throws Exception{try(ParcelFileDescriptor fd=ui.executeShellCommand(cmd);InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(fd)){byte[] b=new byte[4096];while(in.read(b)!=-1){}}}
 private void write(File f,String s)throws Exception{try(FileOutputStream o=new FileOutputStream(f)){o.write(s.getBytes("UTF-8"));}}
 private void nodes(AccessibilityNodeInfo n,JSONArray rows,int[] left){
  if(n==null||left[0]--<=0)return;
  try{JSONObject o=new JSONObject();o.put("class",String.valueOf(n.getClassName()));o.put("package",String.valueOf(n.getPackageName()));o.put("text",String.valueOf(n.getText()));o.put("description",String.valueOf(n.getContentDescription()));o.put("id",n.getViewIdResourceName());android.graphics.Rect r=new android.graphics.Rect();n.getBoundsInScreen(r);o.put("bounds",r.toShortString());rows.put(o);
   for(int i=0;i<n.getChildCount()&&left[0]>0;i++){AccessibilityNodeInfo c=n.getChild(i);if(c!=null){nodes(c,rows,left);c.recycle();}}
  }catch(Exception ignored){}
 }
 private boolean consent(AccessibilityNodeInfo n){
  if(n==null)return false;
  String t=n.getText()==null?"":n.getText().toString().trim();
  if(Arrays.asList("同意","同意并继续","同意并进入","同意并使用","同意并接受","同意并开始使用").contains(t)){
   AccessibilityNodeInfo p=AccessibilityNodeInfo.obtain(n);
   for(int k=0;k<4&&p!=null;k++){if(p.isClickable()){boolean ok=p.performAction(AccessibilityNodeInfo.ACTION_CLICK);p.recycle();return ok;}AccessibilityNodeInfo q=p.getParent();p.recycle();p=q;}
  }
  for(int i=0;i<n.getChildCount();i++){AccessibilityNodeInfo c=n.getChild(i);if(c!=null){boolean ok=consent(c);c.recycle();if(ok)return true;}}
  return false;
 }
 @Override public void onStart(){Bundle result=new Bundle();try{
  ui=getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
  String pkg=args.getString("package");File dir=new File(getTargetContext().getFilesDir(),"baozi-capture");dir.mkdirs();
  if("setup".equals(args.getString("mode"))){
   Prefs p=new Prefs(getTargetContext());p.data.edit().clear().putBoolean("enabled",true).putBoolean("background_guide_seen",true).putInt("seconds",30).commit();p.saveSources(new HashSet<>(Arrays.asList(pkg)));
   for(String name:new String[]{pkg,"cn.returnguard"}){Drawable d=getTargetContext().getPackageManager().getApplicationIcon(name);Bitmap b=Bitmap.createBitmap(256,256,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(b);d.setBounds(0,0,256,256);d.draw(canvas);try(FileOutputStream f=new FileOutputStream(new File(dir,name+".png"))){b.compress(Bitmap.CompressFormat.PNG,100,f);}b.recycle();}
   result.putString("stream","CAPTURE_SETUP_OK\n");finish(Activity.RESULT_OK,result);return;
  }
  // Starting instrumentation restarts the target process: clear the stale crashed binding before reconnecting.
  shell("settings put secure enabled_accessibility_services null");shell("settings put secure accessibility_enabled 0");SystemClock.sleep(500);
  shell("settings put secure enabled_accessibility_services cn.returnguard/.GuardService");shell("settings put secure accessibility_enabled 1");
  shell("am start -n cn.returnguard/.MainActivity");
  long ready=SystemClock.elapsedRealtime()+15000;while(!GuardService.running()&&SystemClock.elapsedRealtime()<ready)SystemClock.sleep(100);
  if(!GuardService.running())throw new IllegalStateException("Guard accessibility did not connect");
  runOnMainSync(()->ProtectionNotificationService.start(getTargetContext()));
  SystemClock.sleep(500);
  Intent launch=getTargetContext().getPackageManager().getLaunchIntentForPackage(pkg);if(launch==null)throw new IllegalStateException("No launcher for "+pkg);
  String component=launch.getComponent().flattenToString();JSONArray frames=new JSONArray(),actions=new JSONArray();
  long start=SystemClock.elapsedRealtime();shell("am start -n "+component);
  for(int i=0;i<75;i++){
   long wait=start+i*1000L-SystemClock.elapsedRealtime();if(wait>0)SystemClock.sleep(wait);
   long actual=SystemClock.elapsedRealtime()-start;String file=String.format(Locale.ROOT,"frame-%03d.png",i);
   Bitmap b=ui.takeScreenshot();if(b==null)throw new IllegalStateException("Screenshot unavailable at "+i);
   try(FileOutputStream f=new FileOutputStream(new File(dir,file))){b.compress(Bitmap.CompressFormat.PNG,100,f);}b.recycle();
   AccessibilityNodeInfo root=ui.getRootInActiveWindow();JSONArray tree=new JSONArray();nodes(root,tree,new int[]{300});
   write(new File(dir,String.format(Locale.ROOT,"ui-%03d.json",i)),tree.toString(2));
   JSONObject frame=new JSONObject();frame.put("index",i);frame.put("actual_ms",actual);frame.put("file",file);frame.put("guard_connected",GuardService.running());frame.put("foreground_service",ProtectionNotificationService.running());frame.put("guard_enabled",new Prefs(getTargetContext()).enabled());frames.put(frame);
   if(i<14&&root!=null&&pkg.contentEquals(root.getPackageName()==null?"":root.getPackageName())&&consent(root)){JSONObject a=new JSONObject();a.put("second",i);a.put("action","Clicked exact consent button on isolated test app");actions.put(a);}
   if(root!=null)root.recycle();
   if(i==14){shell("am force-stop "+pkg);shell("input keyevent KEYCODE_HOME");}
   if(i==15){shell("am start -n "+component);JSONObject a=new JSONObject();a.put("second",i);a.put("action","Cold launch after initial onboarding");actions.put(a);}
  }
  write(new File(dir,"frames.json"),frames.toString(2));write(new File(dir,"actions.json"),actions.toString(2));write(new File(dir,"guard-events.json"),new EventLog(getTargetContext()).read().toString(2));
  result.putString("stream","CAPTURE_OBSERVATION_OK frames=75\n");finish(Activity.RESULT_OK,result);
 }catch(Throwable e){result.putString("stream",android.util.Log.getStackTraceString(e));finish(Activity.RESULT_CANCELED,result);}}
}
