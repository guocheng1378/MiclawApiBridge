package io.github.guocheng1378.miclawbridge;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * OpenAI Chat Completions 格式转换
 */
public class OpenAiCompat {

    /** 从 OpenAI messages 数组提取 text（拼接 system + user） */
    public static String extractText(JSONArray messages) {
        if (messages == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < messages.length(); i++) {
            JSONObject m = messages.optJSONObject(i);
            if (m == null) continue;
            String role = m.optString("role", "");
            String content = m.optString("content", "");
            if ("system".equals(role)) {
                sb.append("[系统指令] ").append(content).append(" [/系统指令]\n");
            } else if ("user".equals(role)) {
                sb.append("用户: ").append(content).append("\n");
            } else if ("assistant".equals(role)) {
                sb.append("助手: ").append(content).append("\n");
            }
        }
        return sb.toString().trim();
    }

    /** 构建非流式 OpenAI 响应 */
    public static JSONObject buildSyncResponse(String model, String content) {
        try {
            JSONObject resp = new JSONObject();
            resp.put("id", "chatcmpl-" + System.currentTimeMillis());
            resp.put("object", "chat.completion");
            resp.put("created", System.currentTimeMillis() / 1000);
            resp.put("model", model);

            JSONArray choices = new JSONArray();
            JSONObject choice = new JSONObject();
            choice.put("index", 0);

            JSONObject msg = new JSONObject();
            msg.put("role", "assistant");
            msg.put("content", content == null ? "" : content);
            choice.put("message", msg);
            choice.put("finish_reason", "stop");
            choices.put(choice);
            resp.put("choices", choices);

            JSONObject usage = new JSONObject();
            usage.put("prompt_tokens", 0);
            usage.put("completion_tokens", 0);
            usage.put("total_tokens", 0);
            resp.put("usage", usage);
            return resp;
        } catch (Exception e) {
            return errorResponse("Failed to build response: " + e.getMessage());
        }
    }

    /** 构建流式 SSE 的 OpenAI chunk */
    public static String buildStreamChunk(String model, String content, String finishReason) {
        try {
            JSONObject chunk = new JSONObject();
            chunk.put("id", "chatcmpl-" + System.currentTimeMillis());
            chunk.put("object", "chat.completion.chunk");
            chunk.put("created", System.currentTimeMillis() / 1000);
            chunk.put("model", model);

            JSONArray choices = new JSONArray();
            JSONObject choice = new JSONObject();
            choice.put("index", 0);

            JSONObject delta = new JSONObject();
            delta.put("role", "assistant");
            if (content != null) delta.put("content", content);
            choice.put("delta", delta);

            if (finishReason != null) {
                choice.put("finish_reason", finishReason);
            } else {
                choice.put("finish_reason", JSONObject.NULL);
            }

            choices.put(choice);
            chunk.put("choices", choices);
            return "data: " + chunk.toString() + "\n\n";
        } catch (Exception e) {
            return "";
        }
    }

    /** 构建模型列表 */
    public static JSONObject buildModelList() {
        try {
            JSONObject resp = new JSONObject();
            resp.put("object", "list");
            JSONArray data = new JSONArray();

            String[][] models = {
                {Config.defaultAgentId, Config.agentName},
                {"osbot.taiyi", "端侧Agent"},
                {"osbot.calendar", "日程助手"},
                {"software-dev", "软件开发官"},
                {"400000000000024", "Wind投资助手"}
            };

            for (String[] m : models) {
                JSONObject model = new JSONObject();
                model.put("id", m[0]);
                model.put("object", "model");
                model.put("created", System.currentTimeMillis() / 1000);
                model.put("owned_by", "miclaw");
                model.put("description", m[1]);
                data.put(model);
            }

            resp.put("data", data);
            return resp;
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    public static JSONObject errorResponse(String msg) {
        return buildError(msg, "server_error", null);
    }

    /** 标准 OpenAI 错误对象 */
    public static JSONObject buildError(String message, String type, String code) {
        try {
            JSONObject r = new JSONObject();
            JSONObject e = new JSONObject();
            e.put("message", message != null ? message : "Internal Server Error");
            e.put("type", type != null ? type : "server_error");
            if (code != null) e.put("code", code);
            r.put("error", e);
            return r;
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    /** OpenAPI 3.0 文档 */
    public static JSONObject openapiDoc() {
        try {
            JSONObject doc = new JSONObject();
            doc.put("openapi", "3.0.0");
            JSONObject info = new JSONObject();
            info.put("title", "MiclawApiBridge");
            info.put("version", "1.1.0");
            info.put("description", "把小米超级小爱(com.aios.osbot)的 AI 能力暴露为 OpenAI 兼容 API");
            doc.put("info", info);

            JSONObject paths = new JSONObject();
            JSONObject chatPath = new JSONObject();
            JSONObject post = new JSONObject();
            post.put("operationId", "createChatCompletion");
            post.put("summary", "Create a chat completion");
            JSONObject reqBody = new JSONObject();
            JSONArray required = new JSONArray();
            required.put("messages");
            JSONObject schema = new JSONObject();
            schema.put("type", "object");
            JSONObject props = new JSONObject();
            JSONObject ms = new JSONObject();
            ms.put("type", "array");
            ms.put("items", new JSONObject().put("type", "object"));
            props.put("messages", ms);
            props.put("model", new JSONObject().put("type", "string"));
            props.put("stream", new JSONObject().put("type", "boolean"));
            schema.put("properties", props);
            schema.put("required", required);
            reqBody.put("content", new JSONObject().put("application/json", new JSONObject().put("schema", schema)));
            post.put("requestBody", reqBody);
            JSONObject resp200 = new JSONObject();
            resp200.put("description", "OK");
            resp200.put("content", new JSONObject().put("application/json", new JSONObject()));
            post.put("responses", new JSONObject().put("200", resp200));
            chatPath.put("post", post);
            paths.put("/v1/chat/completions", chatPath);
            doc.put("paths", paths);
            return doc;
        } catch (Exception e) {
            return new JSONObject();
        }
    }
}
