package cn.returnguard.core;
/** Bounded no-action grace while Android temporarily omits application windows. */
public final class WindowReadiness {
 private long started=-1;
 public boolean shouldDefer(boolean transitioning,long now){
  if(!transitioning){clear();return false;}
  if(started<0)started=now;
  return now>=started&&now-started<1500;
 }
 public void clear(){started=-1;}
}
