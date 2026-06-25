# BYOK 自定义 AI 模型 — 设计文档

## 背景与定位

应用现有的聊天功能在 `androidLibrary/chat` 模块（纯 Android：OkHttp + ViewModel + Compose）。
当前正式引擎 `ChatApi` 走自有后端 `POST api.trendingai.cn/api/chat` → 后端转 ChatGPT（gpt-5.4），
**非流式**，按 `X-Install-Id` 限流。

本功能给该模块新增一个 **BYOK（Bring Your Own Key）直连引擎**：用户填入自己的 API Key、Base URL、模型，
应用直接请求其厂商，与现有"走后端共享"引擎并存。

### 决策汇总

| 维度 | 决策 |
|---|---|
| 请求路径 | App 直连厂商（不经自有后端） |
| 协议 | OpenAI 兼容 + Anthropic 原生，两套适配 |
| 流式 | SSE 流式（打字机效果） |
| Key 存储 | 加密（EncryptedSharedPreferences / Android Keystore） |
| 配置数量 | 单套活动配置 + "启用自己的模型"开关 |
| 模型选择 | 调 `/models` 拉取下拉列表（顺带充当连接测试） |
| 平台 | 仅 Android（chat 模块本就纯 Android），iOS 不在本次范围 |

---

## 1. 引擎层改造

### 1.1 接口改为流式

`ChatEngine.send` 从返回整段 `String` 改为返回增量 `Flow<String>`：

```kotlin
interface ChatEngine {
    fun send(history: List<ChatMessage>, context: ChatContext?): Flow<String>  // 增量 delta
}
```

理由：流式引擎逐字 emit；现有后端 `ChatApi` 拿到整段后一次 `emit(whole)` 即可——
非流式天然是"一次发射的流"，UI 层统一按流处理，概念干净、改动集中。

### 1.2 新增 ByokChatEngine

`ByokChatEngine` 持有当前 BYOK 配置，按 provider 类型分派到两个适配器：

- **OpenAiAdapter**：`POST {baseUrl}/chat/completions`，头 `Authorization: Bearer <key>`，
  body `stream: true`；解析 SSE 的 `data: {...}`，取 `choices[].delta.content`，遇 `[DONE]` 收尾。
- **AnthropicAdapter**：`POST {baseUrl}/v1/messages`，头 `x-api-key` + `anthropic-version`；
  SSE 事件 `content_block_delta` 取 `delta.text`，`message_stop` 收尾。
  system/messages 结构与 OpenAI 不同，单独拼装请求体。
- 两者共用一个 SSE 读取工具：Ktor `bodyAsChannel()` 逐行读 `data:`，处理半行/粘包。

### 1.3 错误归类

复用现有 `ChatErrors`，**新增 `AUTH` 错误类别**（`ChatErrorCategory.AUTH`）：
401/403 归到 AUTH，给出明确文案"API Key 无效或无权限"——这是 BYOK 下最高频错误。
其余 429/5xx/网络/超时沿用现有分类。

---

## 2. 配置数据模型与存储

### 2.1 数据结构（单套活动配置）

```kotlin
enum class ByokProvider { OPENAI_COMPATIBLE, ANTHROPIC }

data class ByokConfig(
    val enabled: Boolean,        // “启用自己的模型”开关
    val provider: ByokProvider,
    val baseUrl: String,         // 形如 https://api.openai.com/v1
    val apiKey: String,          // 敏感，加密存
    val model: String,           // 从 /models 选中的模型 id
) {
    val isValid: Boolean get() = baseUrl.isNotBlank() && apiKey.isNotBlank() && model.isNotBlank()
}
```

### 2.2 存储分层（按敏感度分流）

- **apiKey 单独加密**：新建 `SecureKeyStore`，基于 Android `EncryptedSharedPreferences`
  （Keystore 托管的 MasterKey 加密），只存这一个字段。极薄接口 `get()/set(value)/clear()`，放 androidMain。
- **非敏感字段**（enabled / provider / baseUrl / model）继续用现有 `SettingsManager`，
  新增对应 key，与主题/语言一致，方便用 `getXxxFlow` 观测开关变化。

理由：`EncryptedSharedPreferences` 只装一个 key，逻辑简单、不污染 `SettingsManager`；
非敏感配置复用既有明文存储，省一套加密读写。

### 2.3 读取聚合与降级

- `SettingsManager` 新增 `byokConfig(): ByokConfig` 组装方法（apiKey 从 `SecureKeyStore` 取），
  引擎和 UI 统一通过它拿完整配置。
- **降级安全**：`enabled=true` 但配置不全（`isValid=false`）时视为未启用，回落后端共享引擎，
  不让聊天直接报错。

---

## 3. 设置 UI

### 3.1 入口

`SettingsScreen` 新增"自定义 AI 模型"条目，点进独立子页 `ByokSettingsScreen`。

### 3.2 表单字段（自上而下）

1. **启用开关**："使用我自己的模型"——关时下方表单灰掉但保留已填值。
2. **Provider 选择**：OpenAI 兼容 / Anthropic。切换带出对应 baseUrl 占位提示
   （OpenAI `https://api.openai.com/v1`，Anthropic `https://api.anthropic.com`）。
3. **Base URL**：预填默认值，可改（兼容 OpenRouter / Ollama / 自建网关）。
4. **API Key**：密码态（默认掩码 + 末 4 位可见的眼睛切换），`imeAction=Done`。
5. **模型**："拉取模型"按钮 + 下拉。

### 3.3 /models 拉取（顺带充当连接测试）

点"拉取模型"，用当前 baseUrl/key/provider 调：
- OpenAI 兼容：`GET {baseUrl}/models`，Bearer 鉴权，取 `data[].id`。
- Anthropic：`GET {baseUrl}/v1/models`，`x-api-key` + `anthropic-version` 头，取 `data[].id`。

三种结果：
- 成功 → 下拉填充模型列表，按钮区显示 ✓"连接成功，N 个模型"。
- 鉴权失败（401/403）→ 红字"API Key 无效或无权限"。
- 网络/超时/404 → 对应错误文案。

**降级**：部分网关不支持 `/models`。拉取失败时，下拉下方提供"手动输入模型名"入口，
保证填错 baseUrl 也能用。

### 3.4 UI 规范与保存

- 加载指示：拉取按钮内用 `LoadingIndicator(Modifier.size(24.dp), color = onPrimary)`
  （遵循项目规范，全 app 不用 `CircularProgressIndicator`）。
- 保存时机：非敏感项改动即写；apiKey 失焦/保存时写 `SecureKeyStore`。退出即生效。

---

## 4. 引擎接线与 ViewModel 流式改造

### 4.1 引擎工厂

`ChatScreen` 默认 `engine = ChatApi.shared` 改为进入聊天时按配置解析：

```kotlin
fun resolveChatEngine(): ChatEngine {
    val cfg = globalSettingsManager.byokConfig()
    return if (cfg.enabled && cfg.isValid) ByokChatEngine(cfg) else ChatApi.shared
}
```

在 `ChatScreen` 组合时解析一次（VM 按 sessionKey 创建时注入）。

**已知限制**：会话进行中切换开关不会即时换引擎，需退出重进。v1 接受，避免引入"运行中热切换引擎"的复杂度。

### 4.2 ViewModel 收流式

`ChatViewModel.request()` 由 `runCatching { engine.send(...) }` 拿整段改为：
1. 先插入一条空 assistant 占位消息（`isStreaming=true`）。
2. `engine.send(...).collect { delta -> 追加到该消息 content }`，逐字刷新 UI。
3. 正常结束 → 该消息标 `isStreaming=false`。
4. `catch ChatException` → 占位消息替换为带 `error` 的错误条（复用现有错误条 + 重试逻辑）。

配套：
- `ChatMessage` 新增 `isStreaming: Boolean = false`，`MessageItem` 据此在末尾显示光标/`TypingIndicator`。
- `retry` 逻辑不变（移除错误条重新 `request()`）。
- **取消**：发送中退出/停止时 `viewModelScope` 取消会断开 SSE collect，符合现有
  `CancellationException` 不吞约定。
- `FakeChatEngine` 同步改为 `flow { ... }`（整段或分片 emit 模拟打字），保证 demo 与单测可用。

---

## 5. 依赖与技术选型说明

### 5.1 新增依赖（统一走 version catalog `gradle/libs.versions.toml`）

| 坐标 | 用途 | catalog 现状 |
|---|---|---|
| `androidx.security:security-crypto` | `EncryptedSharedPreferences` 加密存 apiKey | 当前无，需新增 `[versions]`/`[libraries]` |
| SSE 流式读取 | 解析 `data:` 增量 | 现有 `ktor-client-okhttp` 已可用 `bodyAsChannel()` 手动逐行读；若用 Ktor SSE 插件则需补 `io.ktor:ktor-client-core` 的 SSE 能力。**倾向手动读 channel，不引新插件** |

遵守项目规范：新增依赖必须同步更新 `[versions]`、`[libraries]` 两处，禁止在 `build.gradle.kts` 硬编码坐标字符串。

### 5.2 EncryptedSharedPreferences 状态说明

`androidx.security:security-crypto` 的 `EncryptedSharedPreferences` 已被 Google 标记 **deprecated**
（仍可用，且官方暂无直接替代）。可选替代是直接用 Android Keystore 手动加解密后存普通 SharedPreferences。

**本设计仍选用 `EncryptedSharedPreferences`**：简单、够用，apiKey 单字段场景手写 Keystore 加解密收益不大。
封装在 `SecureKeyStore` 薄接口后，未来若需替换实现，调用方零改动。

---

## 6. 测试

沿用模块现有 `kotlin.test` + JUnit，纯函数优先：

- **ChatErrors 扩展**：新增 `AUTH` 类别后，补 401/403 → AUTH 的归类用例（扩 `ChatErrorsTest`）。
- **SSE 解析器单测（重点）**：给 OpenAI / Anthropic 两套适配器各喂录制的 `data:` 流文本，
  断言增量序列正确、`[DONE]`/结束事件正确收尾、半行/粘包能处理。
- **配置选择逻辑**：`ByokConfig.isValid` + `resolveChatEngine`——齐全→ByokEngine、
  缺字段/未启用→回落后端。
- **/models 响应解析**：两套 provider 的列表 JSON → 模型 id 列表。
- 加密存储不写真机集成测试（依赖 Keystore），用接口 + fake 在逻辑层验证读写路径。

---

## 范围边界（YAGNI，明确不做）

- iOS（chat 模块本就纯 Android）。
- 多套配置切换、配置导入导出。
- 自定义 temperature / max_tokens / system prompt 等高级参数（v1 用合理默认）。
- 运行中热切换引擎（需退出重进聊天）。
- 本地用量 / 计费统计。
