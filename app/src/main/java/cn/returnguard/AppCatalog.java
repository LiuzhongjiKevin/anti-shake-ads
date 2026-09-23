package cn.returnguard;
// Adapted from Android-Touch-Helper updatePackage(), Copyright (c) 2020 Zhengfa Dang.
// MIT license: licenses/Android-Touch-Helper-MIT.txt.
import android.content.*;
import android.content.pm.*;
import android.view.inputmethod.*;
import java.util.*;
final class AppCatalog {
 static final class Entry {final String pkg,label;Entry(String p,String l){pkg=p;label=l;}String display(){return label+"\n"+pkg;}}
 static List<Entry> apps(Context c){
  PackageManager pm=c.getPackageManager();Map<String,Entry> unique=new HashMap<>();Set<String> skip=exclusions(c);
  Intent i=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
  for(ResolveInfo r:pm.queryIntentActivities(i,PackageManager.MATCH_ALL)){String p=r.activityInfo.packageName;if(!skip.contains(p))unique.put(p,new Entry(p,r.loadLabel(pm).toString()));}
  List<Entry> out=new ArrayList<>(unique.values());out.sort((a,b)->a.label.compareToIgnoreCase(b.label));return out;
 }
 static Set<String> launchers(Context c){Set<String> out=new HashSet<>();for(ResolveInfo r:c.getPackageManager().queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),PackageManager.MATCH_ALL))out.add(r.activityInfo.packageName);return out;}
 static Set<String> exclusions(Context c){
  Set<String> out=new HashSet<>(Arrays.asList(c.getPackageName(),"android","com.android.systemui","com.android.settings","com.android.permissioncontroller","com.google.android.permissioncontroller","com.android.packageinstaller","com.google.android.packageinstaller","com.android.server.telecom"));
  out.addAll(Arrays.asList("com.miui.securitycenter","com.miui.powerkeeper","com.miui.securitycore","com.miui.home","com.mi.android.globallauncher","com.lbe.security.miui","com.huawei.systemmanager","com.huawei.android.launcher","com.hihonor.systemmanager"));
  PackageManager pm=c.getPackageManager();
  out.addAll(launchers(c));
  InputMethodManager im=(InputMethodManager)c.getSystemService(Context.INPUT_METHOD_SERVICE);if(im!=null)for(InputMethodInfo info:im.getInputMethodList())out.add(info.getPackageName());
  for(String action:Arrays.asList(android.provider.Settings.ACTION_SETTINGS,Intent.ACTION_DIAL)){ResolveInfo r=pm.resolveActivity(new Intent(action),PackageManager.MATCH_DEFAULT_ONLY);if(r!=null&&r.activityInfo!=null)out.add(r.activityInfo.packageName);}
  return out;
 }
 static String label(Context c,String p){if(p==null)return "未知应用";try{return c.getPackageManager().getApplicationLabel(c.getPackageManager().getApplicationInfo(p,0)).toString();}catch(PackageManager.NameNotFoundException e){return p;}}
}
