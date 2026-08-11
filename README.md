# MiclawApiBridge

把小米超级小爱 (com.aios.osbot) 的 AI 能力暴露为本机 **OpenAI 兼容 HTTP API** 的 Xposed 模块。

## 功能
- 本机 HTTP 服务 (127.0.0.1:8787)
- OpenAI 兼容: `POST /v1/chat/completions`（流式/非流式）
- 模型列表: `GET /v1/models`
- 健康检查: `GET /health`
- 原生接口: `POST /v1/chat`
- 独立会话隔离（不污染主对话）
- 自动探测 Agent / socket / 登录状态

## 兼容性
- 基于 **LibXposed API 102**（最新 LSPosed 框架 API，Maven Central: io.github.libxposed:api:102.0.0）
- 入口: META-INF/xposed/java_init.list + module.prop (minApiVersion=101, targetApiVersion=102)
- 作用域: scope.list = com.aios.osbot

## 使用
1. 从 GitHub Release 下载 APK 或 Android Studio 编译
2. 安装并在 **LSPosed (1.9+)** 中启用（作用域: com.aios.osbot）
3. 重启超级小爱
4. 任意 OpenAI 客户端接入:
   - Base URL: `http://127.0.0.1:8787/v1`
   - API Key: 任意
   - Model: `osbot.main` / `software-dev` / `osbot.calendar` / `400000000000024`

## 配置
见 `Config.java`：端口、token、超时、会话ID 等。

## 依赖
- LSPosed / Xposed 框架 (API 82)
- 超级小爱 App (com.aios.osbot)
