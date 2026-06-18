# AI Chat App (Android, Java 7, AIDE)

一款用 **Java 7** 编写、面向 **Android** 平台、**AIDE** 直接打开即可编译运行的
AI 聊天客户端。完整覆盖你列出的 6 个特性：

1. **MCP 调用** — 支持多个 MCP（Model Context Protocol）服务器，可注册、启用 / 停用、卸载
   - HTTP/SSE 传输（`HTTPMCPClient`）
   - WebSocket 传输（`WebSocketMCPClient`，内置最小 RFC 6455 客户端，无需第三方库）
2. **流式响应** — OpenAI 兼容 SSE 与 Anthropic Messages SSE 解析，
   边接收边渲染 (`provider/OpenAIProvider`, `provider/AnthropicProvider`, `util/SSEStreamReader`)
3. **非 WebView Markdown 渲染** — 自实现的解析器 + `Spannable` 渲染器，
   支持标题、段落、列表、任务列表、行内/块级代码、引用、分隔线、表格、链接、图片、
   粗体、斜体、删除线 (`markdown/MarkdownParser`, `markdown/MarkdownRenderer`)
4. **多 AI 提供商** — 可随时新增 / 切换 OpenAI 兼容、Anthropic、自定义端点
   (`provider/ProviderManager`, `ui/SettingsActivity`)
5. **配置存储管理** — 全部用 `SharedPreferences` + JSON 序列化持久化
   `ProviderConfig` / `MCPConfig` / `ToolConfig` / `Conversation` / 消息历史
   (`storage/ConfigManager`)
6. **工具调用（多工具，可卸载）** — 支持内置（计算器 / 时间 / 搜索 / 抓取）、
   MCP 桥接、自定义 HTTP 三类；可在工具管理页中启用 / 停用 / 卸载
   (`tools/ToolManager`, `ui/ToolMgmtActivity`)

## 在 AIDE 中打开

1. 把本目录放到设备本地（例如 `/sdcard/AIChatApp`）。
2. 打开 AIDE → **Open project** → 选中 `AIChatApp` 根目录（包含 `settings.gradle`）。
3. AIDE 会自动同步 Gradle 依赖。首次打开需要联网拉取 AndroidX / Material。
4. 在 AIDE 顶栏选择 **Run** 即可在设备 / 模拟器中编译并安装运行。

> 兼容性：Java 7 源代码（无 lambda / stream / method reference）；`minSdk = 21`，
> `compileSdk = 28`，`buildTools 28.0.3`。AIDE 自带的 Gradle 3.5.4 / AGP 3.5.4
> 兼容。

## 使用流程

1. **设置** → 添加 AI 提供商
   - OpenAI：`https://api.openai.com/v1` + `sk-...` + `gpt-4o-mini`
   - Anthropic：`https://api.anthropic.com` + `sk-ant-...` + `claude-3-5-sonnet-latest`
   - 自定义：任意 OpenAI 兼容端点（如 vLLM / Ollama / Azure OpenAI）
2. **MCP** → 注册 MCP 服务器（HTTP 或 ws://，随时可卸载）
3. **工具** → 选择需要的工具（内置工具默认已注册；可启用 / 停用 / 删除）
4. 返回首页 → 选提供商 + 模型 → 发起对话
   - 边输入边得到流式回答
   - 模型若请求工具调用，UI 会显示 "调用工具" 与结果，然后继续生成
   - 助手内容用自定义 Markdown 渲染（代码块、表格、列表、链接…）

## 目录结构

```
AIChatApp/
├── build.gradle
├── settings.gradle
├── gradle.properties
├── app/
│   ├── build.gradle
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/aichat/app/
│       │   ├── AIChatApplication.java
│       │   ├── chat/ChatManager.java          # 工具调用 + 流式响应编排
│       │   ├── mcp/
│       │   │   ├── MCPClient.java
│       │   │   ├── HTTPMCPClient.java
│       │   │   ├── WebSocketMCPClient.java    # 内置 RFC 6455 客户端
│       │   │   ├── MCPManager.java            # 注册 / 卸载
│       │   │   └── MCPTool.java
│       │   ├── markdown/
│       │   │   ├── MarkdownParser.java       # 块级 + 内联解析
│       │   │   ├── MarkdownRenderer.java     # Spannable 渲染（非 WebView）
│       │   │   └── MdNode.java
│       │   ├── model/
│       │   │   ├── ChatMessage.java
│       │   │   ├── Conversation.java
│       │   │   ├── MCPConfig.java
│       │   │   ├── MessageRole.java
│       │   │   ├── ProviderConfig.java
│       │   │   ├── ToolCall.java
│       │   │   └── ToolConfig.java
│       │   ├── provider/
│       │   │   ├── AIProvider.java           # 抽象接口
│       │   │   ├── OpenAIProvider.java       # OpenAI 兼容 + 流式 SSE
│       │   │   ├── AnthropicProvider.java    # Claude Messages + 流式 SSE
│       │   │   ├── ProviderManager.java
│       │   │   └── StreamCallback.java
│       │   ├── storage/ConfigManager.java    # 统一持久化
│       │   ├── tools/
│       │   │   ├── Tool.java
│       │   │   ├── ToolManager.java
│       │   │   ├── Args.java
│       │   │   ├── CalculatorTool.java
│       │   │   ├── DateTimeTool.java
│       │   │   ├── WebFetchTool.java
│       │   │   └── WebSearchTool.java
│       │   ├── ui/
│       │   │   ├── MainActivity.java         # 主页：对话列表
│       │   │   ├── ChatActivity.java         # 聊天页
│       │   │   ├── SettingsActivity.java     # 提供商
│       │   │   ├── MCPMgmtActivity.java      # MCP
│       │   │   └── ToolMgmtActivity.java     # 工具
│       │   └── util/
│       │       ├── HttpUtil.java             # HTTP + keep-stream
│       │       ├── Ids.java
│       │       ├── JsonUtil.java
│       │       └── SSEStreamReader.java      # SSE 行解析
│       └── res/...
```

## 关键实现要点

- **流式响应**：使用 `HttpURLConnection` 的输入流 + 自实现的 `SSEStreamReader`
  按行解析 `data: ...` 字段，遇到空行派发一次事件。每个文本 / 工具调用增量
  通过 `StreamCallback` 回调到 `ChatManager`，再到 `ChatActivity` 线程，
  写入 `MarkdownRenderer` 并 `notifyItemChanged` 增量更新。
- **MCP**：MCP 客户端是 JSON-RPC 2.0。HTTP 走 POST，WebSocket 走最小 RFC 6455
  文本帧协议。`MCPManager` 持有客户端缓存，可在删除 MCP 时 `disconnect`。
- **工具调用**：`ChatManager` 在收到 `tool_calls` 时调用 `ToolManager.execute`，
  把结果以 `role:"tool"` 注入回 `convo`，重新请求 LLM；最多循环 4 轮。
- **Markdown**：单文件解析 + 单文件渲染。块级解析处理标题 / 列表 / 任务列表 /
  引用 / 代码块 / 分隔线 / 表格；内联解析处理行内代码 / 粗体 / 斜体 / 删除线 /
  链接 / 图片 / 自动链接 / 硬换行。渲染端全部用 `SpannableStringBuilder` +
  各种 `Span`（`StyleSpan`、`BackgroundColorSpan`、`RelativeSizeSpan`、
  `TypefaceSpan`、`ClickableSpan` 等），不使用 WebView。
- **多提供商切换**：`ProviderManager` 根据 `ProviderConfig.type` 选择实现；
  切换是热切换——下一个请求即生效。
- **配置存储**：`ConfigManager` 用 `SharedPreferences` + JSON 数组保存所有
  列表数据；提供线程安全的 `synchronized` 读 / 写 API。

## 已知限制

- 工具调用实现只支持 OpenAI 风格 `function.name + arguments` JSON 字符串
  增量拼装，Anthropic 风格的 `input_json_delta` 也已实现并能正确执行。
- WebSocket MCP 客户端是**最小实现**，单连接、串行收发、无 ping/pong。
  适用于本地开发代理；生产 MCP 服务请用 OK Http 或更完整的实现替换。
- Markdown 解析偏向"LLM 友好子集"：不实现嵌套引用、嵌套列表的精确折叠，
  表格仅作网格字符输出。
- 凭据以明文保存到 SharedPreferences（Android 沙箱内）。如需更高安全性，
  自行接入 EncryptedSharedPreferences。
