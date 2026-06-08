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

    // apk:    独立分发（GitHub Release + R2），包含自建 updater
    // play:   Google Play 渠道，不含 updater（Play 自管更新）
    // fdroid: F-Droid 渠道，不含 updater（F-Droid 客户端统一管理更新）
    flavorDimensions += "distribution"
    productFlavors {
        create("apk") {
            dimension = "distribution"
        }
        create("play") {
            dimension = "distribution"
        }
        create("fdroid") {
            dimension = "distribution"
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true

            val releaseConfig = signingConfigs.getByName("release")
            signingConfig = if (releaseConfig.storeFile?.exists() == true) {
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
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    "apkImplementation"(project(":androidLibrary:updater"))
    implementation(project(":androidLibrary:chat"))
    implementation(libs.aptabase)
    implementation(libs.androidx.lifecycle.process)
    debugImplementation(libs.compose.uiTooling)
}
