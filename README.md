# MiclawApiBridge

把小米超级小爱 (com.aios.osbot) 的 AI 能力暴露为本机 **OpenAI 兼容 HTTP API** 的 Xposed 模块（LibXposed API 102）。

> **v2.0**：恢复真实服务启动（HookEntry 双保险）、全新设置界面（端口/Token/LLM 代理/路由表/限流/日志开关）、限流、请求日志（`/v1/admin/logs`）、会话重置（`/v1/chat/reset`）、配置热重载（`/v1/admin/reload`）、AI 调用失败自动重试。

## ✨ 功能

- **OpenAI 兼容**：`POST /v1/chat/completions`（流式 SSE + 非流式）
- **多模态识图**：图片 URL / base64 / 本地路径 → 超级小爱识别
- **大模型化**：默认通用 AI 人设、JSON 模式、temperature/max_tokens
- **550 内置工具**：天气/日历/设备控制/文件等（`GET /v1/tools` 带描述）
- **多轮会话**：`user` 字段控制会话，上下文记忆
- **代码执行**：`POST /v1/exec`（root，shell/python）
- **管理**：状态页 / 请求日志 / 配置热重载 / 限流 / 重试 / 并发保护
- **全部走本地**：零外部 API 依赖（LLM 代理可选）

## 📱 安装

1. 下载 APK 安装
2. LSPosed（1.9.2+）启用模块，作用域勾选 `com.aios.osbot`
3. 打开模块图标配置（可选：Token、端口、LLM 代理）
4. 重启超级小爱

## 🔌 使用（OpenAI 客户端接入）

```
Base URL: http://127.0.0.1:8787/v1
API Key:  模块设置里的 Token（可留空）
Model:    osbot.main
```

```bash
curl http://127.0.0.1:8787/v1/chat/completions \
  -H "Authorization: Bearer 你的Token" \
  -d '{"model":"osbot.main","messages":[{"role":"user","content":"你好"}]}'
```

## 📡 API 端点

| 端点 | 说明 |
|---|---|
| `POST /v1/chat/completions` | OpenAI 对话（流式/非流式） |
| `POST /v1/completions` | legacy 兼容 |
| `POST /v1/exec` | 代码执行（shell/python，需 Token+root） |
| `POST /v1/chat` | 原生简版 |
| `POST /v1/chat/reset` | 清空会话 (v2.0) |
| `GET /v1/models` | 模型列表 |
| `GET /v1/tools` | 超级小爱 550 工具（带描述） |
| `GET /health` | 健康检查 |
| `GET /openapi.json` | OpenAPI 文档 |
| `GET /v1/admin/status` | 运行状态 |
| `GET /v1/admin/reload` | 重载配置 (v2.0 从设置界面热读) |
| `GET /v1/admin/logs` | 请求日志 (v2.0, 最近 100 条) |

## ⚙️ 配置（模块设置界面）

- **HTTP 端口**：默认 8787（被占自动避让 +1）
- **API Token**：鉴权；代码执行必填
- **LLM 代理**：Function Calling 可选（DeepSeek 等）
- **路由表**：`前缀=BaseURL|Key|模型名`

## 🛡️ 安全

- 仅监听 127.0.0.1（本机）
- `/v1/exec` 强制要求 Token（root 任意代码执行）
- 代码执行 30 秒超时自动终止

## 🐛 常见问题

- **服务没起来**：确认模块启用 + 作用域勾选 + 重启超级小爱
- **端口冲突**：自动避让（日志会显示实际端口）
- **代码执行超时**：先给超级小爱授权 root（KernelSU/Magisk）
- **流式不输出**：确认客户端支持 SSE

## 🚀 一键发布（源码仓库 + LSP 市场）

仓库自带 `release.sh`，一条命令完成：改版本号 → push → Actions 构建 → 同步到 LSP 市场仓库：

```bash
./release.sh 2.1 "修复端口冲突"          # 常规发布
./release.sh 2.1 "新功能" --docs         # 同时更新市场 README
```

- 自动计算 versionCode 和市场 tag 序号（`<N>-<版本>` 格式）
- 自动等待 Actions 构建完成并下载 APK 上传到市场仓库
- Token 从 git remote 自动提取（或设置 `GH_TOKEN`）
- 市场索引约 1-24h 内刷新到 https://modules.lsposed.org

## 🔨 开发

```bash
git clone https://github.com/guocheng1378/MiclawApiBridge
# Android Studio 打开，编译即可
```

---

## 📜 版本历史

- **2.0** (2026-08): 恢复真实服务启动（HookEntry 双保险 attach + onPackageReady）、全新设置界面（原生组件防闪退）、限流、请求日志、会话重置、配置热重载、失败自动重试、Verbose 开关
- **1.7.6** (diag): 极简诊断版（排查 UI 闪退）
- **1.7.5**: 纯 LibXposed102 模块
- **1.7.4**: system_server 加载保护
- **1.7.3**: 双入口排除模块自身进程
- **1.7.2**: UI 回退原生组件（修复闪退）
- **1.7.0**: Material3 分组卡片界面 + 状态实时检测
- **1.6.1**: attach 时机修复 + 防重入 + 端口自动避让
- **1.6.0**: Java 代码执行沙箱 /v1/exec
- **1.5.0**: 多模态识图 + 大模型化
- **1.4.0**: 默认全部走超级小爱（本地）
- **1.2.0**: 设置 UI + Function Calling
- **1.1.0**: OpenAI 标准 API
- **1.0.0**: 首个版本
