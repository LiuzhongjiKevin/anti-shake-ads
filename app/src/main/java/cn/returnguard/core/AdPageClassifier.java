package cn.returnguard.core;
import java.util.*;

/**
 * Recognises ad landing-page Activities that open inside the protected app.
 * Built-in names are ad SDK landing pages recalled from public SDK manifests and are NOT yet
 * confirmed on Xiaomi devices; confirm them with a diagnostic export. Reward/full-screen video
 * Activities are deliberately absent: the user may have chosen to watch those.
 * A rule ending in '*' is a prefix rule; any other rule must equal the Activity class name.
 */
public final class AdPageClassifier {
 public static final List<String> BUILT_IN=Collections.unmodifiableList(Arrays.asList(
  "com.bytedance.sdk.openadsdk.activity.TTLandingPageActivity",
  "com.bytedance.sdk.openadsdk.activity.TTVideoLandingPageActivity",
  "com.qq.e.ads.ADActivity",
  "com.kwad.sdk.api.proxy.app.AdWebViewActivity",
  "com.baidu.mobads.sdk.api.AppActivity"));
 private final Set<String> exact=new HashSet<>();private final List<String> prefixes=new ArrayList<>();
 public AdPageClassifier(){this(Collections.<String>emptySet());}
 public AdPageClassifier(Collection<String> custom){for(String r:BUILT_IN)add(r);for(String r:custom)add(r);}
 private void add(String rule){if(rule==null)return;String r=rule.trim();if(r.isEmpty()||r.equals("*"))return;if(r.endsWith("*"))prefixes.add(r.substring(0,r.length()-1));else exact.add(r);}
 public boolean landing(String activity){
  if(activity==null||activity.isEmpty())return false;if(exact.contains(activity))return true;
  for(String p:prefixes)if(activity.startsWith(p))return true;return false;
 }
}
