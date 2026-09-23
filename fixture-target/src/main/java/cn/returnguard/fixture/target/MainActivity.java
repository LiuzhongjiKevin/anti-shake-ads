package cn.returnguard.fixture.target;
import android.app.*;
import android.os.Bundle;
import android.content.Intent;
import android.widget.*;
public final class MainActivity extends Activity {
 private boolean consume;
 @Override public void onCreate(Bundle b){super.onCreate(b);consume=getIntent().getBooleanExtra("consume",false);LinearLayout p=new LinearLayout(this);p.setOrientation(LinearLayout.VERTICAL);p.setPadding(32,60,32,32);p.setBackgroundColor(0xffffeddb);p.setOnApplyWindowInsetsListener((v,i)->{v.setPadding(32,60+i.getSystemWindowInsetTop(),32,32+i.getSystemWindowInsetBottom());return i;});setContentView(p);
  TextView t=new TextView(this);t.setTextSize(26);t.setText("模拟广告目标\n\n无真实广告、无联网。\n\n吞掉返回键："+consume+"\n层数："+getIntent().getIntExtra("depth",1));p.addView(t);Button back=new Button(this);back.setText("手动关闭本页");p.addView(back);back.setOnClickListener(v->finish());Button more=new Button(this);more.setText("再打开一层目标页面");p.addView(more);more.setOnClickListener(v->startActivity(new Intent(this,MainActivity.class).putExtra("depth",getIntent().getIntExtra("depth",1)+1)));
 }
 @Override public void onBackPressed(){if(consume)Toast.makeText(this,"测试：此页忽略系统返回",Toast.LENGTH_SHORT).show();else super.onBackPressed();}
}
