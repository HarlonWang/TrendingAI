# Trending AI 技术架构文档

本文档详细介绍了 Trending AI 的项目架构、设计模式及协作规范，旨在帮助开发者快速理解代码组织逻辑并保持高质量的代码贡献。

---

## 🏗️ 整体架构

项目采用 **分层架构 (Layered Architecture)**，结合 **单向数据流 (UDF)** 模式进行设计。整体代码主要集中在 `shared` 模块中，以实现 Android 和 iOS 的高度逻辑复用。

### 项目模块结构

```text
Trending/
├── androidApp/             # Android 应用入口
├── androidLibrary/         # Android 专属库模块（不含业务逻辑）
│   └── updater/            # 版本更新检测模块（:androidLibrary:updater）
├── shared/                 # KMP 共享模块（Android + iOS 复用）
└── iosApp/                 # iOS 应用入口
```

> **设计原则**：`shared` 模块承载与平台无关的业务逻辑；`androidLibrary` 下的模块仅包含 Android 平台专属能力，通过接口与 `shared` 解耦，不参与 iOS 构建。

### shared 模块内部分层

```text
shared/src/commonMain/kotlin/whl/trending/ai/
├── core/                   # 核心基础设施 (Infrastructure)
│   ├── platform/           # 跨平台适配 (expect/actual 机制)
│   ├── theme/              # 全局 UI 规范 (颜色、字体、形状)
│   └── App.kt              # 应用入口与全局 Compose 导航
├── data/                   # 数据层 (Data Layer)
│   ├── model/              # 数据实体类 (DTO, Entity)
│   ├── remote/             # 远程数据源 (Ktor API 请求实现)
│   ├── local/              # 本地持久化 (Settings / Preferences)
│   └── repository/         # 存储库 (业务逻辑入口，数据聚合)
├── ui/                     # 表现层 (Presentation Layer)
│   ├── main/               # 首页趋势列表模块 (Screen & ViewModel)
│   ├── settings/           # 设置与偏好模块
│   └── component/          # 可复用的公共 UI 组件
└── update/                 # 平台能力扩展接口
    └── UpdateChecker.kt    # 版本检测接口 + globalUpdateChecker 全局单例
```

---

## 📦 androidLibrary 模块规范

`androidLibrary/` 用于存放 Android 平台专属的独立功能库。每个子模块需满足：

- 职责单一，不依赖其他 `androidLibrary` 子模块
- 通过在 `shared/commonMain` 中定义接口与 `shared` 模块解耦
- `shared` 模块只感知接口，不感知具体实现

### updater 模块（:androidLibrary:updater）

负责 Android 端的版本更新检测与提示，不参与 iOS 构建。

**模块内部结构：**

```text
androidLibrary/updater/
└── src/main/kotlin/whl/trending/updater/
    ├── UpdateApi.kt            # 调用 GitHub Releases API 获取最新版本
    ├── UpdateInfo.kt           # 版本信息数据类
    ├── UpdateViewModel.kt      # 检测逻辑，实现 shared 中的 UpdateChecker 接口
    ├── UpdateDialog.kt         # 更新提示弹窗 Composable
    └── UpdateAwareContent.kt   # 入口 Composable，供 androidApp 的 MainActivity 调用
```

**与 shared 的协作方式（接口解耦）：**

```text
shared/commonMain
  └── UpdateChecker (interface)     # 定义 isChecking、isUpToDate、manualCheck()
      └── globalUpdateChecker       # 全局单例，默认 NoOpUpdateChecker

androidLibrary/updater
  └── UpdateViewModel               # 实现 UpdateChecker，注册到 globalUpdateChecker

shared/commonMain/ui/settings
  └── SettingsScreen                # 读取 globalUpdateChecker，无需感知 updater 模块
```

**更新检测触发时机：**

| 场景 | 行为 |
| :--- | :--- |
| Android 冷启动，距上次检测 > 24h | 自动请求 GitHub API，有新版本弹窗提示 |
| Android 冷启动，距上次检测 < 24h | 跳过，不请求接口（限流） |
| 用户在设置页点击「版本更新」 | 立即检测，无视 24h 限流 |
| iOS 用户点击「版本更新」 | 跳转官网（`globalUpdateChecker` 保持 NoOpUpdateChecker） |
| 网络请求失败 | 静默处理，不影响正常使用 |

**Google Play 扩展点：** 上架 Google Play 时，将 `androidApp/build.gradle.kts` 中的 `implementation` 改为按渠道的 flavor 依赖，并在 play flavor source set 中接入 Play In-App Update API，无需修改 `shared` 或 `updater` 模块内部逻辑。

---

## 🔄 数据流向 (Data Flow)

我们严格遵循 **UI -> ViewModel -> Repository -> DataSource** 的数据获取链路：

1.  **UI (Compose)**：仅负责展示状态和发送用户意图（Intent）。
2.  **ViewModel**：持有并管理 `UiState`。它通过协程调用 Repository，并根据返回结果使用 `.update { ... }` 更新状态。
3.  **Repository**：作为应用唯一的真相来源（Single Source of Truth）。它负责决定是从网络获取数据还是从本地缓存读取，并进行必要的数据转换。
4.  **DataSource (API/Local)**：最底层的具体实现，负责原始数据的 I/O 操作。

---

## 🛠️ 技术选型

-   **跨平台框架**: [Kotlin Multiplatform (KMP)](https://kotlinlang.org/docs/multiplatform.html)
-   **UI 框架**: [Compose Multiplatform](https://www.jetbrains.com/lp/compose-multiplatform/)
-   **网络请求**: [Ktor Client](https://ktor.io/) (支持基于平台特性的 Engine 切换)
-   **状态管理**: `androidx.lifecycle.ViewModel` + `kotlinx.coroutines.Flow`
-   **本地存储**: [Multiplatform Settings](https://github.com/russhwolf/multiplatform-settings)
-   **时间处理**: [Kotlinx Datetime](https://github.com/Kotlin/kotlinx-datetime)
-   **依赖注入**: 目前采用手动注入方式，保持轻量级。

---

## 📝 开发约定与规范

### 1. 状态管理
所有页面必须定义一个对应的 `data class UiState`，包含加载状态、错误信息及业务数据。
```kotlin
data class MainUiState(
    val items: List<Item> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)
```

### 2. 跨平台适配
-   非必要不使用 `expect/actual`。
-   优先考虑在 `commonMain` 中定义接口，并在平台模块中通过注入方式实现。
-   必须使用 `expect/actual` 时，将其统一放置在 `core/platform` 目录下。

### 3. UI 组件拆分
-   复杂的 Composable 页面（如 `MainScreen`）应按逻辑拆分为多个私有的子函数（如 `RepoList`, `RepoItem`）。
-   带有状态的组件应尽量设计为 **Stateless**（状态提升），将状态和事件回调交给父容器处理。

### 4. 协程使用
-   ViewModel 中的异步操作必须绑定在 `viewModelScope` 中。
-   Repository 中的挂起函数必须是线程安全的，不应依赖特定的 Dispatcher（内部应指定 `Dispatchers.Default` 或 `IO`）。

### 5. Commit 规范

- `feat`: 新功能
- `fix`: 修复 bug
- `docs`: 文档更新
- `style`: 格式（不影响代码运行的更改）
- `refactor`: 代码重构（既不是修复 bug 也不是添加功能）
- `test`: 添加缺失的测试或修正现有测试
- `chore`: 其他杂项更改（构建过程或辅助工具的变更）

---

## 🧪 测试策略

-   **单元测试**: 业务逻辑（尤其是 ViewModel 和数据解析）必须在 `commonTest` 中编写测试用例。
-   **UI 测试**: 关键路径的 UI 交互建议使用 Compose 提供的测试工具进行验证。
