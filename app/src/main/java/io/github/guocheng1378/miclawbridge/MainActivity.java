package io.github.guocheng1378.miclawbridge;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * MiclawApiBridge v2.0 设置界面 (纯原生组件, 无外部依赖, 稳定防闪退)
 *
 * 配置项:
 *  - HTTP 端口 (默认 8787, 被占自动避让)
 *  - API Token (留空=不鉴权; /v1/exec 强制要求)
 *  - LLM 代理 (Function Calling, 可选, Key 只存本机)
 *  - 路由表 (每行 ROUTE_前缀=BaseURL|APIKey|模型名)
 *  - 高级: 限流 / 请求日志 / 失败重试 / Verbose 日志
 */
public class MainActivity extends Activity {

    private EditText etPort, etToken, etBaseUrl, etApiKey, etModel, etRoutes, etRateLimit;
    private CheckBox cbProxy, cbReqLog, cbRetry, cbVerbose;
    private TextView tvStatus, tvRoot;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(24));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.MATCH_PARENT));

        // ---------- 标题 ----------
        TextView tvTitle = new TextView(this);
        tvTitle.setText("Miclaw API Bridge v2.0");
        tvTitle.setTextSize(22);
        tvTitle.setTypeface(null, Typeface.BOLD);
        tvTitle.setTextColor(Color.parseColor("#1A73E8"));
        tvTitle.setPadding(0, 0, 0, dp(4));
        root.addView(tvTitle);

        TextView tvSub = new TextView(this);
        tvSub.setText("把小米超级小爱 (com.aios.osbot) 暴露为本机 OpenAI 兼容 API\n设置保存在本机, 保存后需重启超级小爱生效");
        tvSub.setTextSize(12);
        tvSub.setTextColor(Color.parseColor("#666666"));
        tvSub.setPadding(0, 0, 0, dp(12));
        root.addView(tvSub);

        // ---------- 状态卡片 ----------
        tvStatus = new TextView(this);
        tvStatus.setText("● 检测中...");
        tvStatus.setTextSize(14);
        tvStatus.setPadding(dp(12), dp(10), dp(12), dp(10));
        tvStatus.setBackgroundColor(Color.parseColor("#F2F4F7"));
        root.addView(tvStatus);

        Button btnRefresh = new Button(this);
        btnRefresh.setText("刷新状态");
        btnRefresh.setOnClickListener(v -> checkStatus());
        root.addView(btnRefresh);

        tvRoot = new TextView(this);
        tvRoot.setText("🔑 Root: 检测中...");
        tvRoot.setTextSize(14);
        tvRoot.setPadding(dp(12), dp(10), dp(12), dp(10));
        tvRoot.setBackgroundColor(Color.parseColor("#F2F4F7"));
        root.addView(tvRoot);

        // ---------- 基本设置 ----------
        root.addView(sectionLabel("基本设置"));

        etPort = input("HTTP 端口 (默认 8787)", "" + Config.HTTP_PORT, InputType.TYPE_CLASS_NUMBER);
        root.addView(fieldBlock("HTTP 端口", etPort));

        etToken = input("留空=不鉴权", Config.API_TOKEN, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        root.addView(fieldBlock("API Token", etToken));

        etRateLimit = input("0=关闭限流", "" + Config.RATE_LIMIT, InputType.TYPE_CLASS_NUMBER);
        root.addView(fieldBlock("限流 (次/分钟, 0=关闭)", etRateLimit));

        // ---------- LLM 代理 ----------
        root.addView(sectionLabel("LLM 代理 (Function Calling)"));

        cbProxy = new CheckBox(this);
        cbProxy.setText("启用 LLM 代理 (带 tools 请求转发到外部 LLM)");
        cbProxy.setChecked(Config.LLM_PROXY_ENABLED);
        root.addView(cbProxy);

        etBaseUrl = input("https://api.deepseek.com/v1", Config.LLM_BASE_URL, InputType.TYPE_CLASS_TEXT);
        root.addView(fieldBlock("Base URL", etBaseUrl));

        etApiKey = input("仅存本机", Config.LLM_API_KEY, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        root.addView(fieldBlock("API Key", etApiKey));

        etModel = input("deepseek-v4-flash", Config.LLM_MODEL, InputType.TYPE_CLASS_TEXT);
        root.addView(fieldBlock("模型名", etModel));

        etRoutes = new EditText(this);
        etRoutes.setText(joinRoutes(Config.LLM_ROUTES));
        etRoutes.setTextSize(13);
        etRoutes.setHint("路由表: 每行  前缀=BaseURL|APIKey|模型名\n例: deepseek=https://api.deepseek.com/v1|sk-xxx|deepseek-chat\n     step=https://api.stepfun.com/v1|sk-xxx|step-3.7-flash");
        etRoutes.setGravity(Gravity.TOP | Gravity.START);
        etRoutes.setMinLines(4);
        root.addView(fieldBlock("路由表", etRoutes));

        // ---------- 高级 ----------
        root.addView(sectionLabel("高级"));

        cbReqLog = new CheckBox(this);
        cbReqLog.setText("记录请求日志 (最近 100 条, GET /v1/admin/logs)");
        cbReqLog.setChecked(Config.REQ_LOGGING);
        root.addView(cbReqLog);

        cbRetry = new CheckBox(this);
        cbRetry.setText("AI 调用失败自动重试 1 次");
        cbRetry.setChecked(Config.RETRY);
        root.addView(cbRetry);

        cbVerbose = new CheckBox(this);
        cbVerbose.setText("Verbose 调试日志");
        cbVerbose.setChecked(Config.VERBOSE);
        root.addView(cbVerbose);

        // ---------- 保存 ----------
        Button btnSave = new Button(this);
        btnSave.setText("保存配置");
        btnSave.setTextSize(16);
        btnSave.setOnClickListener(v -> saveConfig());
        root.addView(btnSave);

        TextView tvHint = new TextView(this);
        tvHint.setText("保存后重启超级小爱使配置生效。\n\n接入方式:\nBase URL: http://127.0.0.1:" + Config.HTTP_PORT + "/v1\nModel: osbot.main\n\n端点: /v1/chat/completions · /v1/chat · /v1/exec · /v1/tools · /v1/models · /health · /v1/admin/status · /v1/admin/logs");
        tvHint.setTextSize(12);
        tvHint.setTextColor(Color.parseColor("#888888"));
        tvHint.setPadding(0, dp(16), 0, 0);
        root.addView(tvHint);

        setContentView(scroll);
        checkStatus();
    }

    // ---------- UI 构建辅助 ----------

    private TextView sectionLabel(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(15);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setTextColor(Color.parseColor("#1A73E8"));
        tv.setPadding(0, dp(16), 0, dp(4));
        return tv;
    }

    private LinearLayout fieldBlock(String label, View edit) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, dp(4), 0, dp(4));
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextSize(13);
        tv.setTextColor(Color.parseColor("#555555"));
        box.addView(tv);
        box.addView(edit);
        return box;
    }

    private EditText input(String hint, String value, int inputType) {
        EditText et = new EditText(this);
        et.setHint(hint);
        et.setText(value == null ? "" : value);
        et.setInputType(inputType);
        et.setTextSize(15);
        return et;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private String joinRoutes(java.util.Map<String, String> routes) {
        StringBuilder sb = new StringBuilder();
        if (routes != null) {
            for (java.util.Map.Entry<String, String> e : routes.entrySet()) {
                sb.append("ROUTE_").append(e.getKey()).append("=").append(e.getValue()).append("\n");
            }
        }
        return sb.toString();
    }

    // ---------- 状态检测 ----------

    private void checkStatus() {
        int port;
        try { port = Integer.parseInt(etPort.getText().toString().trim()); }
        catch (Exception e) { port = Config.HTTP_PORT; }
        final int targetPort = port;
        tvStatus.setText("● 检测中: 127.0.0.1:" + targetPort + " ...");
        new Thread(() -> {
            boolean ok = isPortOpen(targetPort);
            boolean rootOk = RootUtil.isRootAvailable();
            handler.post(() -> {
                if (ok) {
                    tvStatus.setText("● 服务运行中: http://127.0.0.1:" + targetPort);
                    tvStatus.setTextColor(Color.parseColor("#188038"));
                } else {
                    tvStatus.setText("○ 服务未运行: 127.0.0.1:" + targetPort + " (需 LSPosed 启用模块 + 作用域勾选 com.aios.osbot + 重启超级小爱)");
                    tvStatus.setTextColor(Color.parseColor("#D93025"));
                }
                if (rootOk) {
                    tvRoot.setText("🔑 Root: 可用 (su 正常, /v1/exec 代码执行已开启)");
                    tvRoot.setTextColor(Color.parseColor("#188038"));
                } else {
                    tvRoot.setText("🔑 Root: 不可用 - 请到 Magisk/KernelSU 授权 com.aios.osbot (或不用 /v1/exec)");
                    tvRoot.setTextColor(Color.parseColor("#B06000"));
                }
            });
        }).start();
    }

    private boolean isPortOpen(int port) {
        try {
            Socket s = new Socket();
            s.connect(new InetSocketAddress("127.0.0.1", port), 800);
            s.close();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ---------- 保存 ----------

    private void saveConfig() {
        try {
            int port = Integer.parseInt(etPort.getText().toString().trim());
            if (port < 1 || port > 65535) port = 8787;
            Config.HTTP_PORT = port;
        } catch (Exception e) { /* 保持默认 */ }

        Config.API_TOKEN = etToken.getText().toString().trim();
        Config.LLM_PROXY_ENABLED = cbProxy.isChecked();
        Config.LLM_BASE_URL = etBaseUrl.getText().toString().trim();
        Config.LLM_API_KEY = etApiKey.getText().toString().trim();
        Config.LLM_MODEL = etModel.getText().toString().trim();
        Config.REQ_LOGGING = cbReqLog.isChecked();
        Config.RETRY = cbRetry.isChecked();
        Config.VERBOSE = cbVerbose.isChecked();
        try { Config.RATE_LIMIT = Integer.parseInt(etRateLimit.getText().toString().trim()); }
        catch (Exception e) { Config.RATE_LIMIT = 0; }

        // 解析路由表
        Config.LLM_ROUTES.clear();
        String[] lines = etRoutes.getText().toString().split("\\n");
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            int eq = line.indexOf('=');
            if (eq <= 0) continue;
            String prefix = line.substring(0, eq).trim();
            String val = line.substring(eq + 1).trim();
            if (prefix.startsWith("ROUTE_")) prefix = prefix.substring(6);
            if (!prefix.isEmpty() && !val.isEmpty()) Config.LLM_ROUTES.put(prefix, val);
        }

        // 持久化
        SharedPreferences sp = getSharedPreferences(Config.PREFS, Context.MODE_PRIVATE);
        SharedPreferences.Editor ed = sp.edit();
        ed.putInt("http_port", Config.HTTP_PORT);
        ed.putString("api_token", Config.API_TOKEN);
        ed.putBoolean("llm_proxy_enabled", Config.LLM_PROXY_ENABLED);
        ed.putString("llm_base_url", Config.LLM_BASE_URL);
        ed.putString("llm_api_key", Config.LLM_API_KEY);
        ed.putString("llm_model", Config.LLM_MODEL);
        ed.putBoolean("req_logging", Config.REQ_LOGGING);
        ed.putBoolean("retry", Config.RETRY);
        ed.putBoolean("verbose", Config.VERBOSE);
        ed.putInt("rate_limit", Config.RATE_LIMIT);
        ed.putString("llm_routes", etRoutes.getText().toString().trim());
        ed.apply();

        // 热重载: 宿主进程内存里的 Config 由 HttpServer 每请求读取; 通知已保存
        Toast("配置已保存! 重启超级小爱后生效 (或调用 GET /v1/admin/reload)");
    }

    private void Toast(String msg) {
        android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_LONG).show();
    }
}
