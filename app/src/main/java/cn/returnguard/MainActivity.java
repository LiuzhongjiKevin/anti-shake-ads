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
public final class MainActivity extends Activity {
 private static final int BG=0xff101b28,CARD=0xff1b2a3b,TEXT=0xfff0f6fa,MUTED=0xffaebccd,ACCENT=0xff7ee2c4;
 private Prefs prefs;private LinearLayout body;private TextView status,count;
 private final Handler handler=new Handler(Looper.getMainLooper());
 private final Runnable ticker=new Runnable(){public void run(){update();handler.postDelayed(this,1000);}};
 @Override public void onCreate(Bundle b){super.onCreate(b);prefs=new Prefs(this);}
 @Override public void onResume(){super.onResume();render();handler.post(ticker);}
 @Override public void onPause(){handler.removeCallbacks(ticker);super.onPause();}
 private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
 private TextView text(String s,int size,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setLineSpacing(dp(3),1);t.setPadding(0,dp(5),0,dp(5));return t;}
 private LinearLayout card(String title){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);v.setPadding(dp(20),dp(16),dp(20),dp(16));GradientDrawable bg=new GradientDrawable();bg.setColor(CARD);bg.setCornerRadius(dp(20));v.setBackground(bg);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(14);body.addView(v,lp);TextView h=text(title,18,TEXT);h.setTypeface(null,Typeface.BOLD);v.addView(h);return v;}
 private void button(LinearLayout p,String label,Runnable action,boolean primary){Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setTextSize(15);b.setMinHeight(dp(52));b.setTextColor(primary?BG:TEXT);b.setBackgroundTintList(ColorStateList.valueOf(primary?ACCENT:0xff304256));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(8);p.addView(b,lp);b.setOnClickListener(v->action.run());}
 private void render(){
  ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(BG);scroll.setFillViewport(true);body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(20),dp(20),dp(20),dp(24));scroll.addView(body);
  scroll.setOnApplyWindowInsetsListener((v,i)->{v.setPadding(i.getSystemWindowInsetLeft(),i.getSystemWindowInsetTop(),i.getSystemWindowInsetRight(),i.getSystemWindowInsetBottom());return i;});setContentView(scroll);scroll.requestApplyInsets();
  body.addView(text("ANTI-SHAKE ADS  /  0.1 TEST",12,ACCENT));TextView title=text("回到刚才",34,TEXT);title.setTypeface(null,Typeface.BOLD);body.addView(title);body.addView(text("摇一摇或误触跳走后，尝试回到原应用。",15,MUTED));
  LinearLayout s=card("保护状态");status=text("",21,ACCENT);s.addView(status);
  Switch on=new Switch(this);on.setText("开启跳转保护");on.setTextColor(TEXT);on.setTextSize(17);on.setPadding(0,dp(12),0,dp(12));on.setChecked(prefs.enabled());s.addView(on);on.setOnCheckedChangeListener((v,c)->{prefs.data.edit().putBoolean("enabled",c).apply();update();});
  button(s,"1 · 开启无障碍服务",this::disclose,true);button(s,"2 · 选择保护应用",()->pickApps(false,null),false);count=text("",13,MUTED);s.addView(count);
  button(s,"暂停 60 秒 / 提前恢复",()->{if(prefs.paused())prefs.resume();else prefs.pause();update();},false);
  s.addView(text("保护期间正常登录、支付、分享也可能被返回。可在下拉快捷设置中添加“跳转保护”，随时暂停。",13,MUTED));
  LinearLayout policy=card("按你的习惯保护");policy.addView(text("进入所选应用后的保护时长",14,MUTED));RadioGroup group=new RadioGroup(this);group.setOrientation(LinearLayout.HORIZONTAL);
  for(int sec:new int[]{5,10,20,30}){RadioButton r=new RadioButton(this);r.setId(sec);r.setText(sec+"秒");r.setTextColor(TEXT);group.addView(r,new RadioGroup.LayoutParams(0,dp(48),1));}group.check((int)(prefs.window()/1000));policy.addView(group);group.setOnCheckedChangeListener((v,id)->prefs.data.edit().putInt("seconds",id).apply());
  button(policy,"管理允许跳转的目标",this::pickSource,false);Switch fallback=new Switch(this);fallback.setText("返回失败时，尝试重新打开来源");fallback.setTextColor(TEXT);fallback.setPadding(0,dp(12),0,dp(12));fallback.setChecked(prefs.fallback());policy.addView(fallback);fallback.setOnCheckedChangeListener((v,c)->prefs.data.edit().putBoolean("fallback",c).apply());
  policy.addView(text("重新打开可能回到首页，因此默认关闭。日志会区分返回与重新打开；原页面是否保留需你确认。",13,MUTED));
  LinearLayout test=card("先做一次可重复的测试");test.addView(text("安装随附的“跳转测试源”和“模拟广告页”，勾选保护“跳转测试源”。从桌面打开它，输入文字，再点测试按钮。",14,MUTED));
  button(test,"打开跳转测试源",()->{Intent i=getPackageManager().getLaunchIntentForPackage("cn.returnguard.fixture.source");if(i==null)message("请先安装随附的 fixture-source APK。");else startActivity(i);},false);
  button(test,"查看最近返回记录",this::logs,false);button(test,"后台运行设置",()->settings(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)),false);
  LinearLayout about=card("本地运行，随时关闭");about.addView(text("不需要 root，不联网，不保存截图、网页正文或输入内容。读取活动窗口的包名和类型以确认当前应用。日志最多 100 条，可随时清空。",14,MUTED));about.addView(text("测试版优先处理跨应用跳转。应用内部广告页不自动返回；分屏、画中画及特殊厂商窗口需额外验证。广告页可能短暂出现。",13,MUTED));
  button(about,"开源与许可",this::licenses,false);body.addView(text("保护你的操作，也保留你的选择。",13,MUTED));update();
 }
 private void licenses(){
  try(java.io.InputStream input=getResources().openRawResource(R.raw.third_party_notices);java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream()){
   byte[] buffer=new byte[4096];int n;while((n=input.read(buffer))!=-1)out.write(buffer,0,n);
   message(new String(out.toByteArray(),java.nio.charset.StandardCharsets.UTF_8));
  }catch(java.io.IOException e){message("无法读取许可证，请查看随附源码的 THIRD_PARTY_NOTICES.md。");}
 }
 private void update(){if(status==null)return;status.setText(!GuardService.running()?"○ 等待无障碍服务":!prefs.enabled()?"○ 保护已关闭":prefs.paused()?"Ⅱ 已暂停，60 秒内恢复":prefs.sources().isEmpty()?"○ 请选择保护应用":"● 保护已就绪");if(count!=null)count.setText("已选 "+prefs.sources().size()+" 个应用 · 保护 "+prefs.window()/1000+" 秒\n设置完成后，请从桌面重新进入需要测试的应用。");}
 private void disclose(){new AlertDialog.Builder(this).setTitle("无障碍服务用于什么？").setMessage(getString(R.string.accessibility_description)+"\n\n你可以随时在系统设置关闭服务。").setNegativeButton("暂不开启",null).setPositiveButton("了解，去设置",(d,w)->settings(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))).show();}
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
    new AlertDialog.Builder(this).setTitle(allow?"允许目标 · "+AppCatalog.label(this,source):"选择保护应用").setView(layout).setNegativeButton("取消",null).setPositiveButton("保存",(d,w)->{if(allow)prefs.saveAllowed(source,selected);else prefs.saveSources(selected);update();}).show();
   });
  },"app-catalog").start();
 }
 private void logs(){EventLog log=new EventLog(this);JSONArray rows=log.read();StringBuilder b=new StringBuilder();SimpleDateFormat f=new SimpleDateFormat("MM-dd HH:mm:ss",Locale.getDefault());for(int i=0;i<rows.length();i++){JSONObject r=rows.optJSONObject(i);if(r!=null)b.append(f.format(new Date(r.optLong("at")))).append("\n").append(AppCatalog.label(this,r.optString("source"))).append(" → ").append(AppCatalog.label(this,r.optString("target"))).append("\n").append(r.optString("result")).append("\n返回尝试 ").append(r.optInt("attempts")).append(" 次 · ").append(r.optLong("elapsed")).append(" ms\n\n");}new AlertDialog.Builder(this).setTitle("最近记录（最多 100 条）").setMessage(b.length()==0?"还没有返回记录。不会逐条记录普通浏览过程。":b.toString()).setPositiveButton("关闭",null).setNeutralButton("清空",(d,w)->new AlertDialog.Builder(this).setMessage("清空返回记录？保护设置会保留。").setNegativeButton("取消",null).setPositiveButton("清空",(a,n)->log.clear()).show()).show();}
}
