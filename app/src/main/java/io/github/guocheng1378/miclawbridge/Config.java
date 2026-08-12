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

    // v2.0 新增: 限流 / 请求日志 / 重试 / Verbose
    public static int RATE_LIMIT = 0;           // 每分钟最大请求数, 0=关闭
    public static boolean REQ_LOGGING = true;   // 记录请求日志 (最近 100 条)
    public static boolean RETRY = true;         // AI 调用失败自动重试 1 次
    public static boolean VERBOSE = false;      // Verbose 调试日志

    // LLM 代理 (Function Calling) - API Key 只存本地, 不硬编码
    public static boolean LLM_PROXY_ENABLED = false; // 默认全部走超级小爱(本地), 需代理才改true
    public static String LLM_BASE_URL = "https://api.deepseek.com/v1";
    public static String LLM_API_KEY = "";
    public static String LLM_MODEL = "deepseek-v4-flash";

    // 路由表: 前缀 -> "BaseURL|APIKey|模型名" (来自设置界面, key 只存本机)
    public static java.util.Map<String, String> LLM_ROUTES = new java.util.HashMap<>();

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
            RATE_LIMIT = sp.getInt("rate_limit", RATE_LIMIT);
            REQ_LOGGING = sp.getBoolean("req_logging", REQ_LOGGING);
            RETRY = sp.getBoolean("retry", RETRY);
            VERBOSE = sp.getBoolean("verbose", VERBOSE);
            // 解析路由表 (每行 ROUTE_前缀=BaseURL|APIKey|模型名)
            LLM_ROUTES.clear();
            String routes = sp.getString("llm_routes", "");
            if (routes != null && !routes.isEmpty()) {
                for (String line : routes.split("\\n")) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    int eq = line.indexOf('=');
                    if (eq <= 0) continue;
                    String prefix = line.substring(0, eq).trim();
                    String val = line.substring(eq + 1).trim();
                    if (prefix.startsWith("ROUTE_")) prefix = prefix.substring(6);
                    if (!prefix.isEmpty() && !val.isEmpty()) {
                        LLM_ROUTES.put(prefix, val);
                    }
                }
            }
            Logger.d("Config loaded: port=" + HTTP_PORT
                + " proxy=" + LLM_PROXY_ENABLED
                + " routes=" + LLM_ROUTES.keySet().size()
                + " limit=" + RATE_LIMIT
                + " log=" + REQ_LOGGING
                + " retry=" + RETRY
                + " key=" + (LLM_API_KEY.isEmpty() ? "empty" : "***"));
        } catch (Exception e) {
            Logger.e("Config.loadFrom: " + e.getMessage());
        }
    }
}
