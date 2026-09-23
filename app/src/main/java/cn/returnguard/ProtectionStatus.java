package cn.returnguard;
import cn.returnguard.core.ProtectionState;
final class ProtectionStatus {
 static ProtectionState state(Prefs p){return ProtectionState.resolve(p.enabled(),GuardService.running(),!p.sources().isEmpty(),p.paused());}
 static String title(Prefs p){switch(state(p)){
  case READY:return "保护已开启";
  case PAUSED:return "保护已暂停";
  case NEEDS_ACCESSIBILITY:return "需要开启无障碍";
  case NEEDS_APPS:return "请选择保护应用";
  default:return "保护未开启";
 }}
 static String detail(Prefs p){switch(state(p)){
  case READY:return "进入所选应用后的 "+p.window()/1000+" 秒内，尝试返回跨应用广告跳转。";
  case PAUSED:return "一分钟后自动恢复，期间可正常登录、支付或分享。";
  case NEEDS_ACCESSIBILITY:return "服务尚未连接。点此检查无障碍设置。";
  case NEEDS_APPS:return "仅保护你选择的应用，可先选择高德地图。";
  default:return "开启后，从桌面照常使用其他应用。";
 }}
}
