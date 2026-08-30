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

    // markdown 解析的 iOS 侧绑 cmark-gfm（apple/swift-cmark 源，tag 与产物见 native/build-cmark.sh）
    listOf(
        iosArm64() to "ios_arm64",
        iosSimulatorArm64() to "ios_simulator_arm64",
    ).forEach { (target, libDir) ->
        target.compilations.getByName("main").cinterops.create("cmarkgfm") {
            definitionFile.set(project.file("native/cmarkgfm.def"))
            includeDirs(project.file("native/out/$libDir/include"))
            extraOpts("-libraryPath", project.file("native/out/$libDir").absolutePath)
        }
    }

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

            implementation(libs.room.runtime)
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
        }
        iosMain.dependencies {
            implementation(libs.sqlite.bundled)
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
    add("kspIosArm64", libs.room.compiler)
    add("kspIosSimulatorArm64", libs.room.compiler)
}

// cinterop 前先把 cmark-gfm 静态库编出来（已就绪则秒退，--force 见脚本）
val buildCmarkGfm by tasks.registering(Exec::class) {
    workingDir = projectDir
    commandLine("bash", "native/build-cmark.sh")
    inputs.file("native/build-cmark.sh")
    outputs.dir("native/out/ios_arm64")
    outputs.dir("native/out/ios_simulator_arm64")
}
tasks.matching { it.name.startsWith("cinteropCmarkgfm") }.configureEach {
    dependsOn(buildCmarkGfm)
}
