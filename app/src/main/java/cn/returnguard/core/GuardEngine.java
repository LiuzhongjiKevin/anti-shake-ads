package cn.returnguard.core;

/** Pure state machine; caller verifies foreground immediately before every action. */
public final class GuardEngine {
 public enum Action { NONE, BACK, RELAUNCH, RETURNED, REOPENED, FAILED, CANCELLED }
 private final long windowMs;
 private final boolean fallback;
 private String source,target,lastPackage;
 private long started,lastNow=-1,lastAction,stableSince=-1;
 private boolean recovering,exhausted,fallbackUsed,reopening;
 private int attempts,totalAttempts;
 public GuardEngine(long windowMs,boolean fallback) { if(windowMs<=0)throw new IllegalArgumentException("windowMs must be positive");this.windowMs=windowMs;this.fallback=fallback; }
 public String source(){return source;}
 public String target(){return target;}
 public int attempts(){return attempts;}
 public int totalAttempts(){return totalAttempts;}
 public boolean recovering(){return recovering;}
 public void reset(){source=target=lastPackage=null;recovering=exhausted=fallbackUsed=reopening=false;stableSince=lastNow=-1;attempts=totalAttempts=0;}
 private boolean clockValid(long now){if(lastNow>=0&&now<lastNow){reset();return false;}lastNow=now;return true;}
 public Action update(String pkg,boolean excluded,boolean protectedSource,boolean allowed,long now){
  if(!clockValid(now))return Action.CANCELLED;
  if(pkg==null||excluded){boolean active=recovering;reset();lastPackage=pkg;return active?Action.CANCELLED:Action.NONE;}
  if(recovering){if(allowed){reset();lastPackage=pkg;return Action.CANCELLED;}return tick(pkg,now);}
  boolean changed=!pkg.equals(lastPackage);
  if(source!=null){
   if(pkg.equals(source)){lastPackage=pkg;return Action.NONE;}
   if(source.equals(lastPackage)&&now-started<=windowMs&&!allowed&&!exhausted){
    target=pkg;lastPackage=pkg;attempts=0;reopening=false;stableSince=-1;
    if(totalAttempts>=6){exhausted=true;return Action.FAILED;}
    recovering=true;return back(now);
   }
  }
  if(!changed)return Action.NONE;
  reset();lastNow=now;lastPackage=pkg;if(protectedSource){source=pkg;started=now;}return Action.NONE;
 }
 private Action back(long now){attempts++;totalAttempts++;lastAction=now;return Action.BACK;}
 public Action tick(String pkg,long now){
  if(!clockValid(now))return Action.CANCELLED;
  if(!recovering)return Action.NONE;
  if(source.equals(pkg)){
   if(stableSince<0)stableSince=now;if(now-stableSince<120)return Action.NONE;
   recovering=false;lastPackage=source;return reopening?Action.REOPENED:Action.RETURNED;
  }
  stableSince=-1;
  if(pkg==null||!target.equals(pkg)||now-started>windowMs){reset();lastPackage=pkg;return Action.CANCELLED;}
  if(now-lastAction<(reopening?750:450))return Action.NONE;
  if(!reopening&&attempts<3&&totalAttempts<6)return back(now);
  if(fallback&&!fallbackUsed&&!reopening){fallbackUsed=reopening=true;lastAction=now;return Action.RELAUNCH;}
  recovering=false;exhausted=true;return Action.FAILED;
 }
}
