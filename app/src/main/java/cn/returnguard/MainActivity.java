package cn.returnguard;
import android.app.*;
import android.content.*;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.text.SimpleDateFormat;
import java.util.*;
import cn.returnguard.core.ProtectionState;

public final class MainActivity extends Activity {
 private static final int BG=0xfff5f6f2,CARD=0xffffffff,TEXT=0xff17332c,MUTED=0xff586b62,ACCENT=0xff174b40;
 private Prefs prefs;private LinearLayout body;private TextView status,count,detail,backgroundHint;private Button power;
 private boolean setupPending(){return prefs.data.getBoolean("setup_requested",false);}
 private final Handler handler=new Handler(Looper.getMainLooper());
 private final Runnable ticker=new Runnable(){public void run(){if(setupPending()&&GuardService.running()&&!prefs.sources().isEmpty())activate();update();handler.postDelayed(this,1000);}};
 @Override public void onCreate(Bundle b){super.onCreate(b);prefs=new Prefs(this);}
 @Override public void onResume(){super.onResume();render();if(prefs.enabled()&&GuardService.running())ProtectionNotificationService.start(this);handler.post(ticker);}
 @Override public void onPause(){handler.removeCallbacks(ticker);super.onPause();}
 private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
 private GradientDrawable shape(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
 private TextView text(String s,int size,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setLineSpacing(dp(3),1);t.setPadding(0,dp(5),0,dp(5));return t;}
 private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setPadding(dp(22),dp(12),dp(22),dp(16));return l;}
 private void button(LinearLayout p,String label,Runnable action,boolean primary){Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setTextSize(15);b.setMinHeight(dp(52));b.setTextColor(primary?CARD:TEXT);b.setBackgroundTintList(ColorStateList.valueOf(primary?ACCENT:0xffe3ede5));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(6);p.addView(b,lp);b.setOnClickListener(v->action.run());}
 private void render(){
  ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(BG);scroll.setFillViewport(true);body=column();body.setPadding(dp(26),dp(18),dp(26),dp(24));scroll.addView(body);
  scroll.setOnApplyWindowInsetsListener((v,i)->{v.setPadding(i.getSystemWindowInsetLeft(),i.getSystemWindowInsetTop(),i.getSystemWindowInsetRight(),i.getSystemWindowInsetBottom());return i;});setContentView(scroll);scroll.requestApplyInsets();
  LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);body.addView(header);
  TextView name=text("反摇一摇广告",19,TEXT);name.setTypeface(null,Typeface.BOLD);header.addView(name,new LinearLayout.LayoutParams(0,-2,1));
  Button settings=new Button(this);settings.setText("设置");settings.setTextColor(ACCENT);settings.setBackgroundTintList(ColorStateList.valueOf(BG));settings.setMinWidth(0);header.addView(settings,new LinearLayout.LayoutParams(dp(72),dp(52)));settings.setOnClickListener(v->options());
  TextView heading=text("安心留在\n刚才的页面",34,TEXT);heading.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));LinearLayout.LayoutParams titleLp=new LinearLayout.LayoutParams(-1,-2);titleLp.topMargin=dp(22);body.addView(heading,titleLp);
  TextView intro=text("减少开屏广告误触带来的跨应用跳转",14,MUTED);body.addView(intro);
  power=new Button(this);power.setId(R.id.protection_button);power.setAllCaps(false);power.setTextSize(25);power.setTypeface(null,Typeface.BOLD);power.setTextColor(CARD);power.setGravity(Gravity.CENTER);power.setPadding(dp(18),dp(18),dp(18),dp(18));power.setBackground(shape(ACCENT,160));power.setElevation(dp(2));
  int diameter=Math.max(200,Math.min(252,(int)(getResources().getDisplayMetrics().widthPixels/getResources().getDisplayMetrics().density)-64));
  LinearLayout.LayoutParams pl=new LinearLayout.LayoutParams(dp(diameter),dp(diameter));pl.gravity=Gravity.CENTER_HORIZONTAL;pl.topMargin=dp(26);pl.bottomMargin=dp(20);body.addView(power,pl);power.setOnClickListener(v->primaryAction());
  status=text("",21,TEXT);status.setId(R.id.protection_status);status.setTypeface(null,Typeface.BOLD);status.setGravity(Gravity.CENTER);body.addView(status);
  detail=text("",14,MUTED);detail.setGravity(Gravity.CENTER);body.addView(detail);
  count=text("",15,ACCENT);count.setGravity(Gravity.CENTER);count.setMinHeight(dp(54));count.setBackground(shape(0xffe3ede5,16));LinearLayout.LayoutParams cl=new LinearLayout.LayoutParams(-1,-2);cl.topMargin=dp(22);body.addView(count,cl);count.setOnClickListener(v->pickApps(false,null));count.setContentDescription("选择要保护的应用");count.setFocusable(true);
  backgroundHint=text("",13,MUTED);backgroundHint.setGravity(Gravity.CENTER);backgroundHint.setMinHeight(dp(52));body.addView(backgroundHint);backgroundHint.setOnClickListener(v->background());backgroundHint.setFocusable(true);
  update();
 }
 private void update(){if(status==null)return;
  ProtectionState s=ProtectionStatus.state(prefs);status.setText(ProtectionStatus.title(prefs));detail.setText(ProtectionStatus.detail(prefs));
  String action=s==ProtectionState.READY?"关闭保护":s==ProtectionState.PAUSED?"恢复保护":setupPending()||prefs.enabled()?"继续设置":"开启保护";
  power.setText("↶\n"+action);power.setContentDescription(action);count.setText(prefs.sources().isEmpty()?"选择要保护的应用":"已保护 "+prefs.sources().size()+" 个应用  ·  点此管理");
  if(!prefs.enabled())backgroundHint.setText(BackgroundSettings.xiaomi()?"澎湃 OS 后台设置":"后台运行设置");
  else if(!ProtectionNotificationService.running())backgroundHint.setText("常驻状态未启动 · 点此检查");
  else if(!BackgroundSettings.notifications(this))backgroundHint.setText("通知未允许显示 · 点此开启");
  else backgroundHint.setText("常驻状态已启动 · 查看后台设置");
 }
 private void primaryAction(){
  ProtectionState s=ProtectionStatus.state(prefs);
  if(s==ProtectionState.READY){disableProtection();return;}
  if(s==ProtectionState.PAUSED){prefs.resume();ProtectionNotificationService.start(this);update();return;}
  prefs.data.edit().putBoolean("setup_requested",true).apply();continueSetup();
 }
 private void disableProtection(){prefs.stop();ProtectionNotificationService.stop(this);update();}
 private void cancelSetup(){prefs.data.edit().putBoolean("setup_requested",false).apply();update();}
 private void continueSetup(){if(prefs.sources().isEmpty()){pickApps(false,null);return;}if(!GuardService.running()){disclose();return;}activate();}
 private void activate(){cancelSetup();prefs.resume();prefs.data.edit().putBoolean("enabled",true).apply();boolean started=ProtectionNotificationService.start(this);update();if(!started)message("系统暂未允许启动常驻状态。请检查后台设置后，重新打开本应用。");else if(!prefs.data.getBoolean("background_guide_seen",false)){prefs.data.edit().putBoolean("background_guide_seen",true).apply();background();}}
 private void disclose(){new AlertDialog.Builder(this).setTitle("开启无障碍，才能帮你返回").setMessage(getString(R.string.accessibility_description)+"\n\n接下来请在系统的已下载应用/已安装服务中，打开“反摇一摇广告 · 跳转保护”。完成后返回本应用，将自动继续。\n\n如果开关不可用：在应用信息右上角菜单中检查“允许受限设置”。设备可能要求额外验证；必须由你本人确认。").setNegativeButton("稍后",(d,w)->cancelSetup()).setOnCancelListener(d->cancelSetup()).setPositiveButton("去开启",(d,w)->BackgroundSettings.accessibility(this)).show();}
 private void background(){
  LinearLayout l=column();ScrollView scroll=new ScrollView(this);scroll.addView(l);
  l.addView(text("完成一次设置，之后照常用手机",20,TEXT));
  l.addView(text("无障碍："+(GuardService.running()?"已连接":"未连接")+"\n常驻状态："+(ProtectionNotificationService.running()?"运行中":"未启动")+"\n通知："+(BackgroundSettings.notifications(this)?"已允许显示":"未允许显示")+"\n安卓电池优化："+(BackgroundSettings.batteryExempt(this)?"已豁免":"未豁免"),14,MUTED));
  if(!GuardService.running())button(l,"检查无障碍服务",this::disclose,true);
  if(prefs.enabled()&&!ProtectionNotificationService.running())button(l,"重新启动常驻状态",()->{boolean ok=ProtectionNotificationService.start(this);message(ok?"已请求启动，返回主页查看状态。":"系统未允许启动，请检查后台限制。");},true);
  button(l,"允许常驻通知",()->{if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED&&!prefs.data.getBoolean("notification_asked",false)){prefs.data.edit().putBoolean("notification_asked",true).apply();requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},22);}else BackgroundSettings.notification(this);},false);
  button(l,BackgroundSettings.xiaomi()?"省电策略设为“无限制”":"检查电池优化",()->BackgroundSettings.battery(this),false);
  if(BackgroundSettings.xiaomi()||BackgroundSettings.huawei()){
   button(l,"允许后台自启动",()->BackgroundSettings.autostart(this),false);
   l.addView(text(BackgroundSettings.xiaomi()?"澎湃 OS / MIUI\n在后台自启动列表中允许本应用；应用信息 → 省电策略 → 无限制。最近任务中长按本应用卡片，找到锁定入口。不同机型的名称和位置可能不同。":"华为 EMUI\n应用启动管理中关闭自动管理，允许自启动、关联启动和后台活动，再在最近任务中锁定。",14,MUTED));
   l.addView(text("自启动、省电策略和任务锁定需你手动确认，本应用无法可靠读取这些厂商设置。",13,MUTED));
  }
  l.addView(text("无需从这里打开高德等应用。系统仍可能在强行停止、重启或极端省电时中断保护；下次打开可检查状态。",13,MUTED));
  new AlertDialog.Builder(this).setTitle("后台运行设置").setView(scroll).setPositiveButton("完成，返回主页",(d,w)->update()).show();
 }
 private void options(){
  String[] items={"后台运行设置","保护规则与允许跳转","暂停一分钟 / 恢复","最近返回记录","测试与使用说明","开源与隐私","关闭保护"};
  new AlertDialog.Builder(this).setTitle("设置").setItems(items,(d,i)->{switch(i){case 6:disableProtection();break;case 0:background();break;case 1:policy();break;case 2:if(prefs.paused())prefs.resume();else if(prefs.enabled())prefs.pause();update();break;case 3:logs();break;case 4:message("1. 开启保护，选择高德地图等应用。\n2. 在系统设置中开启无障碍，返回完成后台设置。\n3. 从桌面重新打开高德，开屏广告跨应用跳转时尝试返回。\n\n保护可能影响正常登录、支付和分享，可先从通知暂停一分钟。应用内部的广告页暂不自动处理。\n\n测试版只验证了模拟跳转；真实高德广告和澎湃 OS 真机效果仍需验证。");break;default:new AlertDialog.Builder(this).setTitle("本地运行，随时关闭").setMessage("0.2.0 测试版\n不联网、不保存截图或输入内容。无障碍读取活动窗口的包名和类型并执行返回；记录最多 100 条。\n后台状态使用常驻服务，不是 VPN，不接管网络。系统授权需本人开启，无法承诺永不被系统关闭。").setPositiveButton("开源许可",(a,n)->licenses()).setNegativeButton("关闭",null).show();}}).setNegativeButton("关闭",null).show();
 }
 private void policy(){
  LinearLayout l=column();l.addView(text("进入所选应用后的保护时长",16,TEXT));RadioGroup group=new RadioGroup(this);group.setOrientation(LinearLayout.HORIZONTAL);
  for(int sec:new int[]{5,10,20,30}){RadioButton r=new RadioButton(this);r.setId(sec);r.setText(sec+"秒");r.setTextColor(TEXT);group.addView(r,new RadioGroup.LayoutParams(0,dp(52),1));}group.check((int)(prefs.window()/1000));l.addView(group);group.setOnCheckedChangeListener((v,id)->{prefs.data.edit().putInt("seconds",id).apply();update();});
  button(l,"管理允许跳转的目标",this::pickSource,false);Switch fallback=new Switch(this);fallback.setText("返回失败时重新打开来源");fallback.setTextColor(TEXT);fallback.setMinHeight(dp(56));fallback.setChecked(prefs.fallback());l.addView(fallback);fallback.setOnCheckedChangeListener((v,c)->prefs.data.edit().putBoolean("fallback",c).apply());
  l.addView(text("重新打开可能回到首页，默认关闭。正常支付、登录、分享前可暂停保护。",13,MUTED));new AlertDialog.Builder(this).setTitle("保护规则").setView(l).setPositiveButton("完成",null).show();
 }
 private void licenses(){
  try(java.io.InputStream input=getResources().openRawResource(R.raw.third_party_notices);java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream()){
   byte[] buffer=new byte[4096];int n;while((n=input.read(buffer))!=-1)out.write(buffer,0,n);
   message(new String(out.toByteArray(),java.nio.charset.StandardCharsets.UTF_8));
  }catch(java.io.IOException e){message("无法读取许可证，请查看随附源码的 THIRD_PARTY_NOTICES.md。");}
 }
 private void settings(Intent i){try{startActivity(i);}catch(RuntimeException e){message("无法打开该设置，请手动进入系统设置。");}}
 private void message(String s){new AlertDialog.Builder(this).setTitle("反摇一摇广告").setMessage(s).setPositiveButton("知道了",null).show();}
 private void pickSource(){List<String> sources=new ArrayList<>(prefs.sources());Collections.sort(sources);if(sources.isEmpty()){message("请先选择保护应用。");return;}String[] names=new String[sources.size()];for(int i=0;i<names.length;i++)names[i]=AppCatalog.label(this,sources.get(i));new AlertDialog.Builder(this).setTitle("先选来源应用").setItems(names,(d,i)->pickApps(true,sources.get(i))).setNegativeButton("取消",null).show();}
 private void pickApps(boolean allow,String source){
  Toast.makeText(this,"正在读取可启动应用…",Toast.LENGTH_SHORT).show();new Thread(()->{
   List<AppCatalog.Entry> apps=AppCatalog.apps(getApplicationContext());runOnUiThread(()->{
    if(isFinishing()||isDestroyed())return;Set<String> selected=allow?prefs.allowed(source):prefs.sources();
    LinearLayout layout=new LinearLayout(this);layout.setOrientation(LinearLayout.VERTICAL);layout.setPadding(dp(16),dp(8),dp(16),0);EditText search=new EditText(this);search.setSingleLine(true);search.setHint("搜索应用名称或包名");layout.addView(search);ListView list=new ListView(this);layout.addView(list,new LinearLayout.LayoutParams(-1,dp(360)));List<AppCatalog.Entry> filtered=new ArrayList<>();
    Runnable filter=()->{String q=search.getText().toString().toLowerCase(Locale.ROOT);filtered.clear();List<String> labels=new ArrayList<>();for(AppCatalog.Entry e:apps)if(!e.pkg.equals(source)&&(e.label+e.pkg).toLowerCase(Locale.ROOT).contains(q)){filtered.add(e);labels.add((selected.contains(e.pkg)?"✓  ":"○  ")+e.display());}list.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_list_item_1,labels));};
    list.setOnItemClickListener((p,v,pos,id)->{String pkg=filtered.get(pos).pkg;if(!selected.add(pkg))selected.remove(pkg);filter.run();});search.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int b,int c){filter.run();}public void afterTextChanged(android.text.Editable e){}});filter.run();
    new AlertDialog.Builder(this).setTitle(allow?"允许目标 · "+AppCatalog.label(this,source):"选择保护应用").setView(layout).setNegativeButton("取消",(d,w)->cancelSetup()).setOnCancelListener(d->cancelSetup()).setPositiveButton("保存",(d,w)->{if(allow)prefs.saveAllowed(source,selected);else prefs.saveSources(selected);update();if(!allow&&setupPending())continueSetup();}).show();
   });
  },"app-catalog").start();
 }
 private void logs(){EventLog log=new EventLog(this);JSONArray rows=log.read();StringBuilder b=new StringBuilder();SimpleDateFormat f=new SimpleDateFormat("MM-dd HH:mm:ss",Locale.getDefault());for(int i=0;i<rows.length();i++){JSONObject r=rows.optJSONObject(i);if(r!=null)b.append(f.format(new Date(r.optLong("at")))).append("\n").append(AppCatalog.label(this,r.optString("source"))).append(" → ").append(AppCatalog.label(this,r.optString("target"))).append("\n").append(r.optString("result")).append("\n返回尝试 ").append(r.optInt("attempts")).append(" 次 · ").append(r.optLong("elapsed")).append(" ms\n\n");}new AlertDialog.Builder(this).setTitle("最近记录（最多 100 条）").setMessage(b.length()==0?"还没有返回记录。不会逐条记录普通浏览过程。":b.toString()).setPositiveButton("关闭",null).setNeutralButton("清空",(d,w)->new AlertDialog.Builder(this).setMessage("清空返回记录？保护设置会保留。").setNegativeButton("取消",null).setPositiveButton("清空",(a,n)->log.clear()).show()).show();}
}
