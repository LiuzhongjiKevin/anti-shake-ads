package cn.returnguard.fixture.source;
import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;
public final class InternalActivity extends Activity {@Override public void onCreate(Bundle b){super.onCreate(b);TextView t=new TextView(this);t.setPadding(32,100,32,32);t.setTextSize(24);t.setText("这是同应用内部页面。\n\n预期：不自动返回。\n请手动按返回。\n\n通用跨应用保护不能识别所有内部广告。");setContentView(t);}}
