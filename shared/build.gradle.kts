import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    kotlin("plugin.serialization") version libs.versions.kotlin.get()
}

kotlin {
    android {
        namespace = "whl.trending.ai.shared"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_17
        }
        androidResources {
            enable = true
        }
        withHostTest {
            isIncludeAndroidResources = true
        }
    }
    
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "shared"
            isStatic = true
        }
    }
    
    sourceSets {
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
            implementation(libs.androidx.browser)
        }
        commonMain.dependencies {
            // api 而非 implementation：ChatContext / ChatScreen 直接出现在 shared 的
            // public API（globalChatScreen、各入口页），消费者要在编译期看得见
            api(project(":chat"))

            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.androidx.graphics.shapes)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.compose.icons.core)
            implementation(libs.compose.icons.extended)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.contentNegotiation)
            implementation(libs.ktor.client.auth)
            implementation(libs.ktor.serialization.kotlinxJson)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
            implementation(libs.jetbrains.navigation3.ui)
            implementation(libs.multiplatform.settings)
            implementation(libs.multiplatform.settings.coroutines)
            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor)
            implementation(libs.material.kolor)
            implementation(libs.kmp.webview)
            // api 而非 implementation：shared 的 public API 直接暴露了库的类型
            // （LoginbaseAuthManager.client、globalOAuthLauncher、initLoginbaseAuth），
            // 消费者必须在编译期看得见它们。眼下 androidApp 是从 loginbase-kt-browser
            // 的 api(library) 间接拿到的，哪天那条依赖动了就会编译不过
            api(libs.loginbase.kt)
            // api 而非 implementation：AppEvent 继承库里的 Event，notifier 模块看得见才编得过
            api(libs.eventbase.kt)
            implementation(libs.jetbrains.navigationevent.compose)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
            // GitHub 授权的 iOS 承载（ASWebAuthenticationSession）。Android 侧那个同名 artifact
            // 因 manifest 合并只能由 androidApp 依赖，iOS 没有 manifest，直接在这里接
            implementation(libs.loginbase.kt.browser)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.multiplatform.settings.test)
            implementation(libs.ktor.client.mock)
        }
    }
}
