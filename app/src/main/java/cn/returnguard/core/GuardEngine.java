package cn.returnguard.core;

/**
 * Pure state machine; caller verifies foreground immediately before every action.
 * Activity classes are optional. Pass null whenever the class has not been reported since the last
 * action: a stale ad class would otherwise look like a page that still needs Back.
 */
public final class GuardEngine {
 public enum Action { NONE, BACK, RELAUNCH, RETURNED, REOPENED, FAILED, CANCELLED }
 static final long SOURCE_STABLE_MS=120,SCREEN_STABLE_MS=400,MAX_WAIT_MS=1500,BACK_VERIFY_MS=700,RELAUNCH_VERIFY_MS=900,RECOVERY_LIMIT_MS=6000;
 static final int MAX_ATTEMPTS=3,SESSION_ATTEMPTS=6;
 private final long windowMs;
 private final boolean fallback;
 private final AdPageClassifier ads;
 private String source,target,lastPackage,screenPackage,screenActivity;
 private long started,lastNow=-1,lastAction=-1,stableSince=-1,screenSince,detected;
 private boolean recovering,exhausted,reopening,lastWasRelaunch,inApp;
 private int attempts,totalAttempts;
 public GuardEngine(long windowMs,boolean fallback){this(windowMs,fallback,new AdPageClassifier());}
 public GuardEngine(long windowMs,boolean fallback,AdPageClassifier ads){if(windowMs<=0)throw new IllegalArgumentException("windowMs must be positive");if(ads==null)throw new IllegalArgumentException("ads required");this.windowMs=windowMs;this.fallback=fallback;this.ads=ads;}
 public String source(){return source;}
 public String target(){return target;}
 public int attempts(){return attempts;}
 public int totalAttempts(){return totalAttempts;}
 public boolean recovering(){return recovering;}
 /** True while the page being closed is an ad landing page inside the source app. */
 public boolean inApp(){return inApp;}
 public void reset(){source=target=lastPackage=screenPackage=screenActivity=null;recovering=exhausted=reopening=lastWasRelaunch=inApp=false;stableSince=lastNow=lastAction=-1;attempts=totalAttempts=0;}
 private boolean clockValid(long now){if(lastNow>=0&&now<lastNow){reset();return false;}lastNow=now;return true;}
 public Action update(String pkg,boolean excluded,boolean protectedSource,boolean allowed,long now){return update(pkg,null,excluded,protectedSource,allowed,now);}
 public Action update(String pkg,String activity,boolean excluded,boolean protectedSource,boolean allowed,long now){
  if(!clockValid(now))return Action.CANCELLED;
  if(pkg==null||excluded){boolean active=recovering;reset();lastPackage=pkg;return active?Action.CANCELLED:Action.NONE;}
  if(recovering){if(allowed){reset();lastPackage=pkg;return Action.CANCELLED;}return tick(pkg,activity,now);}
  boolean changed=!pkg.equals(lastPackage);
  if(source!=null){
   if(pkg.equals(source)){lastPackage=pkg;return ads.landing(activity)&&!exhausted?begin(pkg,activity,true,now):Action.NONE;}
   if(source.equals(lastPackage)&&now-started<=windowMs&&!allowed&&!exhausted)return begin(pkg,activity,false,now);
  }
  if(!changed)return Action.NONE;
  reset();lastNow=now;lastPackage=pkg;
  if(protectedSource){source=pkg;started=now;if(ads.landing(activity))return begin(pkg,activity,true,now);}
  return Action.NONE;
 }
 /** Starts a recovery without acting: the first action waits until the page stops changing. */
 private Action begin(String pkg,String activity,boolean sameApp,long now){
  target=pkg;lastPackage=pkg;inApp=sameApp;attempts=0;reopening=lastWasRelaunch=false;stableSince=lastAction=-1;
  detected=now;screenPackage=pkg;screenActivity=activity;screenSince=now;
  if(totalAttempts>=SESSION_ATTEMPTS){exhausted=true;return Action.FAILED;}
  recovering=true;return Action.NONE;
 }
 public Action tick(String pkg,long now){return tick(pkg,null,now);}
 public Action tick(String pkg,String activity,long now){
  if(!clockValid(now))return Action.CANCELLED;
  if(!recovering)return Action.NONE;
  observe(pkg,activity,now);
  boolean inSource=source.equals(pkg),onAd=inSource&&ads.landing(activity);
  if(inSource&&!onAd&&(activity!=null||!inApp)){
   if(stableSince<0)stableSince=now;if(now-stableSince<SOURCE_STABLE_MS)return Action.NONE;
   recovering=inApp=false;lastPackage=source;return reopening?Action.REOPENED:Action.RETURNED;
  }
  stableSince=-1;
  if(pkg==null||now-detected>RECOVERY_LIMIT_MS){reset();lastPackage=pkg;return Action.CANCELLED;}
  if(onAd){inApp=true;target=source;}
  else if(inSource)return Action.NONE; // Same app, page not reported yet: never act on an unknown page.
  else if(inApp){inApp=false;target=pkg;} // The landing page opened another app.
  else if(!pkg.equals(target)){reset();lastPackage=pkg;return Action.CANCELLED;}
  long since=lastAction<0?detected:lastAction;
  if(now-since<(lastAction<0?0:lastWasRelaunch?RELAUNCH_VERIFY_MS:BACK_VERIFY_MS))return Action.NONE;
  if(now-screenSince<SCREEN_STABLE_MS&&now-since<MAX_WAIT_MS)return Action.NONE;
  return next(now);
 }
 /** Unknown Activity in the same package carries no new information and keeps the page stable. */
 private void observe(String pkg,String activity,long now){
  boolean same=pkg==null?screenPackage==null:pkg.equals(screenPackage);
  if(same&&(activity==null||activity.equals(screenActivity)))return;
  screenPackage=pkg;screenActivity=activity;screenSince=now;
 }
 /** Cross-app order with fallback: Back, relaunch the source task, Back. Without fallback: Back three times. */
 private Action next(long now){
  if(attempts>=MAX_ATTEMPTS||totalAttempts>=SESSION_ATTEMPTS){recovering=inApp=false;exhausted=true;return Action.FAILED;}
  attempts++;totalAttempts++;lastAction=now;
  lastWasRelaunch=fallback&&!inApp&&attempts==2;
  if(lastWasRelaunch){reopening=true;return Action.RELAUNCH;}
  return Action.BACK;
 }
}
