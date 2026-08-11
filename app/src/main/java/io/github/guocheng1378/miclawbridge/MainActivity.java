package io.github.guocheng1378.miclawbridge;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 模块设置界面 (LAUNCHER Activity)
 * 配置: 端口 / Token / LLM 代理(DeepSeek) - API Key 安全存本机
 */
public class MainActivity extends Activity {

    private EditText etPort, etToken, etLlmUrl, etLlmKey, etLlmModel;
    private Switch swLlm;
    private TextView tvStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Config.load(this);
        buildUi();
    }

    private int dp(int v) {
        return (int) TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    private TextView label(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(14);
        t.setTextColor(Color.DKGRAY);
        t.setPadding(0, dp(12), 0, dp(4));
        return t;
    }

    private EditText input(String def, boolean number, boolean password) {
        EditText e = new EditText(this);
        e.setText(def);
        if (number) e.setInputType(InputType.TYPE_CLASS_NUMBER);
        if (password)
            e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        e.setTextSize(15);
        return e;
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(20), dp(20), dp(20));

        tvStatus = new TextView(this);
        tvStatus.setTextSize(13);
        tvStatus.setText("状态: HTTP 服务在超级小爱进程内自动启动\n" +
            "API: http://127.0.0.1:" + Config.HTTP_PORT + "/v1");
        root.addView(tvStatus);

        root.addView(label("HTTP 端口"));
        etPort = input(String.valueOf(Config.HTTP_PORT), true, false);
        root.addView(etPort);

        root.addView(label("API Token (留空 = 不鉴权; 建议设置)"));
        etToken = input(Config.API_TOKEN, false, false);
        root.addView(etToken);

        root.addView(label("Function Calling 代理 (LSPilot 工具调用)"));
        swLlm = new Switch(this);
        swLlm.setText("启用 LLM 代理 (DeepSeek)");
        swLlm.setChecked(Config.LLM_PROXY_ENABLED);
        root.addView(swLlm);

        root.addView(label("LLM Base URL"));
        etLlmUrl = input(Config.LLM_BASE_URL, false, false);
        root.addView(etLlmUrl);

        root.addView(label("LLM API Key (仅存本机, 不写入仓库)"));
        etLlmKey = input(Config.LLM_API_KEY, false, true);
        root.addView(etLlmKey);

        root.addView(label("LLM Model"));
        etLlmModel = input(Config.LLM_MODEL, false, false);
        root.addView(etLlmModel);

        Button btn = new Button(this);
        btn.setText("保存配置");
        btn.setOnClickListener(v -> save());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(20);
        root.addView(btn, lp);

        scroll.addView(root);
        setContentView(scroll);
        setTitle("MiclawApiBridge 设置");
    }

    private void save() {
        try {
            Config.HTTP_PORT = Integer.parseInt(etPort.getText().toString());
        } catch (Exception ignored) {}
        Config.API_TOKEN = etToken.getText().toString();
        Config.LLM_PROXY_ENABLED = swLlm.isChecked();
        Config.LLM_BASE_URL = etLlmUrl.getText().toString();
        Config.LLM_API_KEY = etLlmKey.getText().toString();
        Config.LLM_MODEL = etLlmModel.getText().toString();
        Config.save(this);
        Toast.makeText(this, "已保存 (重启超级小爱后生效)", Toast.LENGTH_LONG).show();
    }
}
