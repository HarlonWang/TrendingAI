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

// loginbase-kt（登录底座的 KMP 客户端）：
// **当前尚未发布 Maven Central**，所以本地路径是必需配置而非可选——在 local.properties 里写：
//     loginbase-kt.dir=../../loginbase-kt
// 支持绝对路径或相对工程根目录的路径。首版发布后删掉该配置即切回 Maven 坐标
// （libs.versions.toml 里的 loginbase-kt 条目），业务代码与 catalog 都不用动。
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
}
