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

// 语义化版本 tag「MAJOR.MINOR.PATCH」→ versionCode = MAJOR*10000 + MINOR*100 + PATCH。
// 每段预留 2 位十进制（MINOR / PATCH 取值 0–99）：0.14.0→1400，0.15.3→1503，1.0.0→10000。
// 只要版本号按语义化递增，versionCode 即严格单调递增；无法解析时回落 1。
// 护栏：minor/patch ≥ 100 会进位串到相邻版本，此时直接抛错让构建失败，
// 而非静默产出与隔壁版本冲突的 versionCode（那在 Play 上是发版灾难）。
fun versionCodeFromTag(tag: String?): Int {
    val match = tag?.let { Regex("""(\d+)\.(\d+)\.(\d+)""").find(it) } ?: return 1
    val (major, minor, patch) = match.destructured
    val mi = minor.toInt()
    val pa = patch.toInt()
    require(mi in 0..99 && pa in 0..99) {
        "versionCode 编码要求 minor/patch ≤ 99，当前 tag=$tag (minor=$mi, patch=$pa)，越界会与相邻版本串位"
    }
    return major.toInt() * 10000 + mi * 100 + pa
}

// tag 名：CI 的 VERSION_NAME 优先，否则从 git 取（--abbrev=0 在 exact tag 上即纯 tag 名）
val ciVersionName: String? = System.getenv("VERSION_NAME")?.takeIf { it.isNotBlank() }
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
