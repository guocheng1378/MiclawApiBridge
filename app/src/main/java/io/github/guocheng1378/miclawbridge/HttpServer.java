package io.github.guocheng1378.miclawbridge;

import android.content.Context;
import org.json.JSONObject;
import org.json.JSONArray;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 极简 HTTP 服务器 (127.0.0.1)
 * 提供 OpenAI 兼容 API: /v1/chat/completions (流式+非流式) /v1/models /openapi.json /health
 */
public class HttpServer {

    private final Context context;
    private final CliClient cli;
    private static final boolean CORS = true;

    public HttpServer(Context context) {
        this.context = context;
        this.cli = new CliClient(context);
    }

    public void start() {
        new Thread(() -> {
            try {
                Config.activeSocket = cli.resolveSocketName();
                Logger.d("Socket: " + Config.activeSocket);
                if (!cli.isSocketAlive(Config.activeSocket)) {
                    Logger.d("CLI down, starting service...");
                    cli.ensureService();
                    for (int i = 0; i < 20; i++) {
                        Thread.sleep(500);
                        if (cli.isSocketAlive(Config.activeSocket)) break;
                    }
                }
                cli.discoverAgent();
                cli.checkAuth();
            } catch (Exception e) {
                Logger.e("Init error: " + e.getMessage());
            }
        }).start();

        new Thread(() -> {
            try {
                ServerSocket serverSocket = new ServerSocket();
                serverSocket.setReuseAddress(true);
                serverSocket.bind(new InetSocketAddress("127.0.0.1", Config.HTTP_PORT));
                ExecutorService executor = Executors.newFixedThreadPool(Config.THREAD_POOL_SIZE);
                Logger.d("HTTP listening on 127.0.0.1:" + Config.HTTP_PORT);
                while (true) {
                    Socket client = serverSocket.accept();
                    executor.submit(() -> handleClient(client));
                }
            } catch (Exception e) {
                Logger.e("HTTP server failed: " + e.getMessage(), e);
            }
        }).start();
    }

    private void handleClient(Socket client) {
        try {
            client.setSoTimeout(60000);
            InputStream is = client.getInputStream();
            OutputStream os = client.getOutputStream();

            String requestLine = readHttpLine(is);
            if (requestLine == null) { client.close(); return; }

            String[] parts = requestLine.split(" ");
            String method = parts[0];
            String target = parts.length > 1 ? parts[1] : "/";

            String apiKey = "";
            int contentLength = 0;
            while (true) {
                String line = readHttpLine(is);
                if (line == null || line.isEmpty()) break;
                int idx = line.indexOf(":");
                if (idx > 0) {
                    String name = line.substring(0, idx).trim().toLowerCase();
                    String value = line.substring(idx + 1).trim();
                    if ("x-api-key".equals(name)) apiKey = value;
                    if ("authorization".equals(name) && value.startsWith("Bearer "))
                        apiKey = value.substring(7).trim();
                    if ("content-length".equals(name)) {
                        try { contentLength = Integer.parseInt(value); }
                        catch (Exception e) {}
                    }
                }
            }

            String body = "";
            if (contentLength > 0) {
                byte[] bytes = new byte[contentLength];
                int off = 0;
                while (off < contentLength) {
                    int r = is.read(bytes, off, contentLength - off);
                    if (r < 0) break;
                    off += r;
                }
                body = new String(bytes, 0, off, "UTF-8");
            }

            String path = target;
            int qidx = target.indexOf("?");
            if (qidx >= 0) path = target.substring(0, qidx);

            // 鉴权 (OPTIONS 预检跳过)
            if (!"OPTIONS".equals(method)
                && Config.API_TOKEN.length() > 0
                && !Config.API_TOKEN.equals(apiKey)) {
                sendResponse(os, 401, OpenAiCompat.buildError(
                    "Invalid API key provided", "invalid_request_error", "invalid_api_key").toString());
                client.close(); return;
            }

            if ("OPTIONS".equals(method)) {
                sendResponse(os, 200, "{}");
            } else if ("/".equals(path)) {
                JSONObject r = new JSONObject();
                r.put("name", "MiclawApiBridge");
                r.put("version", "1.1.0");
                r.put("docs", "/openapi.json");
                r.put("models", "/v1/models");
                sendResponse(os, 200, r.toString());
            } else if ("/health".equals(path)) {
                JSONObject r = new JSONObject();
                r.put("status", "ok");
                r.put("agent", Config.defaultAgentId);
                r.put("socket", Config.activeSocket);
                sendResponse(os, 200, r.toString());
            } else if ("/openapi.json".equals(path)) {
                sendResponse(os, 200, OpenAiCompat.openapiDoc().toString());
            } else if ("/v1/models".equals(path)
                       && ("GET".equals(method) || "POST".equals(method))) {
                sendResponse(os, 200, OpenAiCompat.buildModelList().toString());
            } else if ("/v1/tools".equals(path) && "GET".equals(method)) {
                JSONObject tr = new JSONObject();
                tr.put("object", "list");
                tr.put("proxy", Config.LLM_PROXY_ENABLED && !Config.LLM_API_KEY.isEmpty());
                tr.put("model", Config.LLM_MODEL);
                tr.put("note", "超级小爱内置工具由它自动调用; LSPilot 自定义工具走 LLM 代理");
                sendResponse(os, 200, tr.toString());
            } else if ("/v1/chat/completions".equals(path) && "POST".equals(method)) {
                handleChatCompletions(os, body);
            } else if ("/v1/chat".equals(path) && "POST".equals(method)) {
                handleV1Chat(os, body);
            } else {
                sendResponse(os, 404, OpenAiCompat.buildError(
                    "Not Found", "invalid_request_error", "not_found").toString());
            }

            client.close();
        } catch (Exception e) {
            Logger.e("Handler error: " + e.getMessage());
            try { client.close(); } catch (Exception ignored) {}
        }
    }

    /** LLM 代理: 转发到 OpenAI 兼容模型 (DeepSeek), model 重写为代理模型, 支持流式透传 */
    private void proxyLLM(OutputStream os, String body, boolean stream) throws Exception {
        java.net.URL url = new java.net.URL(Config.LLM_BASE_URL + "/chat/completions");
        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Authorization", "Bearer " + Config.LLM_API_KEY);
        conn.setDoOutput(true);
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(60000);
        JSONObject b = new JSONObject(body);
        b.put("model", Config.LLM_MODEL);
        byte[] data = b.toString().getBytes("UTF-8");
        conn.getOutputStream().write(data);
        conn.getOutputStream().flush();
        int code = conn.getResponseCode();
        InputStream is = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
        if (stream) {
            StringBuilder h = new StringBuilder();
            h.append("HTTP/1.1 200 OK\r\n");
            if (CORS) h.append("Access-Control-Allow-Origin: *\r\n");
            h.append("Content-Type: text/event-stream; charset=utf-8\r\n");
            h.append("Cache-Control: no-cache\r\nConnection: close\r\n\r\n");
            os.write(h.toString().getBytes("UTF-8"));
            os.flush();
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) >= 0) { os.write(buf, 0, n); os.flush(); }
        } else {
            StringBuilder sb = new StringBuilder();
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) >= 0) sb.append(new String(buf, 0, n, "UTF-8"));
            sendResponse(os, code, sb.toString());
        }
        is.close();
    }

    private void handleV1Chat(OutputStream os, String body) throws Exception {
        JSONObject reqObj = new JSONObject(body);
        String text = reqObj.optString("text", "");
        String chatId = reqObj.has("chatId") ? reqObj.optString("chatId") : null;
        String agentId = reqObj.has("agentId") ? reqObj.optString("agentId") : null;

        if (text.length() == 0) {
            sendResponse(os, 400, OpenAiCompat.buildError(
                "missing 'text'", "invalid_request_error", "missing_text").toString());
            return;
        }

        CliClient.CliResult r = cli.chat(text, chatId, agentId);
        JSONObject resp = new JSONObject();
        resp.put("ok", r.error == null);
        resp.put("reply", r.reply);
        if (r.error != null) resp.put("error", r.error);
        if (r.chatId != null) resp.put("chatId", r.chatId);
        resp.put("frames", r.frames);
        sendResponse(os, 200, resp.toString());
    }

    private void handleChatCompletions(OutputStream os, String body) throws Exception {
        JSONObject reqObj = new JSONObject(body);
        boolean stream = reqObj.optBoolean("stream", false);
        String model = reqObj.optString("model", "miclaw");
        JSONArray messages = reqObj.optJSONArray("messages");
        JSONArray tools = reqObj.optJSONArray("tools");

        // LLM 代理: 带 tools 且已配置 key → 转发支持 function calling 的模型 (DeepSeek)
        if (Config.LLM_PROXY_ENABLED && Config.LLM_API_KEY != null
            && !Config.LLM_API_KEY.isEmpty() && tools != null && tools.length() > 0) {
            Logger.d("FunctionCalling: proxy to " + Config.LLM_MODEL);
            proxyLLM(os, body, stream);
            return;
        }
        // 拼接 messages (system + history + user)
        StringBuilder sb = new StringBuilder();
        if (messages != null) {
            for (int i = 0; i < messages.length(); i++) {
                JSONObject m = messages.optJSONObject(i);
                if (m == null) continue;
                String role = m.optString("role", "user");
                String content = m.optString("content", "");
                if (content.isEmpty()) continue;
                if ("system".equals(role)) {
                    sb.append("系统指令: ").append(content).append("\n");
                } else if ("assistant".equals(role)) {
                    sb.append("助手: ").append(content).append("\n");
                } else {
                    sb.append("用户: ").append(content).append("\n");
                }
            }
        }
        String text = sb.toString().trim();
        if (text.isEmpty()) {
            sendResponse(os, 400, OpenAiCompat.buildError(
                "messages is required", "invalid_request_error", "missing_messages").toString());
            return;
        }

        // model -> agentId (未知模型用默认)
        String agentId = model;
        if (agentId == null || agentId.isEmpty()
            || "miclaw".equals(agentId) || "gpt-3.5-turbo".equals(agentId)
            || "gpt-4".equals(agentId) || "gpt-4o".equals(agentId)
            || "gpt-4o-mini".equals(agentId)) {
            agentId = Config.defaultAgentId;
        }

        if (stream) {
            // SSE 流式
            StringBuilder sbHeader = new StringBuilder();
            sbHeader.append("HTTP/1.1 200 OK\r\n");
            if (CORS) sbHeader.append("Access-Control-Allow-Origin: *\r\n");
            sbHeader.append("Content-Type: text/event-stream; charset=utf-8\r\n");
            sbHeader.append("Cache-Control: no-cache\r\nConnection: close\r\n\r\n");
            os.write(sbHeader.toString().getBytes("UTF-8"));
            os.flush();

            CliClient.CliResult r = cli.chat(text, Config.API_CHAT_ID, agentId, t -> {
                try {
                    os.write(OpenAiCompat.buildStreamChunk(model, t, null).getBytes("UTF-8"));
                    os.flush();
                } catch (Exception ignored) {}
            });

            os.write(OpenAiCompat.buildStreamChunk(model, null, "stop").getBytes("UTF-8"));
            os.write("data: [DONE]\n\n".getBytes("UTF-8"));
            os.flush();
            return;
        }

        CliClient.CliResult r = cli.chat(text, Config.API_CHAT_ID, agentId);
        if (r.error != null) {
            sendResponse(os, 500, OpenAiCompat.buildError(
                r.error, "server_error", "upstream_error").toString());
            return;
        }
        sendResponse(os, 200, OpenAiCompat.buildSyncResponse(model, r.reply).toString());
    }

    private String readHttpLine(InputStream is) throws Exception {
        StringBuilder sb = new StringBuilder();
        while (true) {
            int b = is.read();
            if (b < 0) return null;
            if (b == 10) break;
            if (b != 13) sb.append((char) b);
        }
        return sb.toString();
    }

    /**
     * LLM 代理: 转发到 OpenAI 兼容模型 (DeepSeek), 支持流式透传
     * API Key 从 Config (SharedPreferences) 读取, 不写死在代码
     */
    private void proxyLLM(OutputStream os, String body, boolean stream) {
        java.net.HttpURLConnection conn = null;
        try {
            // 重写 model 为代理模型
            JSONObject b = new JSONObject(body);
            b.put("model", Config.LLM_MODEL);
            String outBody = b.toString();

            java.net.URL url = new java.net.URL(Config.LLM_BASE_URL + "/chat/completions");
            conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + Config.LLM_API_KEY);
            conn.setDoOutput(true);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(120000);
            conn.getOutputStream().write(outBody.getBytes("UTF-8"));

            int code = conn.getResponseCode();
            java.io.InputStream is = code >= 400 ? conn.getErrorStream() : conn.getInputStream();

            if (stream) {
                StringBuilder h = new StringBuilder();
                h.append("HTTP/1.1 200 OK\r\n");
                if (CORS) h.append("Access-Control-Allow-Origin: *\r\n");
                h.append("Content-Type: text/event-stream; charset=utf-8\r\n");
                h.append("Cache-Control: no-cache\r\nConnection: close\r\n\r\n");
                os.write(h.toString().getBytes("UTF-8"));
                os.flush();
                byte[] buf = new byte[8192];
                int n;
                while ((n = is.read(buf)) >= 0) {
                    os.write(buf, 0, n);
                    os.flush();
                }
            } else {
                StringBuilder sb = new StringBuilder();
                byte[] buf = new byte[8192];
                int n;
                while ((n = is.read(buf)) >= 0) {
                    sb.append(new String(buf, 0, n, "UTF-8"));
                }
                sendResponse(os, code, sb.toString());
            }
            if (is != null) try { is.close(); } catch (Exception ignored) {}
        } catch (Exception e) {
            Logger.e("proxyLLM: " + e.getMessage());
            try {
                sendResponse(os, 502, OpenAiCompat.buildError(
                    "LLM proxy error: " + e.getMessage(), "server_error", "proxy_error").toString());
            } catch (Exception ignored) {}
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private void sendResponse(OutputStream os, int code, String body) throws Exception {
        String reason = "OK";
        if (code == 400) reason = "Bad Request";
        if (code == 401) reason = "Unauthorized";
        if (code == 404) reason = "Not Found";
        if (code == 429) reason = "Too Many Requests";
        if (code == 500) reason = "Internal Server Error";
        byte[] bytes = body.getBytes("UTF-8");
        StringBuilder header = new StringBuilder();
        header.append("HTTP/1.1 ").append(code).append(" ").append(reason).append("\r\n");
        if (CORS) {
            header.append("Access-Control-Allow-Origin: *\r\n");
            header.append("Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n");
            header.append("Access-Control-Allow-Headers: Content-Type, Authorization, X-Api-Key\r\n");
        }
        header.append("Content-Type: application/json; charset=utf-8\r\n");
        header.append("Content-Length: ").append(bytes.length).append("\r\n");
        header.append("Connection: close\r\n\r\n");
        os.write(header.toString().getBytes("UTF-8"));
        os.write(bytes);
        os.flush();
    }
}
