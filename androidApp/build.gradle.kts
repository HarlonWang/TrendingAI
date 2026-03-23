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
        implementation(project(":androidLibrary:updater"))
        // 上架 Google Play 时：将上方改为 "apkImplementation"(project(":updater"))
        // 并在 play flavor source set 中接入 Play In-App Update API
        implementation(libs.androidx.activity.compose)
        implementation(libs.androidx.appcompat)
        implementation(libs.compose.uiToolingPreview)
    }
}

android {
    namespace = "whl.trending.ai"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "whl.trending.ai"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        
        // 动态版本号逻辑：优先从环境变量读取
        val ciBuildNumber = System.getenv("BUILD_NUMBER")?.toIntOrNull()
        versionCode = ciBuildNumber ?: 1
        
        val ciVersionName = System.getenv("VERSION_NAME")
        versionName = ciVersionName ?: "0.1.0-dev"

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

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = true

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
    implementation(libs.aptabase)
    implementation(libs.androidx.lifecycle.process)
    debugImplementation(libs.compose.uiTooling)
}
