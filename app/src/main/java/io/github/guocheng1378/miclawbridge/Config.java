package io.github.guocheng1378.miclawbridge;

public class Config {
    public static final int HTTP_PORT = 8787;
    public static final String API_TOKEN = "";       // 留空=不鉴权
    public static final String CLI_SOCKET = "osbot-cli";
    public static final boolean STREAMING = true;
    public static final long READ_TIMEOUT = 120000;  // 2分钟
    public static final String API_CHAT_ID = "api-gateway";
    public static final int THREAD_POOL_SIZE = 4;

    // --- 运行时自动探测 ---
    public static String activeSocket = CLI_SOCKET;
    public static String defaultAgentId = "osbot.main";
    public static String agentName = "MiClaw";
}
