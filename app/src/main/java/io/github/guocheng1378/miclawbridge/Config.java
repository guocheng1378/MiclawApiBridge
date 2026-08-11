package io.github.guocheng1378.miclawbridge;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 模块配置: 支持通过设置界面(SharedPreferences)覆盖, 不硬编码敏感信息
 */
public class Config {
    public static final String PREFS = "miclaw_config";

    // HTTP 服务
    public static int HTTP_PORT = 8787;
    public static String API_TOKEN = "";        // 留空=不鉴权
    public static final String CLI_SOCKET = "osbot-cli";
    public static boolean STREAMING = true;
    public static long READ_TIMEOUT = 120000;
    public static String API_CHAT_ID = "api-gateway";
    public static int THREAD_POOL_SIZE = 4;

    // LLM 代理 (Function Calling) - API Key 只存本地, 不硬编码
    public static boolean LLM_PROXY_ENABLED = true;
    public static String LLM_BASE_URL = "https://api.deepseek.com/v1";
    public static String LLM_API_KEY = "";
    public static String LLM_MODEL = "deepseek-v4-flash";

    // 运行时自动探测
    public static String activeSocket = CLI_SOCKET;
    public static String defaultAgentId = "osbot.main";
    public static String agentName = "MiClaw";

    /** 从设置读取配置 (模块 UI 保存后, 宿主进程启动时调用) */
    public static void loadFrom(Context context) {
        try {
            SharedPreferences sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            HTTP_PORT = sp.getInt("http_port", HTTP_PORT);
            API_TOKEN = sp.getString("api_token", API_TOKEN);
            LLM_PROXY_ENABLED = sp.getBoolean("llm_proxy_enabled", LLM_PROXY_ENABLED);
            LLM_BASE_URL = sp.getString("llm_base_url", LLM_BASE_URL);
            LLM_API_KEY = sp.getString("llm_api_key", LLM_API_KEY);
            LLM_MODEL = sp.getString("llm_model", LLM_MODEL);
            Logger.d("Config loaded: port=" + HTTP_PORT
                + " proxy=" + LLM_PROXY_ENABLED
                + " key=" + (LLM_API_KEY.isEmpty() ? "empty" : "***"));
        } catch (Exception e) {
            Logger.e("Config.loadFrom: " + e.getMessage());
        }
    }
}
