package whl.trending.ai.tinyui

import okio.Path
import okio.Path.Companion.toOkioPath
import whl.trending.ai.core.platform.AndroidContextHolder

internal actual fun tinyuiUpdatesDir(): Path =
    checkNotNull(AndroidContextHolder.get()) { "AndroidContextHolder not initialized" }.filesDir.resolve("tinyui").toOkioPath()
