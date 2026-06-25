package whl.trending.chat.engine.byok

import whl.trending.chat.engine.ChatApi
import whl.trending.chat.engine.ChatEngine

/**
 * 按当前 BYOK 配置选择聊天引擎：启用且配置完整→直连 [ByokChatEngine]，否则回落后端共享 [ChatApi]。
 * 在进入聊天时解析一次（详见设计文档：会话进行中切换需退出重进）。
 */
fun resolveChatEngine(config: ByokConfig = ByokConfigStore.current()): ChatEngine =
    if (config.enabled && config.isValid) ByokChatEngine(config) else ChatApi.shared
