package whl.trending.chat.store

import androidx.compose.runtime.Composable

/** 平台默认持久化 Store（DB 单例 + 图片目录）；每次组合返回同一实例。 */
@Composable
internal expect fun rememberDefaultChatStore(): ChatStore
