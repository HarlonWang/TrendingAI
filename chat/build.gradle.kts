import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
    kotlin("plugin.serialization") version libs.versions.kotlin.get()
}

kotlin {
    android {
        namespace = "whl.trending.chat"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_17
        }
        androidResources {
            enable = true
        }
        withHostTest {
            // Robolectric 内存 Room 库跑 DAO/迁移测试
            isIncludeAndroidResources = true
        }
    }

    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.icons.extended)
            implementation(libs.compose.components.resources)
            implementation(libs.androidx.lifecycle.viewmodelCompose)

            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.contentNegotiation)
            implementation(libs.ktor.serialization.kotlinxJson)
            implementation(libs.kotlinx.serialization.json)

            implementation(libs.coil.compose)
            implementation(libs.highlights)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)

            // Demo Activity 的 manifest 主题是 Theme.AppCompat.DayNight.NoActionBar。此前靠
            // Aptabase 的传递依赖白拿到 appcompat，它随自建埋点下线后要自己声明
            implementation(libs.androidx.appcompat)
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.exifinterface)
            implementation(libs.androidx.activity.compose)
            implementation(libs.compose.uiToolingPreview)

            implementation(libs.commonmark)
            implementation(libs.commonmark.ext.gfm.tables)

            implementation(libs.room.runtime)
            implementation(libs.room.ktx)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
        getByName("androidHostTest").dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlin.test.junit)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.robolectric)
            implementation(libs.androidx.test.core)
        }
    }
}

room {
    // schema 入库：迁移测试的权威参照（EchoFlow 同款实践）
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    add("kspAndroid", libs.room.compiler)
}
