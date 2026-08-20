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

// 本地源码 ↔ Maven 坐标双轨：在 local.properties 里写 `<库>.dir=<路径>`（绝对路径或相对工程根目录）
// 即启用 composite build，本地构建直接吃那个库的源码——库与 App 一起迭代时不必发版就能验证，
// 这是本机开发的常态。不配也能构建，那时走 libs.versions.toml 里的 Maven 坐标。
//
// **CI 没有 local.properties，走的一律是 libs.versions.toml 里的 Maven 版本**（发布 CI 正是靠它
// 构建）。所以两条路构建的不是同一份代码，而且分岔不会有任何报错：在库里改了代码却没发版，本地
// 照常绿，CI 仍用旧版本，问题要到打 tag 那天才炸出来。
//
// 纪律：**改了库就发版，并同步 bump libs.versions.toml 的版本号**——那里的版本才是 CI 的唯一真相。
// 下面的 logger.lifecycle 会打出当前走的是哪条路，但只在配置阶段真正执行时（首次构建、改过 gradle
// 脚本、配置缓存失效）——命中配置缓存时 settings 脚本整个不重跑，日志也就不出现，别把「这次没打印」
// 当成「没走本地源码」。
//
// 「Maven 坐标 → included build 里的项目路径」这层映射由**库自己**声明在 `gradle/composite-substitutions`
// （坐标与 Gradle 项目名天然对不上：artifactId 是 wang.harlon:eventbase-kt，项目名却是 :library），
// 所以这里不出现任何库名，新增一个库只需往 local.properties 加一行。
val localProperties = java.util.Properties().apply {
    val file = rootDir.resolve("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun localSourceDir(key: String): File? {
    val configured = localProperties.getProperty(key)
    if (configured.isNullOrBlank()) return null
    val dir = File(configured).takeIf { it.isAbsolute } ?: rootDir.resolve(configured)
    require(dir.exists()) { "local.properties 配置的 $key 不存在: $dir" }
    // 让「我这次吃的是本地源码」这件事每次构建都可见，而不是要去翻 local.properties 才知道
    logger.lifecycle("$key: 本地源码 $dir（CI 用 libs.versions.toml 的 Maven 版本；改库不发版，CI 不会跟着变）")
    return dir
}

// 映射文件缺失一律报错：Gradle 的自动替换在坐标对不上时不报错、静默退回 Maven 版本（实测），
// 那种「以为在吃本地源码、其实没有」的错觉比构建失败危险得多。
fun compositeSubstitutions(dir: File): Map<String, String> {
    val file = dir.resolve("gradle/composite-substitutions")
    require(file.exists()) {
        "$dir 缺少 gradle/composite-substitutions（每行 `<group>:<artifactId> = <项目路径>`）；" +
            "没有它 Gradle 会静默退回 Maven 版本，本地源码等于没接上"
    }
    return file.readLines()
        .map { it.substringBefore('#').trim() }
        .filter { it.isNotEmpty() }
        .associate { line ->
            val parts = line.split('=', limit = 2).map(String::trim)
            require(parts.size == 2 && parts.none(String::isEmpty)) { "$file 这行不是 `坐标 = 项目路径`: $line" }
            parts[0] to parts[1]
        }
        .also { require(it.isNotEmpty()) { "$file 是空的" } }
}

// local.properties 里这几个 .dir 是 Android SDK 的，不是库
val toolchainDirKeys = setOf("sdk.dir", "ndk.dir", "cmake.dir")

localProperties.stringPropertyNames()
    .filter { it.endsWith(".dir") && it !in toolchainDirKeys }
    .sorted()
    .forEach { key ->
        val dir = localSourceDir(key) ?: return@forEach
        includeBuild(dir) {
            dependencySubstitution {
                compositeSubstitutions(dir).forEach { (coordinate, projectPath) ->
                    substitute(module(coordinate)).using(project(projectPath))
                }
            }
        }
    }
