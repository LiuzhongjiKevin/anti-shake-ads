package cn.returnguard.core;
/** Diagnostic classification only: never issues a navigation action. */
public final class CtripProbe {
 public static final String PACKAGE="ctrip.android.view";
 public static final String CONTAINER="ctrip.android.ad.webview.MktH5ContainerV2";
 private long startup=-1,last=-1;
 public boolean observe(String pkg,String cls,boolean selected,boolean active,long now,long windowMs){
  if(now<last){startup=-1;last=now;return false;}last=now;
  if(!PACKAGE.equals(pkg))return false;
  if("ctrip.business.splash.CtripSplashActivity".equals(cls))startup=now;
  else if(startup<0&&"ctrip.android.publicproduct.home.view.CtripHomeActivity".equals(cls))startup=now;
  return CONTAINER.equals(cls)&&selected&&active&&startup>=0&&now-startup<=windowMs;
 }
}
