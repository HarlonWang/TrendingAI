# AI Chat API · 设计文档（含 Server 端）

| 项 | 值 |
|----|----|
| 创建日期 | 2026-06-02 |
| 状态 | **草案** |
| 涉及仓库 | `github-ai-trending-api`（Cloudflare Worker + D1）、`TrendingAI`（客户端 chat SDK） |
| 触发动因 | 为 [AI Chat SDK](./2026-06-02-ai-chat-sdk-design.md) 接入真实后端：Worker 包装 ChatGPT，对外提供非流式聊天接口 |
| 关键依赖 | OpenAI Chat Completions（`gpt-5.4`）、Cloudflare D1、`install_id`（客户端已注入） |

---

## 1. 背景与现状

- 后端 `index.js` 按 `pathname` 分发，每接口一个 `handleXxx(request, env)`；POST 范式见 `api/feedback.js`（预检 → 校验 → 按 IP 经 D1 限流 → 写库）
- CORS 已全局 `*`，允许 GET/POST/OPTIONS（`lib/http.js`）
- **Worker 此前从未实时调过 OpenAI**：AI 摘要由爬虫（GitHub Actions）生成。chat 是 Worker 首个实时调 OpenAI 的接口
- 客户端 `install_id` 取自 `globalSettingsManager.getOrCreateInstallId()`（`:shared`）
- 客户端 chat SDK 已就绪，`ChatApi` 端点占位 `https://api.trendingai.cn/api/chat`

## 2. 目标

- 新增 `POST /api/chat`：入参完整历史 + 可选条目上下文，出参整段 Markdown（非流式）
- 后端无状态：不落库会话内容，只记限流计数
- 公开无鉴权接口 + 付费 OpenAI → 双层限流控成本（设备级配额 + 全局日预算）
- 技术情报助手人设，带上下文时基于条目作答

## 3. 非目标（YAGNI）

- ❌ 流式 SSE（与客户端一致，本期非流式）
- ❌ D1 落库会话内容（无状态）
- ❌ 多模型 / 用户鉴权 / token 级精确计费（按调用**次数**限流即可）
- ❌ 客户端会话持久化（沿用内存级单会话）

## 4. 关键决策

| 决策 | 选择 | 备选 | 原因 |
|------|------|------|------|
| 防滥用 | 设备级配额 + 全局日预算（D1 计数） | 仅 IP / 仅 install_id | IP 易变；双层兜底最稳 |
| 限流维度 | 按调用**次数** | 按 token 用量 | 简单够用，无需累计 usage |
| 模型 | `gpt-5.4`（同摘要） | mini / 可切换 | 配置统一、质量优先 |
| 人设 | 技术情报助手（话题聚焦技术） | 通用助手 | 贴合产品定位，间接抑制滥用 |
| 状态 | 后端无状态（客户端带全量历史） | D1 存会话 | 与"内存级单会话"一致，最简 |
| 限流标识 | `X-Install-Id` 请求头 | IP / 登录态 | 客户端已有，维度准 |
| 回复语言 | 客户端传 `lang`，prompt 按该语言书写 + 跟随用户 | 写死中文 / 纯跟随用户 | 写死中文会让英文用户收到中文回复；传 `lang` 默认确定 + 可自适应 |

## 5. 接口契约

`POST /api/chat`

**请求**
```
Header: X-Install-Id: <uuid>
Body:
{
  "messages": [ { "role": "user" | "assistant", "content": "..." } ],
  "lang":     "zh" | "en",                                              // 可选，默认 "zh"
  "context":  { "title": "...", "summary": "...", "sourceUrl": "..." }   // 可选
}
```

**响应**

| 状态码 | 含义 | 体 |
|--------|------|----|
| 200 | 成功 | `{ "content": "<markdown>" }` |
| 400 | 参数非法 / 缺 install_id | `{success:false,error}` |
| 429 | 超配额（设备日 或 全局日） | `{success:false,error}` |
| 502 | OpenAI 上游错误 / 超时 | `{success:false,error}` |

## 6. Server 端实现

**新增 / 改动**
- `src/api/chat.js` → `handleChat(request, env)`
- `src/index.js`：注册 `if (pathname === '/api/chat') return handleChat(request, env)`
- `migrations/012_add_chat_usage.sql`

**`handleChat` 流程**
1. OPTIONS 预检；非 POST → 405
2. 取 `X-Install-Id`，缺失 → 400
3. 解析校验 body：`messages` 非空且**末条为 user**；单条 `content` ≤ 4000 字符；`lang` 取 `"en"` 否则一律按 `"zh"`
4. **限流（D1，UTC 当日）**
   - 设备日配额：`(install_id, day)` 的 `count` < `PER_DEVICE_DAILY = 5`
   - 全局日预算：当日 `SUM(count)` < `GLOBAL_DAILY = 100`
   - 任一超限 → 429（次日 UTC 0 点自然重置）
5. **裁剪历史**：保留最近 `MAX_TURNS = 12` 条，总字符预算 ~8000，超则丢最旧
6. 拼装 `messages = [system] + 裁剪后历史`
7. 调 OpenAI `POST /v1/chat/completions`
   - `model: "gpt-5.4"`、`max_tokens ≈ 1024`、`stream: false`
   - `Authorization: Bearer ${env.OPENAI_API_KEY}`
   - `AbortController` 超时 30s
   - 失败 / 超时 → 502
8. 成功后 **UPSERT 自增** `chat_usage` 计数 → 返回 `{ content }`

> 计数放在调用成功后自增：失败的请求不计入用户配额。设备配额与全局预算各一次 D1 读，写一次 UPSERT。

**system prompt（人设 B，按 `lang` 选用对应语言版本）**

`lang = "zh"`：
```
你是 TrendingAI 的技术情报助手，聚焦技术、开发、开源相关话题，使用 Markdown 排版。
默认用简洁中文回答；若用户明显使用其他语言，则跟随用户的语言。
当用户话题明显偏离技术时，礼貌地把话题引导回技术领域。
```

`lang = "en"`：
```
You are TrendingAI's tech-intelligence assistant, focused on technology, software
development, and open source. Format answers in Markdown. Reply in English by default;
if the user clearly writes in another language, follow the user's language.
If the topic drifts well off technology, politely steer it back.
```

> 关键：**system prompt 用 `lang` 对应语言书写**（英文 persona 让英文输出更自然），并都带"默认 {lang}、用户切换则跟随"的子句——兼顾确定性与自适应。

带 `context` 时按同语言追加（示例 zh）：
```
用户正在查看以下条目：
标题：{title}
摘要：{summary}
来源：{sourceUrl}
请优先基于该条目作答。
```
（`lang = "en"` 时用英文标签 Title/Summary/Source 追加同样内容。）

**D1 迁移 `012_add_chat_usage.sql`**
```sql
CREATE TABLE chat_usage (
    install_id TEXT NOT NULL,
    day        TEXT NOT NULL,            -- UTC 'YYYY-MM-DD'
    count      INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (install_id, day)
);
CREATE INDEX idx_chat_usage_day ON chat_usage(day);
```

**配置常量（`chat.js` 顶部，易调）**
```js
const PER_DEVICE_DAILY = 5;     // 单设备每日对话次数上限
const GLOBAL_DAILY     = 100;   // 全局每日对话次数上限（钱包总闸）
const MAX_TURNS        = 12;    // 发往 OpenAI 的最近消息条数
const MAX_CONTENT_LEN  = 4000;  // 单条消息字符上限
const MAX_TOKENS       = 1024;  // 回复 token 上限
```

**运维**
- `wrangler secret put OPENAI_API_KEY`（密钥进 Worker secret，不写入 `wrangler.toml`）
- 迁移：`npx wrangler d1 migrations apply trending --remote`

## 7. Client 端实现（chat SDK）

`androidLibrary/chat/.../engine/ChatApi.kt` 调整：
- 请求头加 `X-Install-Id`：`header("X-Install-Id", globalSettingsManager.getOrCreateInstallId())`
- body 加 `lang`：由 `AppLanguage` 解析——`CHINESE → "zh"`、`ENGLISH → "en"`、`FOLLOW_SYSTEM → ` 按当前系统 Locale（`zh*` → `"zh"`，否则 `"en"`）
- 识别 `429`：抛可区分的配额异常，UI 把该条助手消息显示为"今日额度已用完"（区别于通用错误重试）
- 端点保持 `https://api.trendingai.cn/api/chat`

> `MessageStatus` 可新增 `QUOTA_EXCEEDED`，或在 `ChatMessage` 附带错误文案，由 `MessageItem` 区分展示。

## 8. 数据流

```
客户端 ChatViewModel.send()
  → ChatApi POST /api/chat  (X-Install-Id + messages + context?)
    → Worker handleChat
        ├─ 校验 + 设备/全局限流（D1 chat_usage）
        ├─ 裁剪历史 + 拼 system prompt
        ├─ OpenAI /v1/chat/completions (gpt-5.4, 非流式)
        └─ UPSERT 计数 → { content }
      → 客户端渲染 Markdown / 429 显示额度用完
```

## 9. 测试与验证

- 本地：`wrangler dev` + curl 验证 200 / 400（缺 install_id）/ 429（超配额）/ 502（伪造 key）
- 限流：同一 install_id 连发 6 次，第 6 次应 429；全局计数累加正确
- 历史裁剪：超长历史只发最近 12 条
- 语言：`lang=en` 用英文提问应得英文回复；`lang=zh` 用英文提问应跟随用户切到英文（验证"默认 + 跟随"子句）
- 客户端：真机/模拟器走真实接口，验证 install_id 透传、`lang` 解析与 429 文案

## 10. 实施顺序

1. `migrations/012_add_chat_usage.sql` + 远程 apply
2. `wrangler secret put OPENAI_API_KEY`
3. `src/api/chat.js` + `index.js` 注册（`wrangler dev` 本地验证）
4. 部署 Worker
5. 客户端 `ChatApi` 加 `X-Install-Id` + 429 处理，真机联调
