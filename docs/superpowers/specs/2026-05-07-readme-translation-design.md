# README 翻译功能 · 设计文档

| 项 | 值 |
|----|----|
| 创建日期 | 2026-05-07 |
| 状态 | **进行中**（客户端章节讨论中） |
| 涉及仓库 | `TrendingAI`（客户端） + `github-ai-trending-api`（后端） |
| 触发动因 | 用户反馈"没有翻译功能" |

---

## 0. 下次继续提示

**接续指令：** 直接说"接着 `docs/superpowers/specs/2026-05-07-readme-translation-design.md` 继续讨论"，并指明从第几章。

**当前进度：**
- ✅ 第 1-4 章已定稿
- ⚠️ 第 5 章（客户端设计）有草案，但有若干 **TBD** 待定
- ⏳ 第 6 章（错误处理）、第 7 章（测试）未开始

**下次最先要解的 TBD（第 5 章末尾列表）：**
1. ViewModel 状态字段拆分粒度（拆 vs 合）
2. 翻译请求 HTTP 超时取值（60s? 90s? 自适应？）
3. 翻译按钮的 icon 选型与错误提示形式

---

## 1. 背景与目标

### 1.1 现状

- 客户端 GitHub 详情页通过 `/readme?owner=X&repo=Y` 拉 README
  - Cloudflare Worker 拉 raw markdown → marked 转 HTML → 返回
  - 客户端 WebView 渲染（见 `shared/.../ui/detail/ReadmeScreen.kt`）
  - **当前展示的是英文原文，无任何翻译**
- AI 摘要（Picks 解读卡片）已是中文（后端 `summary_lang=zh-CN`），不在本设计范围内
- HN/PH 详情页内容也是英文，**留作二期**，本设计需为其留好扩展接口

### 1.2 目标

- 在 GitHub README 详情页提供"一键翻译"能力
- 译文质量：忠实保留 markdown 结构、代码块、链接、命令、徽章
- 成本可控：只在用户主动点击时才调用 AI；译文写回 D1，所有用户共享
- 二期可扩展到 HN/PH 详情字段翻译，**接口/缓存/路由形态不再大改**

### 1.3 非目标

- 不自动翻译（必须用户主动）
- 不流式逐段渲染（首期采用整篇切换）
- 不做双语对照视图
- 不翻译外链 WebView 中的网页
- 不做译文 TTL 清理（YAGNI，等表 >50MB 再加）

---

## 2. 范围与核心决策摘要

| # | 决策 | 选择 |
|---|------|------|
| D1 | 翻译范围 | 仅 GitHub README 详情页（HN/PH 二期） |
| D2 | 触发方式 | 用户主动点击顶栏"译"按钮 |
| D3 | 翻译时机 | 按需 + 译文写回 D1（read-through cache） |
| D4 | UI 形态 | 整篇切换（原文 ⇄ 译文），方案 1 |
| D5 | API 形态 | 通用核心 + adapter 抽象 + 业务路由 |
| D6 | 缓存 | 单张通用 `translations` 表，按 `cache_key` 命名空间 |

---

## 3. UX 流程 ✅

### 3.1 状态机

```
[ReadmeScreen]
  顶栏：[← 返回]  [README 标题]   [译]  [↗ 在 GitHub 打开]
                                  ↑ 新增

  Original ──点"译"──→ Translating ──完成──→ Translated
                            │                      │
                            └── 失败 → SnackBar    ↓ 再次点击
                                                Original
```

### 3.2 时序与体验

- **首次点击**：图标变 loading 旋转；命中 D1 缓存 <500ms 返回，未命中 5-20s
- **完成**：WebView 整篇替换为译文 HTML，按钮态切到"激活"
- **再点击**：瞬间切回原文（前端持有两份 HTML）
- **退出再进**：重新请求；D1 命中通常秒回
- **失败**：保留原文展示，SnackBar 提示

### 3.3 默认决定（已确认）

- **代码块 / 链接 / 图片 / 徽章不翻译**，仅译自然语言
- **按钮位置**：顶栏右侧，`view_on_github` 按钮**左边**

---

## 4. 后端设计 ✅

### 4.1 抽象：通用翻译核心服务

新增模块 `src/translate/`，对外暴露：

```js
translate(contentRef, targetLang = 'zh-CN')
  → { success, format, payload, cached, translatedAt, error? }
```

`contentRef` 为内容引用对象，按 `kind` 区分：

```js
// 首期实现
{ kind: 'github_readme', owner, repo, branch? }

// 二期占位（不实现，仅设计预留）
{ kind: 'hn_item', externalId, fields: ['title', 'text'] }
{ kind: 'ph_item', externalId, fields: ['title', 'tagline', 'description'] }
```

返回 `format`：
- `'html'` — README 类，payload 为完整 HTML 字符串
- `'fields'` — Feed 类（**首期不实现，仅在文档中说明**），payload 为 `{ field: translatedText }` 映射

### 4.2 adapter 抽象（三钩子）

每种 `kind` 注册一个 adapter，实现：

```js
{
  // 缓存键生成（同步）
  cacheKey(ref) → string,

  // 取原文（远程拉 / 查 D1 contents 表）
  async fetchSource(ref, env)
    → { sourceText, sourceHash, meta },

  // 构造翻译 prompt
  buildPrompt(sourceText, ref) → string,

  // 译后处理（如 markdown → HTML、JSON 解析）
  async postProcess(translatedText, ref)
    → { format, payload }
}
```

新增内容源仅需新增 adapter，路由层与缓存层不动。

### 4.3 通用 D1 缓存表

新增 migration `migrations/009_add_translations.sql`：

```sql
CREATE TABLE IF NOT EXISTS translations (
    cache_key      TEXT NOT NULL,   -- 形如 "github_readme:facebook/react"
    source_hash    TEXT NOT NULL,   -- 原文 sha256[:16]
    target_lang    TEXT NOT NULL,
    format         TEXT NOT NULL,   -- 'html' | 'fields'
    payload        TEXT NOT NULL,   -- HTML 字符串 或 JSON
    translated_at  TEXT NOT NULL,   -- ISO8601
    PRIMARY KEY (cache_key, target_lang)
);

CREATE INDEX IF NOT EXISTS idx_translations_source
    ON translations (cache_key, source_hash);
```

UPSERT 语义：同 `(cache_key, target_lang)` 重复时按新 `source_hash` 覆盖，不保留历史译文。

### 4.4 业务路由

```
GET /api/readme/translate?owner=X&repo=Y[&branch=Z]
  → contentRef = { kind: 'github_readme', owner, repo, branch }
  → translate(contentRef)
  → 200 { success, owner, repo, branch, filename, html, cached, translatedAt }
  → 404 README 不存在
  → 413 markdown > 100KB
  → 502 AI 翻译失败
```

业务路由仅做"参数 → contentRef → translate() → 拼装业务响应"，**响应字段保持业务语义**（README 给 `html`，未来 Feed 给 `fields`），客户端不感知内部抽象。

### 4.5 github_readme adapter 实现

```js
export const githubReadmeAdapter = {
  cacheKey: ({ owner, repo, branch }) =>
    `github_readme:${owner}/${repo}${branch ? `@${branch}` : ''}`,

  async fetchSource(ref, env) {
    const { markdown, branch, filename } =
      await fetchRawReadme(ref.owner, ref.repo, ref.branch);
    if (markdown.length > 100 * 1024) {
      throw new TranslateError('SOURCE_TOO_LARGE', 413);
    }
    return {
      sourceText: markdown,
      sourceHash: sha256_16(markdown),
      meta: { branch, filename, owner: ref.owner, repo: ref.repo }
    };
  },

  buildPrompt(markdown) {
    return TRANSLATE_README_PROMPT + '\n\n原文：\n' + markdown;
  },

  async postProcess(translatedMarkdown, ref) {
    const html = renderMarkdownToHtml(translatedMarkdown, ref);
    return { format: 'html', payload: html };
  }
};
```

### 4.6 翻译提示词

```
你是一名中文技术文档翻译，将以下 GitHub README 的 Markdown 翻译成简体中文。

严格规则：
1. 仅翻译自然语言（段落、标题、列表项中的描述、表格中文字、引用块）
2. 保留以下原样不译：
   - 所有代码块（``` ... ``` 与 `inline code`）
   - 所有 URL、Markdown 链接的 href、图片 src
   - 命令行参数、文件名、API 名、变量名、shell 命令
   - 徽章 / Badge 行（含 shields.io、img.shields.io 的图片）
3. Markdown 结构（标题层级、列表、表格、链接语法）必须与原文一一对应
4. 输出仅为翻译后的 Markdown，不要包裹解释或前后缀
```

- 模型：ChatGPT (gpt-5.4)，沿用 `crawler/src/consts.js` 配置
- temperature：0.2

### 4.7 重构动作：抽取共享代码

把现有 `src/api/readme.js` 中的两段逻辑抽到 `src/lib/github_raw.js`：

- `fetchRawReadme(owner, repo, branch?)` → `{ markdown, branch, filename }`
- `renderMarkdownToHtml(markdown, { owner, repo, branch })` → HTML

`readme.js` 与 `github_readme` adapter **共用同一份**，避免双份 raw 抓取/渲染逻辑。

### 4.8 关键约束

- markdown 长度上限 **100KB**（超过 → 413）
- 不流式（整篇翻译，整篇返回）
- AI 调用走 Worker → ChatGPT，假定单次调用 < 30s（Paid plan CPU 上限内）

---

## 5. 客户端设计 ⚠️（讨论中）

### 5.1 数据模型 · 草案

`shared/.../data/model/` 下新增：

```kotlin
@Serializable
data class ReadmeTranslationResponse(
    val success: Boolean = false,
    val owner: String = "",
    val repo: String = "",
    val branch: String = "",
    val filename: String = "",
    val html: String = "",
    val cached: Boolean = false,
    val translatedAt: String? = null,
    val error: String? = null
)
```

### 5.2 网络层 · 草案

- `TrendingApi.kt`：新增 `getReadmeTranslation(owner, repo, branch?: String? = null)`，路径 `/api/readme/translate`
- 复用现有 Ktor client；**翻译请求 timeout 需独立调高**（具体值 → TBD #2）
- `TrendingRepository.kt`：透传一层 `getReadmeTranslation()`

### 5.3 ViewModel 状态机 · 草案

```kotlin
data class ReadmeUiState(
    val originalHtml: String = "",          // 原 html 字段重命名
    val translatedHtml: String? = null,     // null 表示未请求过
    val displayMode: DisplayMode = DisplayMode.Original,
    val isLoading: Boolean = true,          // 加载原文
    val isTranslating: Boolean = false,     // 翻译中
    val error: String? = null,              // 加载原文错误
    val translateError: String? = null      // 翻译错误（独立字段）
)

enum class DisplayMode { Original, Translated }
```

`ReadmeViewModel` 新增 `toggleTranslation()` 与 `fetchTranslation()`：
- 已有译文 → 瞬时切换 mode
- 没有译文 → 请求 `/api/readme/translate` → 写入 state
- 切回原文 → 仅改 mode，不重新请求
- 翻译失败 → 写 `translateError`，**不影响 `originalHtml` 显示**

**译文与原文都驻留 ViewModel 内存；退出 ReadmeScreen 即丢弃，下次依赖 D1 缓存兜底。**

### 5.4 UI · 草案

- TopAppBar 在 `view_on_github` **左边**插入翻译 IconButton
- IconButton 三态：未激活描边图标 / 翻译中 CircularProgressIndicator / 已激活实心图标
- WebView 内容根据 `displayMode` 选择渲染 `originalHtml` 或 `translatedHtml`
- 翻译失败用 `SnackbarHost` 提示，**不替换 WebView 内容**

### 5.5 i18n 字符串

新增（中/英）：
- `translate_readme` = "翻译为中文" / "Translate to Chinese"
- `translate_show_original` = "显示原文" / "Show original"
- `translate_failed` = "翻译失败，请稍后重试" / "Translation failed, please try again"
- `translate_too_large` = "README 过长，无法翻译" / "README is too long to translate"

### 5.6 埋点

新增事件：
- `readme_translate_click`（payload: `owner`, `repo`, `direction=to_zh|to_original`）
- `readme_translate_success`（payload: `cached`, `duration_ms`）
- `readme_translate_failure`（payload: `error_code`）

### 5.7 TBD（下次先解决这些）

| # | 待定项 | 候选 / 倾向 |
|---|--------|-------------|
| 1 | ViewModel 状态字段拆分粒度 | A. 当前草案：`isLoading` / `isTranslating`、`error` / `translateError` 全拆 ← 倾向<br>B. 合并为枚举 `LoadState { Idle, LoadingOriginal, LoadingTranslation, Error }` |
| 2 | 翻译请求 timeout 值 | A. 60s 固定<br>B. 90s 固定<br>C. 与 markdown 长度成正比<br>D. 服务端给 `Retry-After` 后客户端走轮询 |
| 3 | 翻译按钮 icon 选型 | `Icons.Filled.Translate` vs `Icons.Outlined.Translate` 的搭配；要不要文字标签 |
| 4 | 错误提示形式 | SnackBar / Dialog / 顶部 Banner；区分 413（README 过长）vs 502（AI 失败）vs 网络异常 的文案 |

---

## 6. 错误处理与边界 ⏳

**未开始。** 下次至少需要覆盖：

- 后端层：404 / 413 / 502 / 502 重试策略；AI 偶发返回非预期格式（包了 ```markdown ``` 围栏）的兜底
- 客户端层：超时、网络断开、Worker 5xx；切回原文时是否应清掉 `translateError`
- 缓存层：D1 写入失败是否阻塞响应；并发同 `cache_key` 请求是否做去重

---

## 7. 测试方案 ⏳

**未开始。** 下次需要确定：

- 后端：adapter 单测、translate() 缓存命中/未命中分支、长度上限、提示词稳定性回归
- 客户端：ViewModel 状态机单测、显示模式切换、错误态恢复
- 端到端：用一个 README 长度典型样本（含代码块、表格、徽章）跑一次完整链路并人审译文质量

---

## 附录 A · 已澄清的关键问题

1. **"没有翻译"的范围** → 用户指向 GitHub README（不是列表页 / 外链 WebView）
2. **是否默认翻译** → 否，用户主动触发
3. **`'fields'` format 的含义** → 二期 HN/PH 用的字段映射，本期不实现，仅 adapter 接口预留

## 附录 B · 已读取的关键源文件

- `TrendingAI/shared/.../data/model/ReadmeResponse.kt`
- `TrendingAI/shared/.../ui/detail/ReadmeScreen.kt`
- `TrendingAI/shared/.../ui/detail/ReadmeViewModel.kt`
- `github-ai-trending-api/src/api/readme.js`
- `github-ai-trending-api/migrations/`（最新到 008）
