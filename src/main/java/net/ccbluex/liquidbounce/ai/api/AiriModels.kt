// skid AIRI
/*
 * Air Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 */
package net.ccbluex.liquidbounce.ai.api

import com.google.gson.annotations.SerializedName

/**
 * OpenAI 兼容 API 的请求/响应数据类
 *
 * 默认兼容 DeepSeek、OpenAI、OpenRouter、Groq、Moonshot 等遵循 OpenAI Chat Completions 协议的服务端。
 */

// ===== Request =====

data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Float = 0.7f,
    @SerializedName("max_tokens")
    val maxTokens: Int? = null,
    val stream: Boolean = false,
    /** DeepSeek / OpenAI reasoning 模型开关 */
    @SerializedName("enable_thinking", alternate = ["thinking"])
    val enableThinking: Boolean? = null,
    /** 思考强度(部分模型支持,如 0..1) */
    @SerializedName("thinking_budget", alternate = ["reasoning_effort"])
    val thinkingBudget: Any? = null,
    /** 工具调用(P6 阶段接入) */
    val tools: List<ToolDefinition>? = null,
    @SerializedName("tool_choice")
    val toolChoice: String? = null
)

data class ChatMessage(
    val role: String,                // system | user | assistant | tool
    val content: String,
    val name: String? = null,
    @SerializedName("tool_calls")
    val toolCalls: List<ToolCall>? = null,
    @SerializedName("tool_call_id")
    val toolCallId: String? = null
)

data class ToolDefinition(
    val type: String = "function",
    val function: ToolFunction
)

data class ToolFunction(
    val name: String,
    val description: String,
    val parameters: Map<String, Any>     // JSON Schema
)

data class ToolCall(
    val id: String,
    val type: String = "function",
    val function: ToolCallFunction
)

data class ToolCallFunction(
    val name: String,
    val arguments: String                // JSON 字符串
)

// ===== Response =====

data class ChatCompletionResponse(
    val id: String? = null,
    val model: String? = null,
    val choices: List<Choice> = emptyList(),
    val usage: Usage? = null,
    val error: ApiError? = null
)

data class Choice(
    val index: Int = 0,
    val message: ChatMessage? = null,
    @SerializedName("finish_reason")
    val finishReason: String? = null
)

data class Usage(
    @SerializedName("prompt_tokens")
    val promptTokens: Int = 0,
    @SerializedName("completion_tokens")
    val completionTokens: Int = 0,
    @SerializedName("total_tokens")
    val totalTokens: Int = 0,

    // DeepSeek reasoning 模型额外字段
    @SerializedName("completion_tokens_details")
    val completionTokensDetails: CompletionTokensDetails? = null
)

data class CompletionTokensDetails(
    @SerializedName("reasoning_tokens")
    val reasoningTokens: Int = 0
)

data class ApiError(
    val message: String = "",
    val type: String? = null,
    val code: String? = null
)
