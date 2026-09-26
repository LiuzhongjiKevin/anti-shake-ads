package cn.returnguard.core;
public final class CtripProbeTest {
 public static void main(String[] args){
  String p="ctrip.android.view", h="ctrip.android.publicproduct.home.view.CtripHomeActivity", ad="ctrip.android.ad.webview.MktH5ContainerV2";
  CtripProbe probe=new CtripProbe();
  assert !probe.observe(p,ad,true,true,100,10000):"No startup evidence must not qualify";
  probe.observe(p,h,true,true,1000,10000);
  assert probe.observe(p,ad,true,true,3191,10000):"Observed startup then exact container must qualify";
  assert !probe.observe(p,"android.webkit.WebView",true,true,3200,10000):"Generic WebView is not an ad rule";
  assert !probe.observe("other.app",ad,true,true,3300,10000):"Class alone must not match";
  assert !probe.observe(p,ad,false,true,3400,10000):"Unselected source must not qualify";
  assert !probe.observe(p,ad,true,false,3500,10000):"Paused/disabled protection must not qualify";
  probe.observe(p,h,true,true,10900,10000);
  assert !probe.observe(p,ad,true,true,11001,10000):"Repeated home event must not renew startup window";
  probe.observe(p,"ctrip.business.splash.CtripSplashActivity",true,true,20000,10000);
  assert probe.observe(p,ad,true,true,21000,10000):"New splash starts a new observation window";
  assert !probe.observe(p,ad,true,true,19000,10000):"Clock rollback invalidates context";
  System.out.println("Ctrip diagnostic candidate: 9 checks passed");
 }
}
