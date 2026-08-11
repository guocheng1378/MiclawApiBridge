package com.xiaomi.miclaw.bridge;

import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.UUID;

/**
 * 通过 LocalSocket 与超级小爱 CLI (osbot-cli) 通信
 */
public class CliClient {

    private final Context context;

    public CliClient(Context context) {
        this.context = context;
    }

    /** 自动探测 CLI socket 名 (复刻 CliTransportService.resolveSocketName) */
    public String resolveSocketName() {
        try {
            Class<?> cls = context.getClassLoader().loadClass(
                "com.aios.osbot.cli.server.CliTransportService");
            PackageManager pm = context.getPackageManager();
            ServiceInfo info = pm.getServiceInfo(
                new ComponentName(context, cls), PackageManager.GET_META_DATA);
            if (info.metaData != null) {
                String name = info.metaData.getString("com.aios.osbot.cli.socket_name");
                if (name != null && name.length() > 0) {
                    Logger.d("Socket resolved from meta-data: " + name);
                    return name;
                }
            }
        } catch (Exception e) {
            Logger.e("resolveSocketName fallback: " + e.getMessage());
        }
        return Config.CLI_SOCKET;
    }

    /** 检测 CLI socket 是否可达 */
    public boolean isSocketAlive(String socketName) {
        try {
            LocalSocket s = new LocalSocket();
            s.connect(new LocalSocketAddress(socketName));
            s.close();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** 拉起 CliTransportService */
    public void ensureService() {
        try {
            Class<?> cls = context.getClassLoader().loadClass(
                "com.aios.osbot.cli.server.CliTransportService");
            android.content.Intent intent = new android.content.Intent(context, cls);
            context.startService(intent);
        } catch (Exception e) {
            Logger.e("ensureService: " + e.getMessage());
        }
    }

    /** 发送 CLI 请求并等待流式响应, 拼接成完整回复 */
    public CliResult chat(String text, String chatId, String agentId) {
        LocalSocket sock = null;
        try {
            sock = new LocalSocket();
            sock.connect(new LocalSocketAddress(Config.activeSocket));
            sock.setSoTimeout((int) Config.READ_TIMEOUT);

            BufferedReader reader = new BufferedReader(
                new InputStreamReader(sock.getInputStream(), "UTF-8"));
            OutputStream os = sock.getOutputStream();

            String id = UUID.randomUUID().toString()
                .replace("-", "").substring(0, 16);
            String method = Config.STREAMING
                ? "conversation.send_streaming" : "conversation.send";

            if (chatId == null || chatId.length() == 0)
                chatId = Config.API_CHAT_ID;
            if (agentId == null || agentId.length() == 0)
                agentId = Config.defaultAgentId;

            JSONObject req = new JSONObject();
            req.put("type", method);
            req.put("id", id);
            req.put("text", text);
            if (chatId.length() > 0) req.put("chatId", chatId);
            if (agentId.length() > 0) req.put("agentId", agentId);

            Logger.d("CLI send: " + req.toString());
            os.write((req.toString() + "\n").getBytes("UTF-8"));
            os.flush();

            StringBuilder reply = new StringBuilder();
            String err = null;
            String lastChatId = null;
            int frames = 0;
            long deadline = System.currentTimeMillis() + Config.READ_TIMEOUT;

            while (true) {
                if (reader.ready()) {
                    String line = reader.readLine();
                    if (line == null) break;
                    frames++;

                    JSONObject frame;
                    try { frame = new JSONObject(line); }
                    catch (Exception e) { continue; }

                    String type = frame.optString("type", "");

                    if ("error".equals(type)) {
                        err = frame.optString("code", "ERROR")
                            + ": " + frame.optString("message", "");
                        Logger.e("CLI error: " + err);
                        break;
                    }
                    if ("end".equals(type)) break;

                    if ("stream".equals(type) || "response".equals(type)) {
                        JSONObject data = frame.optJSONObject("data");
                        if (data == null) continue;

                        if (data.has("chat_id"))
                            lastChatId = data.optString("chat_id");
                        if (data.has("chatId"))
                            lastChatId = data.optString("chatId");

                        String t = data.optString("text", null);
                        if (t != null) {
                            boolean isStreaming = data.optBoolean("is_streaming", true);
                            if (isStreaming) {
                                reply.append(t);
                            } else {
                                reply.setLength(0);
                                reply.append(t);
                            }
                        } else {
                            JSONObject m = data.optJSONObject("message");
                            if (m != null) {
                                String t2 = m.optString("text", null);
                                if (t2 != null) reply.append(t2);
                                if (m.has("chat_id"))
                                    lastChatId = m.optString("chat_id");
                            }
                        }
                    }
                } else {
                    if (System.currentTimeMillis() > deadline) {
                        Logger.e("CLI hard timeout, frames=" + frames);
                        break;
                    }
                    Thread.sleep(100);
                }
            }

            Logger.d("CLI done: len=" + reply.length()
                + " frames=" + frames + " chatId=" + lastChatId);
            return new CliResult(reply.toString(), err, lastChatId, frames);

        } catch (Exception e) {
            Logger.e("CLI exception: " + e.getMessage(), e);
            return new CliResult("", e.toString(), null, 0);
        } finally {
            if (sock != null) {
                try { sock.close(); } catch (Exception ignored) {}
            }
        }
    }

    /** 通过 CLI 发任意请求并返回首帧 JSONObject */
    public JSONObject sendRaw(String type, long timeoutMs) {
        LocalSocket sock = null;
        try {
            sock = new LocalSocket();
            sock.connect(new LocalSocketAddress(Config.activeSocket));
            sock.setSoTimeout((int) timeoutMs);

            BufferedReader reader = new BufferedReader(
                new InputStreamReader(sock.getInputStream(), "UTF-8"));
            OutputStream os = sock.getOutputStream();

            String id = UUID.randomUUID().toString()
                .replace("-", "").substring(0, 16);
            JSONObject req = new JSONObject();
            req.put("type", type);
            req.put("id", id);
            os.write((req.toString() + "\n").getBytes("UTF-8"));
            os.flush();

            long deadline = System.currentTimeMillis() + timeoutMs;
            while (System.currentTimeMillis() < deadline) {
                if (reader.ready()) {
                    String line = reader.readLine();
                    if (line == null) break;
                    try {
                        JSONObject frame = new JSONObject(line);
                        String t = frame.optString("type", "");
                        if ("error".equals(t)) {
                            Logger.e(type + " error: "
                                + frame.optString("message", ""));
                            return null;
                        }
                        if ("ok".equals(t) || "stream".equals(t)
                            || "response".equals(t)) {
                            return frame;
                        }
                    } catch (Exception ignored) {}
                } else {
                    Thread.sleep(50);
                }
            }
        } catch (Exception e) {
            Logger.e("sendRaw(" + type + "): " + e.getMessage());
        } finally {
            if (sock != null) {
                try { sock.close(); } catch (Exception ignored) {}
            }
        }
        return null;
    }

    /** 探测默认 Agent */
    public void discoverAgent() {
        JSONObject frame = sendRaw("agent.list", 6000);
        if (frame == null) {
            Logger.e("agent.list: no response");
            return;
        }
        try {
            JSONObject data = frame.optJSONObject("data");
            if (data != null) {
                JSONArray arr = data.optJSONArray("agents");
                if (arr != null && arr.length() > 0) {
                    JSONObject a = arr.getJSONObject(0);
                    Config.defaultAgentId = a.optString("id", "osbot.main");
                    Config.agentName = a.optString("name", "MiClaw");
                    Logger.d("Agent: " + Config.defaultAgentId
                        + " (" + Config.agentName + ")");
                }
            }
        } catch (Exception e) {
            Logger.e("agent parse: " + e.getMessage());
        }
    }

    /** 探测登录状态 */
    public void checkAuth() {
        JSONObject frame = sendRaw("auth.status", 5000);
        if (frame == null) return;
        try {
            JSONObject data = frame.optJSONObject("data");
            if (data != null) {
                boolean loggedIn = data.optBoolean("logged_in", false);
                String nick = data.optString("nickname", "");
                String uid = data.optString("user_id", "");
                Logger.d("Auth: logged_in=" + loggedIn
                    + " nick=" + nick + " uid=" + uid);
            }
        } catch (Exception e) {}
    }

    public static class CliResult {
        public final String reply;
        public final String error;
        public final String chatId;
        public final int frames;

        public CliResult(String reply, String error, String chatId, int frames) {
            this.reply = reply;
            this.error = error;
            this.chatId = chatId;
            this.frames = frames;
        }
    }
}
