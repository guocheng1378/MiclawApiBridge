package com.xiaomi.miclaw.bridge;

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
 * 提供 OpenAI 兼容的 /v1/chat/completions 端点
 */
public class HttpServer {

    private final Context context;
    private final CliClient cli;

    public HttpServer(Context context) {
        this.context = context;
        this.cli = new CliClient(context);
    }

    public void start() {
        // 后台初始化: 探测 socket / agent / auth
        new Thread(() -> {
            try {
                Config.activeSocket = cli.resolveSocketName();
                Logger.d("Socket: " + Config.activeSocket);
                if (!cli.isSocketAlive(Config.activeSocket)) {
                    Logger.d("CLI down, starting service...");
                    cli.ensureService();
                    for (int i = 0; i < 20; i++) {
                        Thread.sleep(500);
                        if (cli.isSocketAlive(Config.activeSocket)) {
                            Logger.d("CLI up after " + ((i+1)*500) + "ms");
                            break;
                        }
                    }
                } else {
                    Logger.d("CLI already alive");
                }
                cli.discoverAgent();
                cli.checkAuth();
            } catch (Exception e) {
                Logger.e("Init error: " + e.getMessage());
            }
        }).start();

        // HTTP 服务
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
                    if ("authorization".equals(name))
                        apiKey = value.replace("Bearer ", "");
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

            if (Config.API_TOKEN.length() > 0
                && !Config.API_TOKEN.equals(apiKey)) {
                sendResponse(os, 401, "{\"error\":\"unauthorized\"}");
                client.close(); return;
            }

            String path = target;
            int qidx = target.indexOf("?");
            if (qidx >= 0) path = target.substring(0, qidx);

            if ("/health".equals(path)) {
                handleHealth(os);
            } else if ("/v1/models".equals(path) && "GET".equals(method)) {
                sendResponse(os, 200, OpenAiCompat.buildModelList().toString());
            } else if ("/v1/chat/completions".equals(path) && "POST".equals(method)) {
                handleChatCompletions(os, body);
            } else if ("/v1/chat".equals(path) && "POST".equals(method)) {
                handleV1Chat(os, body);
            } else {
                sendResponse(os, 404, "{\"error\":\"not found\"}");
            }

            client.close();
        } catch (Exception e) {
            Logger.e("Handler error: " + e.getMessage());
            try { client.close(); } catch (Exception ignored) {}
        }
    }

    private void handleHealth(OutputStream os) throws Exception {
        JSONObject r = new JSONObject();
        r.put("ok", true);
        r.put("agent", Config.defaultAgentId);
        r.put("socket", Config.activeSocket);
        r.put("version", "1.0.0");
        sendResponse(os, 200, r.toString());
    }

    private void handleV1Chat(OutputStream os, String body) throws Exception {
        JSONObject reqObj = new JSONObject(body);
        String text = reqObj.optString("text", "");
        String chatId = reqObj.has("chatId") ? reqObj.optString("chatId") : null;
        String agentId = reqObj.has("agentId") ? reqObj.optString("agentId") : null;

        if (text.length() == 0) {
            sendResponse(os, 400, "{\"error\":\"missing 'text'\"}");
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
        String text = OpenAiCompat.extractText(messages);

        if (text.length() == 0) {
            sendResponse(os, 400, "{\"error\":{\"message\":\"missing user message\",\"type\":\"invalid_request_error\"}}");
            return;
        }

        // model -> agentId 映射
        String agentId = model;
        if (agentId == null || agentId.length() == 0
            || "miclaw".equals(agentId) || "gpt-3.5-turbo".equals(agentId)
            || "gpt-4".equals(agentId) || "gpt-4o".equals(agentId)
            || "gpt-4o-mini".equals(agentId)) {
            agentId = Config.defaultAgentId;
        }

        if (stream) {
            // 流式: 同步聚合后逐段推送(简化: 一次推送完整内容 + done)
            sendResponse(os, 200, "stream mode not implemented in standalone; use sync");
            return;
        }

        CliClient.CliResult r = cli.chat(text, Config.API_CHAT_ID, agentId);
        if (r.error != null) {
            sendResponse(os, 200, OpenAiCompat.errorResponse(r.error).toString());
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

    private void sendResponse(OutputStream os, int code, String body) throws Exception {
        String reason = "OK";
        if (code == 400) reason = "Bad Request";
        if (code == 401) reason = "Unauthorized";
        if (code == 404) reason = "Not Found";
        byte[] bytes = body.getBytes("UTF-8");
        String header = "HTTP/1.1 " + code + " " + reason + "\r\n"
            + "Content-Type: application/json; charset=utf-8\r\n"
            + "Content-Length: " + bytes.length + "\r\n"
            + "Connection: close\r\n\r\n";
        os.write(header.getBytes("UTF-8"));
        os.write(bytes);
        os.flush();
    }
}
