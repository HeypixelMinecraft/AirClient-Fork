// skid AIRI
/*
 * Air Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 */
package net.ccbluex.liquidbounce.ai.api

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.ccbluex.liquidbounce.file.FileManager.PRETTY_GSON
import net.ccbluex.liquidbounce.utils.client.ClientUtils.LOGGER
import net.ccbluex.liquidbounce.utils.io.applyBypassHttps
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/**
 * OpenAI 兼容 API 客户端
 *
 * - 支持任意遵循 OpenAI Chat Completions 协议的端点(DeepSeek/OpenAI/OpenRouter/Moonshot 等)
 * - P1: 非流式调用
 * - P3: 流式 SSE 调用(预留接口)
 */
object AiriApiClient {

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    /** Airi 专用 HTTP 客户端(独立超时,不受全局 HttpClient 15s 限制) */
    private var httpClient: OkHttpClient = buildClient(AiriSettings.timeoutSeconds)

    /** 请求体序列化用 Gson(与全局 PRETTY_GSON 一致,但禁用 HTML 转义以减小体积) */
    private val requestGson: Gson = GsonBuilder().disableHtmlEscaping().create()

    /** 手动解析 SSE chunk */
    private fun parseStreamChunk(parser: JsonParser, data: String): StreamChunk? {
        val obj = parser.parse(data).asJsonObject
        val model = if (obj.has("model") && !obj.get("model").isJsonNull)
            obj.get("model").asString else null
        var deltaContent: String? = null
        var deltaReasoning: String? = null
        var finishReason: String? = null
        if (obj.has("choices") && obj.get("choices").isJsonArray) {
            val choices = obj.getAsJsonArray("choices")
            if (choices.size() > 0) {
                val choice = choices[0].asJsonObject
                if (choice.has("delta") && !choice.get("delta").isJsonNull) {
                    val delta = choice.getAsJsonObject("delta")
                    if (delta.has("content") && !delta.get("content").isJsonNull)
                        deltaContent = delta.get("content").asString
                    if (delta.has("reasoning_content") && !delta.get("reasoning_content").isJsonNull)
                        deltaReasoning = delta.get("reasoning_content").asString
                }
                if (choice.has("finish_reason") && !choice.get("finish_reason").isJsonNull)
                    finishReason = choice.get("finish_reason").asString
            }
        }
        var usage: Usage? = null
        if (obj.has("usage") && !obj.get("usage").isJsonNull) {
            usage = PRETTY_GSON.fromJson(obj.get("usage"), Usage::class.java)
        }
        return StreamChunk(model, deltaContent, deltaReasoning, finishReason, usage)
    }

    private fun buildClient(timeoutSeconds: Int): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(timeoutSeconds.toLong(), TimeUnit.SECONDS)
        .writeTimeout(timeoutSeconds.toLong(), TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .applyBypassHttps()
        .build()

    /** 当超时设置变更时重建客户端 */
    fun rebuildClient() {
        httpClient = buildClient(AiriSettings.timeoutSeconds)
    }

    /**
     * 拼接完整的 chat/completions URL
     * 兼容用户填入:
     *   - https://api.deepseek.com/v1
     *   - https://api.deepseek.com/v1/
     *   - https://api.deepseek.com/v1/chat/completions
     */
    fun buildChatUrl(endpoint: String): String {
        val base = endpoint.trimEnd('/')
        return if (base.endsWith("/chat/completions", ignoreCase = true)) {
            base
        } else {
            "$base/chat/completions"
        }
    }

    /**
     * 非流式调用 chat/completions
     *
     * @param request 请求体
     * @return 响应体;若 API 返回错误或网络异常,返回带 error 字段的 [ChatCompletionResponse]
     */
    suspend fun chat(request: ChatCompletionRequest): ChatCompletionResponse = withContext(Dispatchers.IO) {
        val url = buildChatUrl(AiriSettings.endpoint)
        val json = requestGson.toJson(request)
        LOGGER.info("[Airi] POST $url (model=${request.model}, messages=${request.messages.size})")

        val reqBuilder = Request.Builder()
            .url(url)
            .post(json.toRequestBody(JSON_MEDIA_TYPE))
            .header("Authorization", "Bearer ${AiriSettings.apiKey}")
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")

        try {
            httpClient.newCall(reqBuilder.build()).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    PRETTY_GSON.fromJson(body, ChatCompletionResponse::class.java)
                        ?: ChatCompletionResponse(error = ApiError(message = "Empty response body"))
                } else {
                    LOGGER.error("[Airi] API error ${response.code}: $body")
                    val errResponse = runCatching { PRETTY_GSON.fromJson(body, ChatCompletionResponse::class.java) }.getOrNull()
                    errResponse?.copy(
                        error = errResponse.error ?: ApiError(
                            message = "HTTP ${response.code}: ${body.take(500)}",
                            type = "http_error",
                            code = response.code.toString()
                        )
                    ) ?: ChatCompletionResponse(
                        error = ApiError(
                            message = "HTTP ${response.code}: ${body.take(500)}",
                            type = "http_error",
                            code = response.code.toString()
                        )
                    )
                }
            }
        } catch (t: Throwable) {
            LOGGER.error("[Airi] Request failed", t)
            ChatCompletionResponse(
                error = ApiError(
                    message = "${t.javaClass.simpleName}: ${t.message ?: "unknown"}",
                    type = "network_error"
                )
            )
        }
    }

    /**
     * 简化的非流式调用入口:接收消息列表与可选 system prompt,返回助手回复文本与 usage。
     */
    suspend fun complete(
        messages: List<ChatMessage>,
        systemPrompt: String? = null
    ): AiriCompletionResult {
        val fullMessages = buildList {
            if (!systemPrompt.isNullOrBlank()) add(ChatMessage(role = "system", content = systemPrompt))
            addAll(messages)
        }

        val request = ChatCompletionRequest(
            model = AiriSettings.model,
            messages = fullMessages,
            temperature = AiriSettings.temperature,
            maxTokens = AiriSettings.maxTokens,
            stream = false,
            enableThinking = if (AiriSettings.thinkEnabled) true else null,
            thinkingBudget = if (AiriSettings.thinkEnabled) AiriSettings.thinkStrength.toDouble() else null
        )

        val response = chat(request)
        return if (response.error != null) {
            AiriCompletionResult(
                success = false,
                content = "",
                errorMessage = response.error.message,
                usage = null,
                model = AiriSettings.model,
                durationMs = 0L
            )
        } else {
            val choice = response.choices.firstOrNull()
            AiriCompletionResult(
                success = true,
                content = choice?.message?.content ?: "",
                errorMessage = null,
                usage = response.usage,
                model = response.model ?: AiriSettings.model,
                durationMs = 0L   // 由调用方计时
            )
        }
    }

    /**
     * 流式调用 chat/completions (SSE)
     *
     * 服务端按 OpenAI 协议返回 data 行,末尾以 data [DONE] 结束。
     * 每收到一段文本回调 onDelta,reasoning 模型的思考片段回调 onReasoningDelta,
     * 完成时回调 onUsage (若服务端在末尾 chunk 提供)。
     */
    suspend fun streamChat(
        request: ChatCompletionRequest,
        onDelta: (String) -> Unit = {},
        onReasoningDelta: (String) -> Unit = {},
        onUsage: (Usage) -> Unit = {}
    ): ChatCompletionResponse = withContext(Dispatchers.IO) {
        val url = buildChatUrl(AiriSettings.endpoint)
        val streamReq = request.copy(stream = true)
        val json = requestGson.toJson(streamReq)
        LOGGER.info("[Airi] POST stream $url (model=${request.model}, messages=${request.messages.size})")

        val reqBuilder = Request.Builder()
            .url(url)
            .post(json.toRequestBody(JSON_MEDIA_TYPE))
            .header("Authorization", "Bearer ${AiriSettings.apiKey}")
            .header("Content-Type", "application/json")
            .header("Accept", "text/event-stream")

        val accumulatedContent = StringBuilder()
        val accumulatedReasoning = StringBuilder()
        var finalModel: String? = request.model
        var finalUsage: Usage? = null
        var firstError: ApiError? = null

        try {
            httpClient.newCall(reqBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    val body = response.body?.string().orEmpty()
                    LOGGER.error("[Airi] Stream API error ${response.code}: $body")
                    val errResp = runCatching { PRETTY_GSON.fromJson(body, ChatCompletionResponse::class.java) }.getOrNull()
                    firstError = errResp?.error ?: ApiError(
                        message = "HTTP ${response.code}: ${body.take(500)}",
                        type = "http_error",
                        code = response.code.toString()
                    )
                    return@use
                }
                val bodyStream = response.body?.byteStream() ?: return@use
                val reader = BufferedReader(InputStreamReader(bodyStream, Charsets.UTF_8))
                val parser = JsonParser()
                while (true) {
                    val l = reader.readLine() ?: break
                    if (l.isBlank() || l.startsWith(":")) continue
                    if (!l.startsWith("data:")) continue
                    val data = l.removePrefix("data:").trim()
                    if (data == "[DONE]") break
                    if (data.isEmpty()) continue
                    try {
                        val chunk = parseStreamChunk(parser, data) ?: continue
                        chunk.model?.let { finalModel = it }
                        chunk.deltaContent?.let { accumulatedContent.append(it); onDelta(it) }
                        chunk.deltaReasoning?.let { accumulatedReasoning.append(it); onReasoningDelta(it) }
                        chunk.usage?.let { finalUsage = it; onUsage(it) }
                    } catch (t: Throwable) {
                        LOGGER.warn("[Airi] Failed to parse stream chunk: ${data.take(100)}")
                    }
                }
            }
        } catch (t: Throwable) {
            LOGGER.error("[Airi] Stream request failed", t)
            firstError = ApiError(
                message = "${t.javaClass.simpleName}: ${t.message ?: "unknown"}",
                type = "network_error"
            )
        }

        ChatCompletionResponse(
            model = finalModel,
            choices = listOf(
                Choice(
                    index = 0,
                    message = ChatMessage(
                        role = "assistant",
                        content = accumulatedContent.toString()
                    ),
                    finishReason = "stop"
                )
            ),
            usage = finalUsage,
            error = firstError
        )
    }

    /**
     * 简化的流式调用入口
     */
    suspend fun streamComplete(
        messages: List<ChatMessage>,
        systemPrompt: String? = null,
        onDelta: (String) -> Unit = {},
        onReasoningDelta: (String) -> Unit = {},
        onUsage: (Usage) -> Unit = {}
    ): AiriCompletionResult {
        val fullMessages = buildList {
            if (!systemPrompt.isNullOrBlank()) add(ChatMessage(role = "system", content = systemPrompt))
            addAll(messages)
        }

        val request = ChatCompletionRequest(
            model = AiriSettings.model,
            messages = fullMessages,
            temperature = AiriSettings.temperature,
            maxTokens = AiriSettings.maxTokens,
            stream = true,
            enableThinking = if (AiriSettings.thinkEnabled) true else null,
            thinkingBudget = if (AiriSettings.thinkEnabled) AiriSettings.thinkStrength.toDouble() else null
        )

        val response = streamChat(request, onDelta, onReasoningDelta, onUsage)
        return if (response.error != null) {
            AiriCompletionResult(
                success = false,
                content = "",
                errorMessage = response.error.message,
                usage = null,
                model = AiriSettings.model,
                durationMs = 0L
            )
        } else {
            val choice = response.choices.firstOrNull()
            AiriCompletionResult(
                success = true,
                content = choice?.message?.content ?: "",
                errorMessage = null,
                usage = response.usage,
                model = response.model ?: AiriSettings.model,
                durationMs = 0L
            )
        }
    }
}

/**
 * 流式 chunk 解析数据类
 */
data class StreamChunk(
    val model: String?,
    val deltaContent: String?,
    val deltaReasoning: String?,
    val finishReason: String?,
    val usage: Usage?
)

/**
 * [AiriApiClient.complete] / [AiriApiClient.streamComplete] 的简化返回类型
 */
data class AiriCompletionResult(
    val success: Boolean,
    val content: String,
    val errorMessage: String?,
    val usage: Usage?,
    val model: String,
    val durationMs: Long,
    /** AI 请求调用的工具列表(若启用 tools 且模型决定调用) */
    val toolCalls: List<ToolCall> = emptyList()
) {
    val totalTokens: Int get() = usage?.totalTokens ?: 0
    val hasToolCalls: Boolean get() = toolCalls.isNotEmpty()
}
