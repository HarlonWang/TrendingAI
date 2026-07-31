# chat 语音输入方案评估

> 状态：**评估稿**，未实现。结论是「先做方案 A，用埋点决定要不要再做方案 B」。

## 背景

chat 输入区（`androidLibrary/chat/.../ui/ChatInputBar.kt`）当前只有三个入口：`+` 菜单（联网搜索 / 深度调研 / 拍照 / 相册）、文本框、发送键。图片理解已打通「登录闸 + 服务端 403 真闸 + 埋点」这套范式，语音输入要接的是同一条管线。

先说一个必须摆在前面的事实：**用户今天已经能语音输入**——Gboard、搜狗、百度输入法都在键盘上自带麦克风，识别结果直接进 `OutlinedTextField`，我们零代码就享受了。所以这个需求的增量价值不是「从没有到有」，而是：

1. 入口显性化——输入法的麦克风按钮位置各家不一、部分被折叠进二级面板，很多用户不知道能用；
2. 结果可控——自建入口能拿到识别文本后做预处理（去掉尾随句号、自动 trim），并埋点观测使用率；
3. 为后续「语音对话」（边说边答 + TTS 朗读）留下第一块地基。

价值成立，但**不足以支撑一次重投入**。下面的方案排序按这个判断展开。

## 候选方案

### 方案 A：系统语音识别 Intent（`RecognizerIntent.ACTION_RECOGNIZE_SPEECH`）

拉起系统的语音输入弹窗，说完返回文本，填进输入框由用户确认后发送。

- **权限成本 0**：录音由识别应用完成，宿主 app **不需要 `RECORD_AUDIO`**。这一条对我们尤其重要——F-Droid 渠道页会逐条列权限，多一个麦克风权限对一个「看 GitHub Trending」的工具是明显的信任成本。
- **服务端成本 0、流量成本 0**：识别在设备侧或识别应用自己的通道完成，不经过 `api.trendingai.cn`，不占 chat 配额，因此**可以直接对匿名用户开放**，无需图片那套登录闸。
- **改动量**：`ChatInputBar` 加一个 `ActivityResultContract` + 麦克风按钮，约 50 行；文案 2 条；埋点 3 个。半天以内。
- **代价**：
  - UI 是系统弹窗，与 app 视觉不统一，做不到「边说边上屏」（拿不到 partial results）；
  - 依赖设备装有语音识别应用。无 GMS 的国行 ROM / 部分海外精简包可能没有，**必须做能力探测，探测不到就不渲染麦克风按钮**，否则点了没反应。Android 11+ 的包可见性限制下，探测前要在 manifest 用 `<queries>` 声明 **`android.speech.action.RECOGNIZE_SPEECH`**（本方案查的是能处理该 Intent 的 **Activity**；`android.speech.RecognitionService` 是给 `SpeechRecognizer` 查 Service 用的，声明错了 `resolveActivity` 恒为 null，会在有识别能力的机器上误判成「不支持」）；
  - 识别语言跟随 `EXTRA_LANGUAGE`，要按 app 语言设置（`globalSettingsManager.appLanguage`，与 `ChatApi.resolveLang()` 同口径）传 `zh-CN` / `en-US`，否则中英混说的识别率会掉；
  - **出错时我们插不上手**——识别在别人进程里，用户看到的是对方的错误文案，我们既拿不到原因也给不了引导。国产 ROM 上这不是理论风险，小米 13 实测要连闯三道关，详见下方〈真机验证〉一节。

### 方案 B：应用内录音 + 服务端转写

自绘「按住说话」UI，`MediaRecorder` 录 AAC/m4a，multipart 上传到后端新增的 `/api/transcribe`，服务端转 Whisper 类模型（后端已是 Cloudflare Worker + D1 那套，可用 Workers AI 的 whisper 同栈，或沿用现有 OpenAI 通道的 `gpt-4o-mini-transcribe`），回文本。

- **优点**：识别质量与一致性远好于设备侧，中英混说、技术术语（repo 名、库名）明显更准；不依赖设备有没有识别服务；UI 完全自控，能做波形动画、取消手势、重录。
- **代价（这是它落到第二位的原因）**：
  - 要 `RECORD_AUDIO` 运行时权限 + 权限拒绝/永久拒绝的引导分支；
  - 后端要新端点、新配额维度、新滥用防护（音频比文本贵且易被刷），还要过一遍隐私政策——「录音会上传到我们的服务器」必须明示；
  - 有真金白银的单位成本（按分钟计费），因此必须挂登录闸 + 配额，和图片理解同待遇；
  - 一个完整的录音状态机（空闲/录音中/上手指取消/上传中/失败重试/超时），加上 `LoadingIndicator` 的内嵌态、生命周期里释放 recorder，工程量是方案 A 的 5–8 倍，2–3 天起。

### 方案 C：应用内 `SpeechRecognizer` 直连（自绘 UI + 实时上屏）

介于 A、B 之间：用 `SpeechRecognizer` API 自绘 UI，靠 `onPartialResults` 做边说边上屏；API 31+ 还可走 `createOnDeviceSpeechRecognizer` 离线识别。

代价是**要 `RECORD_AUDIO`**（这次录音在我们进程里），却仍然继承方案 A 「设备没有识别服务就没得用」的缺陷——**付出了 B 的权限成本，只拿到 A 的能力上限**。除非明确要做实时上屏，否则不划算。不推荐。

### 方案 D：不做，保持现状

把入口交给输入法。零成本，但拿不到任何数据，也无法回答「用户到底会不会对着 app 说话」。

## 对比

| | A 系统 Intent | B 录音+服务端转写 | C 应用内 SpeechRecognizer | D 不做 |
|---|---|---|---|---|
| 麦克风权限 | 不需要 | 需要 | 需要 | — |
| 服务端改动 | 无 | 新端点+配额+防刷 | 无 | — |
| 单位成本 | 0 | 按分钟计费 | 0 | 0 |
| 识别质量 | 依设备，中等 | 高 | 依设备，中等 | 依输入法 |
| 无 GMS 设备 | 可能不可用 | 可用 | 可能不可用 | 依输入法 |
| 视觉一致性 | 系统弹窗 | 全自控 | 全自控 | — |
| 匿名可用 | 可以 | 应挂登录闸 | 可以 | — |
| 工作量 | ~0.5 天 | 2–3 天 + 后端 | 1.5 天 | 0 |

## 结论与推荐

**先做 A，把埋点做扎实，用两周数据决定要不要做 B。**

理由：语音输入的真实需求强度目前是**未经验证的假设**——输入法已经提供了替代路径，我们没有任何数据说明用户想在 chat 里说话。方案 A 是验证这个假设的最便宜方式（半天、零权限、零成本、零后端），而且它产出的埋点正好是方案 B 的立项依据。反过来先做 B，会在需求未证实的前提下同时背上麦克风权限、隐私政策、服务端成本和配额设计四笔债。

升级到 B 的触发条件建议写死成三条，命中任意两条再启动：

- `chat_voice_start` 的周触发用户数 / chat 周活用户数 ≥ 10%；
- 识别不可用率（`chat_voice_unsupported` + `chat_voice_fail`）≥ 15%，说明设备侧能力确实不够；
- 有明确的质量反馈（识别不准导致改写/放弃发送，可用「识别完成但未发送」的比例近似）。

## 方案 A 的落地要点

改动集中在 chat 模块，不碰 shared / ViewModel / engine / 服务端。

1. **`ChatInputBar` 加麦克风按钮**：位置放在文本框右侧、与发送键同一组——`input` 为空时显示麦克风，有内容时切回发送键（ChatGPT / 微信同款，避免底栏拥挤）。仅当能力探测通过时渲染。
2. **能力探测**：`Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).resolveActivity(packageManager) != null`，配合 chat 模块 manifest 新增 `<queries>` 声明 action `android.speech.action.RECOGNIZE_SPEECH`（**不是** `android.speech.RecognitionService`，那个查的是 Service，对本方案无效）。探测结果 `remember` 一次即可。
3. **结果回填**：拿 `EXTRA_RESULTS` 首项，`trim()` 后**追加**到 `input` 现有内容尾部（而不是覆盖），走 `viewModel.updateInput`。**不自动发送**——识别错了直接发出去比多点一下更伤。
4. **文案**：`chat_voice_input`（内容描述）+ 失败 Toast 一条，中英双语，落在 `androidLibrary/chat/src/main/res/values{,-zh}/strings.xml`。
5. **埋点**（对齐 `docs/analytics-notes.md` 的口径，事件名 `chat_` 前缀）：
   - `chat_voice_start`——点击麦克风；
   - `chat_voice_result`，带 `chars` 分桶——识别返回非空；
   - `chat_voice_cancel`——用户取消 / 返回空结果；
   - `chat_voice_unsupported`——探测失败（这条要**在按钮隐藏时也记一次**，否则永远看不到不可用设备的分母，只能记冷启动首次进 chat 时一次，避免刷量）。
6. **UI 规范**：本方案不涉及加载态，若后续加「识别中」提示，按 CLAUDE.md 一律用 `LoadingIndicator`，禁用 `CircularProgressIndicator`。

### 边界

- 识别应用被用户禁用 / `ActivityNotFoundException`：`runCatching` 包住 `launch`，失败弹 Toast，与相机入口现有写法一致。
- 识别返回空列表或空串：静默返回，不清空用户已输入的内容。
- 正在发送中（`isSending`）：麦克风按钮不禁用——用户完全可以在 AI 回复期间先把下一句说好。
- 无障碍：`contentDescription` 必填；系统弹窗本身已由平台处理无障碍。

### 验证

- 单测价值低（逻辑几乎全在 Activity result 回调里），不强求；
- 真机/模拟器验证走 `.claude/skills/verify` 那套：装 debug 包到 `Pixel_9_2`，进 chat 截图确认按钮渲染、点击拉起系统弹窗、模拟器无识别服务时确认按钮**不渲染**且不崩；
- 发版前照常跑 `scripts/release-smoke.sh`。

## 真机验证：小米 13 / Android 16（2026-07-31）

装 r2 debug 包到小米 13（`2211133C`，Android 16）实测，麦克风按钮正常渲染、识别可用，但**首次使用要连闯三道关**，每一道我们都插不上手：

1. **MIUI 跳转确认**——「Trending AI 想要打开 系统语音引擎，是否允许？」（`com.miui.securitycenter` 拦截）。选「本次允许」的话**每次点麦克风都会弹**。MIUI 行为，无法消除。
2. **识别应用的首启协议页**——小米「系统语音引擎」自己的用户协议 + 隐私政策，要点「同意」。
3. **识别应用的录音权限**——`com.xiaomi.mibrain.speech` 的 `RECORD_AUDIO`。

第 2 步没走完（用户误触退出）之后，问题就来了：**再点麦克风只弹一个「似乎出错了呢 (2)」，没有任何补救入口**。实测该包 `RECORD_AUDIO: granted=false`，而它**没有 launcher activity**（`cmd package resolve-activity` 返回 `No activity found`），用户在桌面和应用列表里根本找不到它，也就无从授权。测试中只能靠 `adb shell pm grant com.xiaomi.mibrain.speech android.permission.RECORD_AUDIO` 解开——普通用户没有这条路。

这是方案 A 的**结构性缺陷**：识别过程在别人进程里，出错时用户看到的是对方的文案，我们既拿不到原因，也给不了引导。

### 失败信号无法区分（问题的根）

`ACTION_RECOGNIZE_SPEECH` 的契约里，**用户主动取消**和**识别应用出错**回传的都是 `RESULT_CANCELED`。当前实现一律记 `chat_voice_cancel` 后静默返回，于是「用户改主意了」和「这台设备的语音输入彻底废了」在埋点里长得一模一样——而后者正是升级方案 B 的关键依据。

一个直觉的补救是**用耗时区分**（秒退即失败）。**实测证明它不成立**：小米的失败路径是弹一个 AlertDialog 等用户点「确定」，耗时取决于用户反应速度，2–5 秒都有可能，与正常说一句话完全重叠。这条路不要走。

### 可靠的判据：查目标包的录音权限

`PackageManager.checkPermission` 是公开 API，可以查**任意可见包**的权限授予状态。我们已经通过 `<queries>` 声明使 识别应用可见，`resolveActivity` 又能拿到它的 `packageName`，两者一凑就得到了一个确定性判据：

```kotlin
/** 识别应用包名 + 它是否已拿到录音权限；识别应用不存在时返回 null */
private fun recognizerState(context: Context): Pair<String, Boolean>? {
    val pm = context.packageManager
    val pkg = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        .resolveActivity(pm)?.packageName ?: return null
    val granted = pm.checkPermission(Manifest.permission.RECORD_AUDIO, pkg) ==
        PackageManager.PERMISSION_GRANTED
    return pkg to granted
}
```

**不要用它做前置拦截**——多数识别应用的录音权限是首次进入时自己运行时申请的，「未授权」是正常初始态，提前拦会把正常首启也堵死。正确用法是**回调时的事后判据**：

```
识别返回 null（RESULT_CANCELED）
  ├── 目标包已有 RECORD_AUDIO  → 用户主动取消，静默，记 chat_voice_cancel
  └── 目标包仍无 RECORD_AUDIO  → 授权链没走通，记 chat_voice_blocked 并给引导
```

今天这个 case 正好落在第二条分支上，且判据是确定性的、不依赖任何启发式。

### 引导 UI

沿用图片登录闸那套 `AlertDialog` 范式（`ChatInputBar` 现有写法）：

- **标题/正文**：说明是「设备的语音引擎缺少麦克风权限」，而不是笼统的「语音输入失败」——把责任指向正确的地方，用户才知道该去哪修；
- **主按钮「去设置」**：`Settings.ACTION_APPLICATION_DETAILS_SETTINGS` + `Uri.parse("package:$pkg")` 跳该包的应用详情页。**这条待真机验证**——小米语音引擎没有 launcher 入口，应用详情页能否正常打开尚未实测，打不开就退化为纯文案指路（设置 → 应用管理 → 显示所有应用 → 系统语音引擎 → 权限）；
- **次按钮「知道了」**：关闭；
- **正文附一句降级建议**：「也可以直接用键盘上的麦克风按钮」。输入法的语音走的是另一条通道，不受该应用权限影响——这是最实用的兜底，而且本来就是用户今天在用的路径；
- **频次控制**：同一会话最多弹一次（`rememberSaveable` 标记），之后失败只出 Toast，避免反复打扰。

### 埋点修正

原方案的 `chat_voice_unsupported` 只覆盖「设备没装识别应用」，**覆盖不到「装了但用不了」——而后者才是真机上真实发生的那种**。补一个事件，并把它计入不可用率：

| 事件 | 含义 |
| :--- | :--- |
| `chat_voice_cancel` | 返回 null 且目标包**有**录音权限 → 用户主动取消 |
| `chat_voice_blocked` | 返回 null 且目标包**无**录音权限 → 授权链断了，带 `pkg` 维度 |
| `chat_voice_unsupported` | 压根没有识别应用 |

**这两个指标不能相加**——`chat_voice_unsupported` 的设备上麦克风按钮根本不渲染，`chat_voice_start` 恒为 0，把它塞进以 start 为分母的比率里，等于给分子加了个分母里不存在的东西。必须拆成两个各自自洽的口径：

| 指标 | 公式 | 回答的问题 |
| :--- | :--- | :--- |
| 设备覆盖缺口 | `chat_voice_unsupported` 的 install 数 / chat 活跃 install 数 | 多少设备**连入口都没有** |
| 授权失败率 | `chat_voice_blocked` / `chat_voice_start` | 有入口的设备里，多少次**被权限挡住** |
| 完成率 | `chat_voice_result` / `chat_voice_start` | 点了麦克风的，多少次真的转出了文字 |

升级方案 B 的触发条件相应改为：**任一** 缺口 ≥ 15% 或授权失败率 ≥ 15%。两者指向同一个结论但成因不同——前者是设备没能力，后者是授权链太长，而方案 B（自己录音 + 服务端转写）**同时解决这两种**：它绕开第三方识别应用，三道关一道都不用闯。

**`chat_voice_blocked` 的 `pkg` 维度是这组数据里最值钱的一个**：失败若集中在某一两个厂商的识别应用上，那就不是零星个例而是国产 ROM 的系统性问题，方案 B 的必要性直接被证明。

⚠️ **语音输入没有服务端参与，因此没有 D1 兜底**。`chat_send` 还能靠 `chat_logs` 表对账（见 `docs/analytics-notes.md`），语音这套**只有 Aptabase 埋点一条路**——埋点漏报就等于这个功能的使用情况完全不可见。这是 chat 那次「埋点侧长期为零、只能查 D1」的教训在这里**没有退路**的版本，上线后要尽早确认事件真的有量。

### 实现与验证结果（小米 13 实测，2026-07-31）

失败分流 + 引导弹窗已实现（`ChatInputBar.kt`，含 `findRecognizer` / `hasRecordAudio`），四条路径逐一验证：

| 场景 | 复现方式 | 预期 | 结果 |
| :--- | :--- | :--- | :--- |
| 授权链断（用户早上遇到的） | `pm revoke com.xiaomi.mibrain.speech RECORD_AUDIO` | 弹引导，标题「语音引擎需要麦克风权限」，正文带应用名 | ✅ 应用名正确解析为「系统语音引擎」 |
| 引导跳设置 | 点弹窗「去设置」 | 落到该包应用信息页 | ✅ 落到「系统语音引擎」应用信息页，含「权限管理」入口 |
| 用户主动取消 | `pm grant` 复权限后进聆听界面按返回 | 静默，无弹窗无 Toast，输入框不变 | ✅ 界面干净 |
| 按钮互斥 | 输入框输入文字 | 麦克风切回发送键 | ✅ |

**`pm revoke` 是这套逻辑唯一可靠的复现手段**，它把设备精确退回「有识别应用但授权链断」的状态——这个状态无法靠 UI 操作制造（识别应用没有 launcher 入口，进不去它的设置）。回归验证时照抄这条命令即可，验完记得 `pm grant` 还原。

一个意外收获：验证截图里小米输入法的空格键上就有麦克风键，**「降级用键盘语音」这条兜底建议在同一屏内就能被用户执行**，不是纸上谈兵。

待验证项（本次未覆盖）：识别成功后的文本回填与追加逻辑需真人说话，已由使用者手动确认可用；非小米 ROM（GMS 设备走 Google 语音）的三道关表现未测。

## 明确不做

- **语音对话**（实时打断、TTS 朗读回复）：产品形态完全不同，成本和复杂度另一个量级，不在本次范围。
- **iOS**：chat 是 Android-only 模块（`globalChatScreen` 依赖反转，iOS 侧为 null），本方案不涉及 iOS。
