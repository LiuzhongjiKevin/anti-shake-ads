package cn.returnguard.core;
/** Facts shared by the home screen and notification; enabled alone is not ready. */
public enum ProtectionState {
 OFF, NEEDS_ACCESSIBILITY, NEEDS_APPS, PAUSED, READY;
 public static ProtectionState resolve(boolean enabled, boolean connected, boolean hasApps, boolean paused) {
  if(!enabled)return OFF;
  if(!connected)return NEEDS_ACCESSIBILITY;
  if(!hasApps)return NEEDS_APPS;
  return paused?PAUSED:READY;
 }
}
