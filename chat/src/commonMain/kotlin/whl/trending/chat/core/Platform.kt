package whl.trending.chat.core

import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
internal fun epochMillis(): Long = Clock.System.now().toEpochMilliseconds()

/** 警告级日志（Android=Logcat，iOS=标准输出）。 */
internal expect fun logWarn(tag: String, message: String, error: Throwable? = null)
