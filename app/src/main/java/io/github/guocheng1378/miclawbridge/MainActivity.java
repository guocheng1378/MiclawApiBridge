package io.github.guocheng1378.miclawbridge;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    private TextView tvServerStatus;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Runnable statusRunnable = new Runnable() {
        @Override public void run() {
            checkServerStatus();
            handler.postDelayed(this, 3000);
        }
    };

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
        EditText etRoutes = findViewById(R.id.et_routes);
        Button btnSave = findViewById(R.id.btn_save);
        tvServerStatus = findViewById(R.id.tv_server_status);
        TextView tvVersion = findViewById(R.id.tv_version);

        tvVersion.setText("v1.7.2");

        // 回显
        etPort.setText(String.valueOf(sp.getInt("http_port", Config.HTTP_PORT)));
        etToken.setText(sp.getString("api_token", Config.API_TOKEN));
        swProxy.setChecked(sp.getBoolean("llm_proxy_enabled", Config.LLM_PROXY_ENABLED));
        etBase.setText(sp.getString("llm_base_url", Config.LLM_BASE_URL));
        etKey.setText(sp.getString("llm_api_key", Config.LLM_API_KEY));
        etModel.setText(sp.getString("llm_model", Config.LLM_MODEL));
        etRoutes.setText(sp.getString("llm_routes", ""));

        checkServerStatus();
        handler.postDelayed(statusRunnable, 3000);

        btnSave.setOnClickListener(v -> {
            try {
                sp.edit()
                    .putInt("http_port", Integer.parseInt(etPort.getText().toString().trim()))
                    .putString("api_token", etToken.getText().toString().trim())
                    .putBoolean("llm_proxy_enabled", swProxy.isChecked())
                    .putString("llm_base_url", etBase.getText().toString().trim())
                    .putString("llm_api_key", etKey.getText().toString().trim())
                    .putString("llm_model", etModel.getText().toString().trim())
                    .putString("llm_routes", etRoutes.getText().toString().trim())
                    .apply();
                Toast.makeText(this, "✅ 已保存！重启超级小爱后生效", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(this, "保存失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void checkServerStatus() {
        final int port;
        try {
            port = Integer.parseInt(((EditText) findViewById(R.id.et_port)).getText().toString().trim());
        } catch (Exception e) {
            return;
        }
        new Thread(() -> {
            boolean up = false;
            try {
                java.net.Socket s = new java.net.Socket();
                s.connect(new java.net.InetSocketAddress("127.0.0.1", port), 800);
                s.close();
                up = true;
            } catch (Exception ignored) {}
            final boolean finalUp = up;
            handler.post(() -> {
                if (finalUp) {
                    tvServerStatus.setText("● 运行中  127.0.0.1:" + port);
                    tvServerStatus.setTextColor(0xFF2E7D32);
                } else {
                    tvServerStatus.setText("○ 未运行（需启用模块并重启超级小爱）");
                    tvServerStatus.setTextColor(0xFFC62828);
                }
            });
        }).start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(statusRunnable);
    }
}
