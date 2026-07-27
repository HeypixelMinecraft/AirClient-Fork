// skid AIRI
/*
 * Air Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 */
package net.ccbluex.liquidbounce.file.configs

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import net.ccbluex.liquidbounce.ai.api.AiriSettings
import net.ccbluex.liquidbounce.ai.conversation.Conversation
import net.ccbluex.liquidbounce.ai.conversation.ConversationManager
import net.ccbluex.liquidbounce.file.FileConfig
import net.ccbluex.liquidbounce.utils.client.ClientUtils.LOGGER
import net.ccbluex.liquidbounce.utils.io.readJson
import net.ccbluex.liquidbounce.utils.io.writeJson
import java.io.File
import java.io.IOException

/**
 * Airi 持久化配置
 *
 * 存储:
 *   - settings:运行时设置(API key 等用 XOR 简单混淆)
 *   - conversations:所有对话历史
 *
 * 文件位置:{airclient_dir}/airi.json
 */
class AiriConfig(file: File) : FileConfig(file) {

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    @Throws(IOException::class)
    override fun loadConfig() {
        if (!file.exists() || file.length() == 0L) return
        try {
            val json = file.readJson()
            val data = gson.fromJson(json, AiriConfigData::class.java) ?: return

            // 恢复 settings
            data.settings?.let { dto ->
                AiriSettings.endpoint = dto.endpoint
                AiriSettings.apiKey = decodeKey(dto.apiKey ?: "")
                AiriSettings.model = dto.model
                AiriSettings.models = dto.models?.toMutableList() ?: mutableListOf("deepseek-v4-flash", "deepseek-v4-pro")
                AiriSettings.temperature = dto.temperature
                AiriSettings.maxTokens = dto.maxTokens
                AiriSettings.timeoutSeconds = dto.timeoutSeconds
                AiriSettings.thinkEnabled = dto.thinkEnabled
                AiriSettings.thinkStrength = dto.thinkStrength
                AiriSettings.mode = dto.mode
                AiriSettings.interactionAllowed = dto.interactionAllowed
                AiriSettings.destructiveCommandsAllowed = dto.destructiveCommandsAllowed
                AiriSettings.keyHoldMaxTicks = dto.keyHoldMaxTicks
                AiriSettings.role = dto.role
                AiriSettings.roleTickInterval = dto.roleTickInterval
                AiriSettings.customRolePrompt = dto.customRolePrompt
                AiriSettings.trustMode = dto.trustMode
                AiriSettings.rateLimitPerMinute = dto.rateLimitPerMinute
                AiriSettings.currentConversationId = dto.currentConversationId
                AiriSettings.uiStyle = dto.uiStyle ?: "Card"
            }

            // 恢复 conversations
            val conversations = data.conversations ?: emptyList()
            ConversationManager.loadFrom(conversations)

            // 重建 HTTP 客户端以应用新超时
            net.ccbluex.liquidbounce.ai.api.AiriApiClient.rebuildClient()

            LOGGER.info("[Airi] Loaded config: ${conversations.size} conversations")
        } catch (t: Throwable) {
            LOGGER.error("[Airi] Failed to load config", t)
        }
    }

    @Throws(IOException::class)
    override fun saveConfig() {
        try {
            val data = AiriConfigData(
                settings = SettingsDto(
                    endpoint = AiriSettings.endpoint,
                    apiKey = encodeKey(AiriSettings.apiKey),
                    model = AiriSettings.model,
                    models = AiriSettings.models,
                    temperature = AiriSettings.temperature,
                    maxTokens = AiriSettings.maxTokens,
                    timeoutSeconds = AiriSettings.timeoutSeconds,
                    thinkEnabled = AiriSettings.thinkEnabled,
                    thinkStrength = AiriSettings.thinkStrength,
                    mode = AiriSettings.mode,
                    interactionAllowed = AiriSettings.interactionAllowed,
                    destructiveCommandsAllowed = AiriSettings.destructiveCommandsAllowed,
                    keyHoldMaxTicks = AiriSettings.keyHoldMaxTicks,
                    role = AiriSettings.role,
                    roleTickInterval = AiriSettings.roleTickInterval,
                    customRolePrompt = AiriSettings.customRolePrompt,
                    trustMode = AiriSettings.trustMode,
                    rateLimitPerMinute = AiriSettings.rateLimitPerMinute,
                    currentConversationId = AiriSettings.currentConversationId,
                    uiStyle = AiriSettings.uiStyle
                ),
                conversations = ConversationManager.all()
            )
            file.writeJson(data, gson)
        } catch (t: Throwable) {
            LOGGER.error("[Airi] Failed to save config", t)
        }
    }

    // ===== API Key 简单 XOR 混淆(非加密,避免明文) =====

    private fun encodeKey(plain: String): String {
        if (plain.isEmpty()) return ""
        val key = XOR_KEY
        val sb = StringBuilder()
        for (i in plain.indices) {
            sb.append((plain[i].code xor key[i % key.length].code).toString(16).padStart(2, '0'))
        }
        return "xor:" + sb.toString()
    }

    private fun decodeKey(encoded: String): String {
        if (encoded.isEmpty()) return ""
        if (!encoded.startsWith("xor:")) return encoded   // 兼容明文(旧版本/手动编辑)
        val hex = encoded.removePrefix("xor:")
        if (hex.length % 2 != 0) return ""
        val key = XOR_KEY
        val sb = StringBuilder()
        var i = 0
        while (i < hex.length) {
            val byte = hex.substring(i, i + 2).toInt(16)
            sb.append((byte xor key[(i / 2) % key.length].code).toChar())
            i += 2
        }
        return sb.toString()
    }

    companion object {
        private const val XOR_KEY = "AirClient-Airi-2026"
    }
}

// ===== DTO =====

private data class AiriConfigData(
    val settings: SettingsDto? = null,
    val conversations: List<Conversation>? = null
)

private data class SettingsDto(
    val endpoint: String = "https://api.deepseek.com/v1",
    val apiKey: String = "",
    val model: String = "deepseek-v4-flash",
    val models: List<String>? = null,
    val temperature: Float = 0.7f,
    val maxTokens: Int? = null,
    val timeoutSeconds: Int = 60,
    val thinkEnabled: Boolean = false,
    val thinkStrength: Float = 0.5f,
    val mode: String = "chat",
    val interactionAllowed: Boolean = false,
    val destructiveCommandsAllowed: Boolean = false,
    val keyHoldMaxTicks: Int = 1200,
    val role: String = "observer",
    val roleTickInterval: Int = 1000,
    val customRolePrompt: String = "",
    val trustMode: Boolean = false,
    val rateLimitPerMinute: Int = 20,
    val currentConversationId: String? = null,
    val uiStyle: String? = "Card"
)
