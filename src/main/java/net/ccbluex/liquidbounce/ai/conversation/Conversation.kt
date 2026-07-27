// skid AIRI
/*
 * Air Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 */
package net.ccbluex.liquidbounce.ai.conversation

import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 一个完整的对话会话
 */
data class Conversation(
    val id: String = UUID.randomUUID().toString(),
    var title: String = "New Chat",
    val messages: MutableList<Message> = CopyOnWriteArrayList(),
    val createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis(),
    /** 对话专属 system prompt(覆盖全局模式 prompt) */
    var systemPrompt: String = "",
    /** 对话模式:chat | script | roleplay(null = 跟随全局) */
    var mode: String? = null
) {
    /** 转换为 API 请求所需的消息列表(过滤错误消息) */
    fun toChatMessages(): List<net.ccbluex.liquidbounce.ai.api.ChatMessage> =
        messages.filter { it.error == null && it.content.isNotBlank() }
            .map { it.toChatMessage() }

    /** 最后一条消息 */
    fun lastMessage(): Message? = messages.lastOrNull()

    /** 总 tokens 消耗 */
    fun totalTokens(): Int = messages.sumOf { it.tokens }

    /** 触发更新时间戳 */
    fun touch() { updatedAt = System.currentTimeMillis() }
}
