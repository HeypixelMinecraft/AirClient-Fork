// skid AIRI
/*
 * Air Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 */
package net.ccbluex.liquidbounce.ai.script

import net.ccbluex.liquidbounce.ai.api.AiriSettings
import net.ccbluex.liquidbounce.ai.api.ToolDefinition
import net.ccbluex.liquidbounce.ai.api.ToolFunction
import net.ccbluex.liquidbounce.ai.interaction.ClientInteractor
import net.ccbluex.liquidbounce.script.ScriptManager
import net.ccbluex.liquidbounce.utils.client.ClientUtils.LOGGER
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.ChatLine
import net.minecraft.util.EnumChatFormatting
import java.awt.Desktop
import java.net.URI
import java.io.File

/**
 * 脚本桥接 - 提供 AI 工具调用接口与 [SafetyGuard] 安全审核
 *
 * 工具:
 *   - list_scripts   列出 scripts 目录下所有 .js 文件
 *   - read_script    读取脚本内容
 *   - write_script   写入/创建脚本(经 SafetyGuard 审核)
 *   - delete_script  删除脚本
 *   - reload_scripts 重新加载所有脚本(使写入立即生效)
 */
object ScriptBridge {

    /** 工具调用结果 */
    data class Result(val success: Boolean, val message: String, val data: Any? = null)

    /** OpenAI 工具定义(在 script 模式下注入到请求) */
    val toolDefinitions: List<ToolDefinition> = listOf(
        ToolDefinition(
            function = ToolFunction(
                name = "toggle_module",
                description = "Toggle (enable or disable) a client module by name. The module will be toggled to the opposite of its current state.",
                parameters = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "name" to mapOf("type" to "string", "description" to "Module name, e.g. KillAura, Sprint, Fly, Velocity, Scaffold, etc.")
                    ),
                    "required" to listOf("name")
                )
            )
        ),
        ToolDefinition(
            function = ToolFunction(
                name = "press_key",
                description = "Press a key or mouse button once (single tap).",
                parameters = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "key" to mapOf("type" to "string", "description" to "Key name: w/a/s/d/space/shift/jump/sneak/sprint/attack/use/left/right/middle or any key name like KEY_R")
                    ),
                    "required" to listOf("key")
                )
            )
        ),
        ToolDefinition(
            function = ToolFunction(
                name = "hold_key",
                description = "Hold down a key or mouse button for a number of game ticks (20 ticks = 1 second).",
                parameters = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "key" to mapOf("type" to "string", "description" to "Key name: w/a/s/d/space/shift/jump/sneak/sprint/attack/use/left/right/middle"),
                        "ticks" to mapOf("type" to "integer", "description" to "Number of ticks to hold (default 20 = 1 second)")
                    ),
                    "required" to listOf("key")
                )
            )
        ),
        ToolDefinition(
            function = ToolFunction(
                name = "send_chat",
                description = "Send a chat message to the server as the player.",
                parameters = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "message" to mapOf("type" to "string", "description" to "The chat message to send")
                    ),
                    "required" to listOf("message")
                )
            )
        ),
        ToolDefinition(
            function = ToolFunction(
                name = "execute_command",
                description = "Execute a client command (starts with . or without, both work).",
                parameters = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "command" to mapOf("type" to "string", "description" to "Command string, e.g. toggle KillAura, bind, etc.")
                    ),
                    "required" to listOf("command")
                )
            )
        ),
        ToolDefinition(
            function = ToolFunction(
                name = "list_scripts",
                description = "List all script files (.js) in the client's scripts folder.",
                parameters = mapOf("type" to "object", "properties" to emptyMap<String, Any>(), "required" to emptyList<String>())
            )
        ),
        ToolDefinition(
            function = ToolFunction(
                name = "read_script",
                description = "Read the content of a script file by name.",
                parameters = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "name" to mapOf("type" to "string", "description" to "Script file name (e.g. example.js)")
                    ),
                    "required" to listOf("name")
                )
            )
        ),
        ToolDefinition(
            function = ToolFunction(
                name = "write_script",
                description = "Create or overwrite a script file. Content is validated for safety.",
                parameters = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "name" to mapOf("type" to "string", "description" to "Script file name (must end with .js)"),
                        "content" to mapOf("type" to "string", "description" to "Full JavaScript content of the script")
                    ),
                    "required" to listOf("name", "content")
                )
            )
        ),
        ToolDefinition(
            function = ToolFunction(
                name = "delete_script",
                description = "Delete a script file by name.",
                parameters = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "name" to mapOf("type" to "string", "description" to "Script file name to delete")
                    ),
                    "required" to listOf("name")
                )
            )
        ),
        ToolDefinition(
            function = ToolFunction(
                name = "reload_scripts",
                description = "Reload all scripts so that newly written/deleted scripts take effect immediately.",
                parameters = mapOf("type" to "object", "properties" to emptyMap<String, Any>(), "required" to emptyList<String>())
            )
        ),
        ToolDefinition(
            function = ToolFunction(
                name = "get_chat_history",
                description = "Get recent chat messages from the Minecraft chat bar. Returns the last N messages with their raw text. You can use this to see what other players are saying, server announcements, and your own messages.",
                parameters = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "count" to mapOf("type" to "integer", "description" to "Number of recent messages to retrieve (default 20, max 100)")
                    ),
                    "required" to emptyList<String>()
                )
            )
        ),
        ToolDefinition(
            function = ToolFunction(
                name = "open_browser",
                description = "Open the user's default browser and navigate to a URL. Use this to show websites, search results, documentation, or any web resource to the user.",
                parameters = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "url" to mapOf("type" to "string", "description" to "The URL to open in the browser, e.g. https://www.google.com")
                    ),
                    "required" to listOf("url")
                )
            )
        )
    )

    /** 执行工具调用 */
    fun execute(name: String, argsJson: String): Result {
        val args = parseArgs(argsJson)
        return when (name) {
            "toggle_module" -> toggleModule(args["name"] ?: "")
            "press_key" -> pressKey(args["key"] ?: "")
            "hold_key" -> holdKey(args["key"] ?: "", args["ticks"]?.toIntOrNull() ?: 20)
            "send_chat" -> sendChat(args["message"] ?: "")
            "execute_command" -> executeCommand(args["command"] ?: "")
            "list_scripts" -> listScripts()
            "read_script" -> readScript(args["name"] ?: "")
            "write_script" -> writeScript(args["name"] ?: "", args["content"] ?: "")
            "delete_script" -> deleteScript(args["name"] ?: "")
            "reload_scripts" -> reloadScripts()
            "get_chat_history" -> getChatHistory(args["count"]?.toIntOrNull() ?: 20)
            "open_browser" -> openBrowser(args["url"] ?: "")
            else -> Result(false, "Unknown tool: $name")
        }
    }

    private fun toggleModule(name: String): Result {
        if (name.isBlank()) return Result(false, "Module name is required")
        val success = ClientInteractor.toggleModule(name)
        return if (success) {
            Result(true, "Module '$name' toggled successfully")
        } else {
            Result(false, "Failed to toggle module '$name' (check name or interaction permissions)")
        }
    }

    private fun pressKey(key: String): Result {
        if (key.isBlank()) return Result(false, "Key name is required")
        val parsed = ClientInteractor.parseKey(key)
            ?: return Result(false, "Unknown key: $key. Try w/a/s/d/space/shift/jump/sneak/attack/use")
        val success = ClientInteractor.pressKey(parsed.first, parsed.second)
        return if (success) {
            Result(true, "Key '$key' pressed")
        } else {
            Result(false, "Failed to press key '$key'")
        }
    }

    private fun holdKey(key: String, ticks: Int): Result {
        if (key.isBlank()) return Result(false, "Key name is required")
        val parsed = ClientInteractor.parseKey(key)
            ?: return Result(false, "Unknown key: $key")
        val success = ClientInteractor.holdKey(parsed.first, ticks, parsed.second)
        return if (success) {
            Result(true, "Holding key '$key' for $ticks ticks")
        } else {
            Result(false, "Failed to hold key '$key'")
        }
    }

    private fun sendChat(message: String): Result {
        if (message.isBlank()) return Result(false, "Message is required")
        val success = ClientInteractor.sendChat(message)
        return if (success) {
            Result(true, "Chat message sent")
        } else {
            Result(false, "Failed to send chat message")
        }
    }

    private fun executeCommand(cmd: String): Result {
        if (cmd.isBlank()) return Result(false, "Command is required")
        val success = ClientInteractor.executeCommand(cmd)
        return if (success) {
            Result(true, "Command executed: $cmd")
        } else {
            Result(false, "Failed to execute command '$cmd'")
        }
    }

    private fun listScripts(): Result {
        val files = ScriptManager.availableScriptFiles
        val list = files.map { mapOf("name" to it.name, "size" to it.length()) }
        return Result(true, "Found ${list.size} script(s).", list)
    }

    private fun readScript(name: String): Result {
        if (!isValidName(name)) return Result(false, "Invalid script name: $name")
        val file = File(ScriptManager.scriptsFolder, name)
        if (!file.exists()) return Result(false, "Script not found: $name")
        return try {
            val content = file.readText()
            Result(true, "Script $name loaded (${content.length} chars).", content)
        } catch (t: Throwable) {
            Result(false, "Failed to read: ${t.message}")
        }
    }

    private fun writeScript(name: String, content: String): Result {
        if (!isValidName(name)) return Result(false, "Invalid script name: $name (must end with .js)")
        if (!ScriptManager.scriptsFolder.exists()) ScriptManager.scriptsFolder.mkdirs()

        // 安全审核
        val audit = SafetyGuard.audit(content)
        if (!audit.safe) {
            LOGGER.warn("[Airi] Script write blocked by SafetyGuard: ${audit.reason}")
            return Result(false, "Script blocked by safety check: ${audit.reason}")
        }

        val file = File(ScriptManager.scriptsFolder, name)
        val isOverwrite = file.exists()
        return try {
            file.writeText(content)
            LOGGER.info("[Airi] Script ${if (isOverwrite) "overwritten" else "created"}: $name (${content.length} chars)")
            Result(true, "Script ${if (isOverwrite) "overwritten" else "created"}: $name. Call reload_scripts to apply.")
        } catch (t: Throwable) {
            Result(false, "Failed to write: ${t.message}")
        }
    }

    private fun deleteScript(name: String): Result {
        if (!isValidName(name)) return Result(false, "Invalid script name: $name")
        val file = File(ScriptManager.scriptsFolder, name)
        if (!file.exists()) return Result(false, "Script not found: $name")
        return try {
            file.delete()
            LOGGER.info("[Airi] Script deleted: $name")
            Result(true, "Script deleted: $name. Call reload_scripts to apply.")
        } catch (t: Throwable) {
            Result(false, "Failed to delete: ${t.message}")
        }
    }

    private fun reloadScripts(): Result {
        return try {
            ScriptManager.reloadScripts()
            Result(true, "Scripts reloaded.")
        } catch (t: Throwable) {
            Result(false, "Reload failed: ${t.message}")
        }
    }

    /** 验证脚本名(只允许 .js 后缀,不含路径分隔符) */
    private fun isValidName(name: String): Boolean {
        if (name.isBlank()) return false
        if (!name.endsWith(".js", ignoreCase = true)) return false
        if (name.contains('/') || name.contains('\\')) return false
        if (name.contains("..")) return false
        return true
    }

    /** 简易 JSON 参数解析(只处理顶层字符串字段,够用) */
    private fun parseArgs(json: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        if (json.isBlank() || json == "{}") return result
        return try {
            val obj = com.google.gson.JsonParser().parse(json).asJsonObject
            obj.entrySet().forEach { (k, v) ->
                if (!v.isJsonNull) {
                    // 字符串字段直接取值,其它类型转字符串
                    result[k] = if (v.isJsonPrimitive) v.asString else v.toString()
                }
            }
            result
        } catch (t: Throwable) {
            LOGGER.warn("[Airi] Failed to parse tool args: $json")
            result
        }
    }

    /** 获取 Minecraft 聊天栏历史记录 */
    private fun getChatHistory(count: Int): Result {
        val mc = Minecraft.getMinecraft()
        if (mc.ingameGUI == null) return Result(false, "Chat GUI not available")

        val limitedCount = count.coerceIn(1, 100)
        return try {
            // 通过反射获取 GuiNewChat 的 chatLines 字段
            val chatGui = mc.ingameGUI.chatGUI
            val chatLinesField = chatGui.javaClass.getDeclaredField("chatLines")
            chatLinesField.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            val chatLines = chatLinesField.get(chatGui) as? List<ChatLine>
                ?: return Result(false, "Failed to access chat lines")

            val playerName = mc.thePlayer?.name ?: "Unknown"
            val sb = StringBuilder()
            val lines = chatLines.take(limitedCount)

            for ((index, line) in lines.withIndex()) {
                val rawText = EnumChatFormatting.getTextWithoutFormattingCodes(
                    line.chatComponent.unformattedText
                )
                // 分类消息
                val category = when {
                    rawText.startsWith("<$playerName>") || rawText.startsWith("[$playerName") -> "[自己]"
                    rawText.startsWith("<") && rawText.contains(">") -> "[玩家]"
                    rawText.startsWith("[") && rawText.contains("]") -> "[提示/系统]"
                    else -> "[其他]"
                }
                sb.append("$category $rawText\n")
            }

            if (sb.isEmpty()) {
                Result(true, "No chat messages found.")
            } else {
                Result(true, "Recent $limitedCount chat message(s):\n${sb.trimEnd()}")
            }
        } catch (t: Throwable) {
            LOGGER.warn("[Airi] Failed to get chat history: ${t.message}")
            Result(false, "Failed to get chat history: ${t.message}")
        }
    }

    /** 打开浏览器访问指定 URL */
    private fun openBrowser(url: String): Result {
        if (url.isBlank()) return Result(false, "URL is required")
        val trimmedUrl = url.trim()
        // 确保URL有协议前缀
        val finalUrl = if (!trimmedUrl.startsWith("http://") && !trimmedUrl.startsWith("https://")) {
            "https://$trimmedUrl"
        } else {
            trimmedUrl
        }
        return try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI(finalUrl))
                LOGGER.info("[Airi] Opened browser: $finalUrl")
                Result(true, "Opened browser to: $finalUrl")
            } else {
                LOGGER.warn("[Airi] Desktop.browse not supported on this platform")
                Result(false, "Opening browser is not supported on this platform")
            }
        } catch (t: Throwable) {
            LOGGER.error("[Airi] Failed to open browser: ${t.message}")
            Result(false, "Failed to open browser: ${t.message}")
        }
    }
}
