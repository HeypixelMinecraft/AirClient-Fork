// skid AIRI
/*
 * Air Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 */
package net.ccbluex.liquidbounce.ai.conversation

import net.ccbluex.liquidbounce.ai.api.ChatMessage
import java.util.UUID

/**
 * 单条对话消息(持久化形式)
 *
 * 与 [ChatMessage] 的区别:[Message] 含元数据(timestamp/tokens/durationMs)用于 UI 展示;
 * [ChatMessage] 是 API 请求体,只有 role/content。
 */
data class Message(
    val id: String = UUID.randomUUID().toString(),
    val role: String,                  // user | assistant | system | tool
    var content: String,
    val timestamp: Long = System.currentTimeMillis(),
    /** 本次回复消耗的 tokens(仅 assistant 消息) */
    var tokens: Int = 0,
    /** 本次回复耗时毫秒(仅 assistant 消息) */
    var durationMs: Long = 0L,
    /** 错误信息(若该条为失败回复) */
    var error: String? = null,
    /** 是否为思考过程输出(reasoning 模型) */
    var isReasoning: Boolean = false
) {
    /** 转换为 API 请求体 */
    fun toChatMessage(): ChatMessage = ChatMessage(role = role, content = content)

    val isUser: Boolean get() = role == "user"
    val isAssistant: Boolean get() = role == "assistant"
    val isSystem: Boolean get() = role == "system"
}
