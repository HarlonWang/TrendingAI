import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    target {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    dependencies {
        implementation(projects.shared)
        implementation(libs.androidx.activity.compose)
        implementation(libs.androidx.appcompat)
        implementation(libs.compose.uiToolingPreview)
    }
}

// 版本号策略：versionCode / versionName 统一从 git tag 推导，apk / play / fdroid 三渠道完全一致、
// 可复现——F-Droid buildserver 从 tag 源码自建，拿不到 CI 的 run_number，必须能从 tag 算出 versionCode。
// tag 来源：CI 注入的 VERSION_NAME（= 推送的 tag 名）优先；缺失时（F-Droid 自建 / 本地）从 git 读取。
// configuration-cache 安全：用 providers.exec 执行 git，由 Gradle 登记为缓存输入。
fun gitDescribe(vararg extra: String): String? =
    try {
        providers.exec {
            commandLine(listOf("git", "describe", "--tags") + extra.toList())
            isIgnoreExitValue = true
        }.standardOutput.asText.get().trim().ifEmpty { null }
    } catch (e: Exception) {
        null
    }

// 语义化版本 tag「MAJOR.MINOR.PATCH[-prerelease]」→ versionCode = MAJOR*10000 + MINOR*100 + PATCH。
// 每段预留 2 位十进制（MINOR / PATCH 取值 0–99）：0.14.0→1400，0.15.3→1503，1.0.0→10000。
// 用 matchEntire 锚定整串，并允许 -alpha/-beta/-rc 等预发布/构建后缀（与 CI 的 prerelease 发版一致，
// 预发布与正式版同 versionCode、不同渠道分发）；拒绝 release-1.2.3 / v1.2.3 / foo1.2.3bar 这类畸形 tag，
// 直接构建失败，避免旧的非锚定 find 从中截取子串、静默产出意外 versionCode。
// 无 tag（全新 clone / 本地无 CI）回落 1；护栏：minor/patch ≥ 100 抛错，避免进位串到相邻版本。
fun versionCodeFromTag(tag: String?): Int {
    val trimmed = tag?.trim().takeUnless { it.isNullOrEmpty() } ?: return 1
    val match = Regex("""(\d+)\.(\d+)\.(\d+)(?:[-+].*)?""").matchEntire(trimmed)
        ?: error("非法版本 tag，必须为 MAJOR.MINOR.PATCH[-后缀]，当前 tag=$tag")
    val (major, minor, patch) = match.destructured
    val mi = minor.toInt()
    val pa = patch.toInt()
    require(mi in 0..99 && pa in 0..99) {
        "versionCode 编码要求 minor/patch ≤ 99，当前 tag=$tag (minor=$mi, patch=$pa)，越界会与相邻版本串位"
    }
    return major.toInt() * 10000 + mi * 100 + pa
}

// tag 名：CI 的 VERSION_NAME 优先，否则从 git 取（--abbrev=0 在 exact tag 上即纯 tag 名）
// 用 providers.environmentVariable 而非 System.getenv，与 gitDescribe 的 providers.exec 一致，
// 让 env 被登记为 configuration cache 输入（env 变化能正确使缓存失效）
val ciVersionName: String? = providers.environmentVariable("VERSION_NAME").orNull?.takeIf { it.isNotBlank() }
val appVersionCode: Int = versionCodeFromTag(ciVersionName ?: gitDescribe("--abbrev=0"))
// versionName：CI / F-Droid 在 tag 上均为纯 tag 名；本地非 tag commit 回落带距离的 git describe
val appVersionName: String = ciVersionName ?: gitDescribe() ?: "0.1.0-dev"

android {
    namespace = "whl.trending.ai"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "whl.trending.ai"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        
        // 版本号见文件顶部 appVersionCode / appVersionName（CI env 优先，回落 git tag 推导）
        versionCode = appVersionCode
        versionName = appVersionName

        manifestPlaceholders["appName"] = "Trending AI"

        // loginbase 的 OAuth 回跳 scheme。中转页 intent-filter 与运行时 redirect 推导都由
        // loginbase-kt-browser 从这一个占位符取值（库内同源、不会漂移），App 不再写
        // intent-filter / BuildConfig。完整 redirect 为 `cn.trendingai:/loginbase/callback`
        // （Loginbase.redirectUri(context) 可查，debug 构建首次发起会打进日志）。
        //
        // scheme 取 **自有域名反写**（trendingai.cn → cn.trendingai）：RFC 8252 §7.1 对
        // private-use scheme 的要求是 MUST 基于自己控制的域名反写，`myapp` 这类通用名不合格。
        // 注意不能用 applicationId（whl.trending.ai 反写不对应任何真实域名，不合规）。
        //
        // **不带 host**（单斜杠）：private-use scheme 的 host 位没有所有权语义，纯粹是字符串，
        // RFC 的示例形态也是 `com.example.app:/oauth2redirect/...`。
        //
        // **debug 用独立 scheme 而非 path 区分**：Android 的 <data> 规则是「没有 host 时所有
        // path 属性都被忽略」，不带 host 就只剩 scheme 一个可过滤维度；且 scheme 是最粗、
        // 最不可能被厂商 ROM 改坏的那一层，两个变体装同一台机器物理隔离。见下方 debug buildType。
        //
        // 改动须与服务端 AUTH_DEEPLINKS 同步（github-ai-trending-api/wrangler.toml），
        // 对不上服务端直接 400 invalid_redirect，GitHub 登录整条不可用。
        manifestPlaceholders["loginbaseRedirectScheme"] = "cn.trendingai"
    }

    // 签名配置：从环境变量读取加密存储的密钥信息
    signingConfigs {
        create("release") {
            val keystorePath = System.getenv("ANDROID_KEYSTORE_PATH")
            if (!keystorePath.isNullOrEmpty()) {
                storeFile = file(keystorePath)
                storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ANDROID_KEY_ALIAS")
                keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
            }
        }
    }

    // 渠道埋点：每个 flavor 注入 BuildConfig.CHANNEL，Application 启动时写入 ChannelHolder，
    // 供 shared 的埋点配置（analyticsConfig）与请求 UA（getUserAgent）统一打标。
    buildFeatures {
        buildConfig = true
    }

    // github: GitHub Release 分发，含自建 updater，更新弹窗跳 GitHub Release（保持 channel 稳定）
    // r2:     官网/R2 分发，含自建 updater，更新弹窗跳官网（保持 channel 稳定）
    // play:   Google Play 渠道，不含 updater（Play 自管更新）
    // fdroid: F-Droid 渠道，不含 updater（F-Droid 客户端统一管理更新）
    // 四渠道仅 CHANNEL 不同，applicationId / versionCode / versionName / 签名完全一致，可互相覆盖升级。
    flavorDimensions += "distribution"
    productFlavors {
        create("github") {
            dimension = "distribution"
            buildConfigField("String", "CHANNEL", "\"github\"")
        }
        create("r2") {
            dimension = "distribution"
            buildConfigField("String", "CHANNEL", "\"r2\"")
        }
        create("play") {
            dimension = "distribution"
            buildConfigField("String", "CHANNEL", "\"play\"")
        }
        create("fdroid") {
            dimension = "distribution"
            buildConfigField("String", "CHANNEL", "\"fdroid\"")
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    // F-Droid 的 check apk 不接受 APK 内 AGP 的「Dependency metadata」签名块
    // （会报 extra signing block）；仅 APK 禁用，AAB 保留供 Google Play Console 依赖洞察
    dependenciesInfo {
        includeInApk = false
        includeInBundle = true
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true

            // 用 findByName 而非 getByName：F-Droid 构建会剥离 signingConfigs 的 release 定义，
            // 此时 findByName 返回 null（getByName 会抛 "SigningConfig 'release' not found" 致构建失败），
            // 回落到 debug 占位签名（F-Droid 之后用自己的 key 重签）；CI / 本地有 release 配置时正常用它签名。
            val releaseConfig = signingConfigs.findByName("release")
            signingConfig = if (releaseConfig?.storeFile?.exists() == true) {
                releaseConfig
            } else {
                signingConfigs.getByName("debug")
            }
            manifestPlaceholders["appName"] = "Trending AI"
        }
        getByName("debug") {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            manifestPlaceholders["appName"] = "Trending AI (D)"
            // 独立 scheme，与 release 物理隔离：装同一台设备时互不抢 deepLink。
            // 对应 debug.trendingai.cn 反写，同样是自有域名，仍合 RFC 8252 §7.1
            manifestPlaceholders["loginbaseRedirectScheme"] = "cn.trendingai.debug"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // updater 仅 github / r2 两个独立分发渠道需要（play/fdroid 由各自商店管更新）
    "githubImplementation"(project(":androidLibrary:updater"))
    "r2Implementation"(project(":androidLibrary:updater"))
    implementation(project(":chat"))
    // 每日 Picks 本地通知：纯 AlarmManager 方案，不依赖 GMS/FCM，四渠道（含 F-Droid）通用
    implementation(project(":androidLibrary:notifier"))
    implementation(libs.androidx.lifecycle.process)
    // 社交登录浏览器环节：只由本模块（持有 Activity 的那层）依赖，经
    // globalOAuthLauncher 注入给 shared——直接让 shared 依赖会把它的 manifest
    // 合并进所有中间模块的单测（placeholder 无值即构建失败）
    implementation(libs.loginbase.kt.browser)
    debugImplementation(libs.compose.uiTooling)
}
