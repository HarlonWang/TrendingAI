rootProject.name = "TrendingAI"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        maven { url = uri("https://www.jitpack.io") }
    }
}

include(":shared")
include(":androidApp")
include(":androidLibrary:updater")
include(":androidLibrary:chat")
include(":androidLibrary:notifier")

// loginbase-kt（登录底座的 KMP 客户端）：**本地源码与 Maven 坐标双轨**。
//
// 在 local.properties 里写 `loginbase-kt.dir=../../loginbase-kt`（绝对路径或相对工程根目录）
// 即启用 composite build，本地构建直接吃本地源码——库与 App 一起迭代时不必发版就能验证，
// 这是本机开发的常态。不配也能构建，那时走 Maven 坐标。
//
// **CI 没有 local.properties，走的一律是 libs.versions.toml 里的 Maven 版本**（0.1.0 起已
// 发布 Maven Central，发版 CI 正是靠它构建）。所以两条路构建的不是同一份代码，而且分岔
// 不会有任何报错：在 loginbase-kt 里改了代码却没发版，本地照常绿，CI 仍用旧版本，问题
// 要到打 tag 那天才炸出来。
//
// 纪律：**改了 loginbase-kt 就发版，并同步 bump libs.versions.toml 的版本号**——那里的
// 版本才是 CI 的唯一真相。下面的 logger.lifecycle 会打出当前走的是哪条路，但只在配置阶段
// 真正执行时（首次构建、改过 gradle 脚本、配置缓存失效）——命中配置缓存时 settings 脚本
// 整个不重跑，日志也就不出现，别把「这次没打印」当成「没走本地源码」。
val localProperties = java.util.Properties().apply {
    val file = rootDir.resolve("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val loginbaseKtDir: String? = localProperties.getProperty("loginbase-kt.dir")
if (!loginbaseKtDir.isNullOrBlank()) {
    val dir = File(loginbaseKtDir).takeIf { it.isAbsolute } ?: rootDir.resolve(loginbaseKtDir)
    require(dir.exists()) { "local.properties 配置的 loginbase-kt.dir 不存在: $dir" }
    includeBuild(dir) {
        dependencySubstitution {
            substitute(module("wang.harlon:loginbase-kt")).using(project(":library"))
            substitute(module("wang.harlon:loginbase-kt-browser")).using(project(":library-browser"))
        }
    }
    // 让「我这次吃的是本地源码」这件事每次构建都可见，而不是要去翻 local.properties 才知道
    logger.lifecycle(
        "loginbase-kt: 本地源码 $dir（CI 用 libs.versions.toml 的 Maven 版本；改库不发版，CI 不会跟着变）"
    )
}
