package io.github.guocheng1378.miclawbridge;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

/** 极简诊断版: 只显示文字, 排除 UI 代码问题 */
public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TextView tv = new TextView(this);
        tv.setText("MiclawApiBridge v1.7.6\n\n这是极简诊断版。\n如果能显示这行字 → UI 代码没问题");
        tv.setTextSize(18);
        tv.setPadding(50, 100, 50, 50);
        setContentView(tv);
    }
}
