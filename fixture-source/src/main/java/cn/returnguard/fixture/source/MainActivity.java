package cn.returnguard.fixture.source;
import android.app.*;
import android.os.*;
import android.content.*;
import android.hardware.*;
import android.net.Uri;
import android.widget.*;
public final class MainActivity extends Activity implements SensorEventListener {
 private final Handler handler=new Handler(Looper.getMainLooper());private SensorManager sensors;private boolean armed;private TextView state;private int resumes;
 private final String instance=Long.toHexString(System.nanoTime());
 @Override public void onCreate(Bundle saved){super.onCreate(saved);sensors=(SensorManager)getSystemService(SENSOR_SERVICE);ScrollView scroll=new ScrollView(this);scroll.setId(R.id.test_scroll);LinearLayout col=new LinearLayout(this);col.setOrientation(LinearLayout.VERTICAL);col.setPadding(30,24,30,24);scroll.addView(col);scroll.setOnApplyWindowInsetsListener((v,i)->{v.setPadding(0,i.getSystemWindowInsetTop(),0,i.getSystemWindowInsetBottom());return i;});setContentView(scroll);
  add(col,"跳转测试源 · 无真实广告\n先在“反摇一摇广告”中勾选本应用。每轮测试请先回桌面再打开，以重新开始保护时间窗。",20);state=add(col,"",14);EditText input=new EditText(this);input.setId(R.id.test_input);input.setSingleLine(true);input.setHint("输入文字，返回后检查是否保留");col.addView(input);
  button(col,"模拟误点：打开广告页",()->launch(false,false));
  button(col,"模拟无点击跳转：1 秒后打开",()->{hideKeyboard();handler.postDelayed(()->launch(false,false),1000);});
  button(col,"模拟新任务跳转（NEW_TASK）",()->launch(true,false));
  button(col,"模拟广告页吞掉返回键",()->launch(false,true));
  button(col,"启用一次真实摇动触发",()->{hideKeyboard();armed=true;state.setText("轻摇手机一次\n页面实例："+instance);});
  button(col,"跳转到外部浏览器",()->{hideKeyboard();try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://example.com")));}catch(ActivityNotFoundException e){Toast.makeText(this,"未安装浏览器",Toast.LENGTH_SHORT).show();}});
  button(col,"同应用内部页面（预期不自动返回）",()->startActivity(new Intent(this,InternalActivity.class)));
  add(col,"向下滚动，再测试是否回到同一位置。",18);for(int i=1;i<=12;i++)add(col,"记忆位置 · 第 "+i+" 段\n这段本地内容用于检查滚动位置是否保留。",17);button(col,"从这里跳转，检查滚动位置",()->launch(false,false));
 }
 private TextView add(LinearLayout p,String s,int size){TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setPadding(0,12,0,18);p.addView(v);return v;}
 private void button(LinearLayout p,String s,Runnable r){Button b=new Button(this);b.setText(s);p.addView(b);b.setOnClickListener(v->r.run());}
 private void hideKeyboard(){((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(getWindow().getDecorView().getWindowToken(),0);}
 private void launch(boolean newTask,boolean consume){armed=false;hideKeyboard();Intent i=new Intent().setComponent(new ComponentName("cn.returnguard.fixture.target","cn.returnguard.fixture.target.MainActivity")).putExtra("consume",consume);if(newTask)i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);try{startActivity(i);}catch(ActivityNotFoundException e){Toast.makeText(this,"请先安装“模拟广告页”APK",Toast.LENGTH_LONG).show();}}
 @Override public void onResume(){super.onResume();resumes++;state.setText("页面实例："+instance+"\n前台恢复次数："+resumes);Sensor s=sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);if(s!=null)sensors.registerListener(this,s,SensorManager.SENSOR_DELAY_NORMAL);}
 @Override public void onPause(){armed=false;handler.removeCallbacksAndMessages(null);sensors.unregisterListener(this);super.onPause();}
 @Override public void onSensorChanged(SensorEvent e){double a=Math.sqrt(e.values[0]*e.values[0]+e.values[1]*e.values[1]+e.values[2]*e.values[2]);if(armed&&a>16)launch(false,false);}
 @Override public void onAccuracyChanged(Sensor s,int a){}
}
