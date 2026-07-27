// skid AIRI
/*
 * Air Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 */
package net.ccbluex.liquidbounce.ai.conversation

import net.ccbluex.liquidbounce.ai.api.AiriApiClient
import net.ccbluex.liquidbounce.ai.api.AiriSettings
import net.ccbluex.liquidbounce.ai.api.ChatCompletionRequest
import net.ccbluex.liquidbounce.ai.api.ChatMessage
import net.ccbluex.liquidbounce.ai.api.ToolCall
import net.ccbluex.liquidbounce.ai.script.ScriptBridge
import net.ccbluex.liquidbounce.utils.client.ClientUtils.LOGGER

/**
 * 工具调用循环处理器
 *
 * 在 script/chat 模式下,把 [ScriptBridge] 暴露的工具传给 AI,
 * 自动执行 AI 决定调用的工具,并把结果回传给 AI 直到给出最终文本回复。
 *
 * - 最多迭代 8 轮(防死循环)
 * - 中间消息 (assistant_with_tools / tool_result) 只存在于 workingMessages 中,不写入 conv.messages
 * - 最终只返回 AI 的自然语言回复,UI 保持干净
 * - onProgress 回调通知 UI 更新进度提示
 */
object ToolCallLoop {

    private const val MAX_ITERATIONS = 8

    /**
     * @param systemPrompt 系统提示
     * @param onProgress 进度回调,接收当前正在执行的工具名 (用于 UI 显示)
     * @return AI 最终的自然语言回复
     */
    suspend fun runWithTools(
        systemPrompt: String,
        onProgress: (String) -> Unit = {}
    ): String {
        val conv = ConversationManager.current
        val tools = ScriptBridge.toolDefinitions

        // workingMessages 维护完整上下文 (含工具调用历史),但不写入 conv.messages
        val workingMessages = mutableListOf<ChatMessage>()
        workingMessages.add(ChatMessage(role = "system", content = systemPrompt))
        conv.messages.forEach { m ->
            if (m.content.isNotBlank() && m.role != "system") {
                workingMessages.add(m.toChatMessage())
            }
        }

        var finalAssistantReply = ""
        var iterations = 0

        while (iterations++ < MAX_ITERATIONS) {
            val request = ChatCompletionRequest(
                model = AiriSettings.model,
                messages = workingMessages.toList(),
                temperature = AiriSettings.temperature,
                maxTokens = AiriSettings.maxTokens,
                stream = false,
                enableThinking = if (AiriSettings.thinkEnabled) true else null,
                thinkingBudget = if (AiriSettings.thinkEnabled) AiriSettings.thinkStrength.toDouble() else null,
                tools = tools,
                toolChoice = "auto"
            )

            val response = AiriApiClient.chat(request)
            if (response.error != null) {
                LOGGER.error("[Airi] Tool call loop error: ${response.error.message}")
                return "出错啦... ${response.error.message} (＞﹏＜)"
            }

            val choice = response.choices.firstOrNull() ?: break
            val msg = choice.message ?: break
            val toolCalls = msg.toolCalls

            if (!msg.content.isNullOrBlank()) {
                finalAssistantReply = msg.content
            }

            if (toolCalls.isNullOrEmpty()) {
                break
            }

            // 把带 tool_calls 的 assistant 消息加入 workingMessages (不写入 conv.messages)
            val assistantMsgWithTools = ChatMessage(
                role = "assistant",
                content = msg.content ?: "",
                toolCalls = toolCalls
            )
            workingMessages.add(assistantMsgWithTools)

            // 执行每个工具调用
            toolCalls.forEach { tc ->
                val toolName = tc.function.name
                val toolArgs = tc.function.arguments

                LOGGER.info("[Airi] Tool call: $toolName($toolArgs)")
                onProgress(toolName)

                val result = ScriptBridge.execute(toolName, toolArgs)
                val resultText = if (result.success) {
                    result.message + (result.data?.let { "\n" + it.toString().take(300) } ?: "")
                } else {
                    "Error: ${result.message}"
                }

                // 工具结果加入 workingMessages (不写入 conv.messages)
                val toolResultMsg = ChatMessage(
                    role = "tool",
                    content = resultText,
                    toolCallId = tc.id
                )
                workingMessages.add(toolResultMsg)
            }
        }

        if (iterations >= MAX_ITERATIONS) {
            LOGGER.warn("[Airi] Reached max tool call iterations ($MAX_ITERATIONS)")
        }

        if (finalAssistantReply.isBlank()) {
            finalAssistantReply = "好的，已经弄好啦~ (｡･ω･｡)ﾉ♡"
        }

        return finalAssistantReply
    }
}
