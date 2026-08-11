package io.github.guocheng1378.miclawbridge;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        SharedPreferences sp = getSharedPreferences(Config.PREFS, MODE_PRIVATE);

        EditText etPort = findViewById(R.id.et_port);
        EditText etToken = findViewById(R.id.et_token);
        Switch swProxy = findViewById(R.id.sw_proxy);
        EditText etBase = findViewById(R.id.et_base);
        EditText etKey = findViewById(R.id.et_key);
        EditText etModel = findViewById(R.id.et_model);
        TextView tvStatus = findViewById(R.id.tv_status);
        Button btnSave = findViewById(R.id.btn_save);

        // 回显当前配置
        etPort.setText(String.valueOf(sp.getInt("http_port", Config.HTTP_PORT)));
        etToken.setText(sp.getString("api_token", Config.API_TOKEN));
        swProxy.setChecked(sp.getBoolean("llm_proxy_enabled", Config.LLM_PROXY_ENABLED));
        etBase.setText(sp.getString("llm_base_url", Config.LLM_BASE_URL));
        etKey.setText(sp.getString("llm_api_key", Config.LLM_API_KEY));
        etModel.setText(sp.getString("llm_model", Config.LLM_MODEL));

        btnSave.setOnClickListener(v -> {
            try {
                sp.edit()
                    .putInt("http_port", Integer.parseInt(etPort.getText().toString().trim()))
                    .putString("api_token", etToken.getText().toString().trim())
                    .putBoolean("llm_proxy_enabled", swProxy.isChecked())
                    .putString("llm_base_url", etBase.getText().toString().trim())
                    .putString("llm_api_key", etKey.getText().toString().trim())
                    .putString("llm_model", etModel.getText().toString().trim())
                    .apply();
                tvStatus.setText("✅ 已保存！重启超级小爱后生效");
                tvStatus.setTextColor(0xFF2E7D32);
            } catch (Exception e) {
                tvStatus.setText("保存失败: " + e.getMessage());
                tvStatus.setTextColor(0xFFC62828);
            }
        });
    }
}
