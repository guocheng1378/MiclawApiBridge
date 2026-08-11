package io.github.guocheng1378.miclawbridge;

import android.content.Context;
import android.content.SharedPreferences;

public class Config {
    public static int HTTP_PORT = 8787;
    public static String API_TOKEN = "";       // 留空=不鉴权
    public static String CLI_SOCKET = "osbot-cli";
    public static boolean STREAMING = true;
    public static long READ_TIMEOUT = 120000;  // 2分钟
    public static String API_CHAT_ID = "api-gateway";
    public static int THREAD_POOL_SIZE = 4;

    // LLM 代理 (Function Calling) - API Key 只存本机 SharedPreferences
    public static boolean LLM_PROXY_ENABLED = true;
    public static String LLM_BASE_URL = "https://api.deepseek.com/v1";
    public static String LLM_API_KEY = "";
    public static String LLM_MODEL = "deepseek-v4-flash";

    // 运行时自动探测
    public static String activeSocket = CLI_SOCKET;
    public static String defaultAgentId = "osbot.main";
    public static String agentName = "MiClaw";

    private static final String PREFS = "miclaw_bridge_config";

    public static void load(Context ctx) {
        try {
            SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            HTTP_PORT = sp.getInt("http_port", HTTP_PORT);
            API_TOKEN = sp.getString("api_token", API_TOKEN);
            LLM_PROXY_ENABLED = sp.getBoolean("llm_proxy_enabled", LLM_PROXY_ENABLED);
            LLM_BASE_URL = sp.getString("llm_base_url", LLM_BASE_URL);
            LLM_API_KEY = sp.getString("llm_api_key", LLM_API_KEY);
            LLM_MODEL = sp.getString("llm_model", LLM_MODEL);
        } catch (Exception ignored) {}
    }

    public static void save(Context ctx) {
        try {
            SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            sp.edit()
                .putInt("http_port", HTTP_PORT)
                .putString("api_token", API_TOKEN)
                .putBoolean("llm_proxy_enabled", LLM_PROXY_ENABLED)
                .putString("llm_base_url", LLM_BASE_URL)
                .putString("llm_api_key", LLM_API_KEY)
                .putString("llm_model", LLM_MODEL)
                .apply();
        } catch (Exception ignored) {}
    }
}
