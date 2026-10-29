package cn.returnguard.core;
import java.util.*;
public final class AdPageClassifierTest {
 private static int count;
 private static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);count++;}
 public static void main(String[] args){
  AdPageClassifier built=new AdPageClassifier();
  for(String c:AdPageClassifier.BUILT_IN)check(built.landing(c),"built-in "+c);
  check(!built.landing(null),"null is not an ad page");
  check(!built.landing(""),"empty is not an ad page");
  check(!built.landing("com.app.MainActivity"),"ordinary Activity");
  check(!built.landing("com.bytedance.sdk.openadsdk.activity.TTRewardVideoActivity"),"reward video is user-chosen, not a landing page");
  check(!built.landing("com.qq.e.ads.ADActivityX"),"exact rules do not match longer names");
  AdPageClassifier custom=new AdPageClassifier(Arrays.asList(" ctrip.android.ad.webview.MktH5ContainerV2 ","com.example.ads.*","*","",null));
  check(custom.landing("ctrip.android.ad.webview.MktH5ContainerV2"),"custom exact rule trimmed");
  check(custom.landing("com.example.ads.Landing"),"custom prefix rule");
  check(!custom.landing("com.example.adsx"),"prefix rule keeps its separator");
  check(!custom.landing("com.app.MainActivity"),"lone '*' never matches everything");
  check(custom.landing(AdPageClassifier.BUILT_IN.get(0)),"custom rules keep built-in rules");
  System.out.println("Ad page classifier: "+count+" checks passed");
 }
}
