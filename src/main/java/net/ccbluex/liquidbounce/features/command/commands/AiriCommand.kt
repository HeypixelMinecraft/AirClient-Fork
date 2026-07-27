// skid AIRI
/*
 * Air Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 */
package net.ccbluex.liquidbounce.features.command.commands

import net.ccbluex.liquidbounce.ai.api.AiriSettings
import net.ccbluex.liquidbounce.ai.conversation.ConversationManager
import net.ccbluex.liquidbounce.features.command.Command
import net.ccbluex.liquidbounce.features.module.modules.client.Airi
import net.ccbluex.liquidbounce.file.FileManager.airiConfig
import net.ccbluex.liquidbounce.file.FileManager.saveConfig
import net.ccbluex.liquidbounce.utils.client.chat

/**
 * `.airi` 命令组
 *
 * P1 实现的子命令:
 *   - on / off           打开 / 关闭 Airi GUI(P2 起生效,P1 阶段等同触发模块)
 *   - send <msg>         发送一条消息给 AI,回复显示在聊天框
 *   - endpoint <url>     设置 API 端点
 *   - key <sk-...>       设置 API 密钥
 *   - model <name>       切换模型
 *   - models             列出可用模型
 *   - mode <chat|script|roleplay>
 *   - list               列出对话
 *   - new [title]        新建对话
 *   - switch <index>     切换对话
 *   - delete <index>     删除对话
 *   - clear              清空当前对话
 *   - status             显示当前配置状态
 *   - think <on|off>     开关思考
 *   - thinkstrength <0-1>
 *   - interact <on|off>  开关客户端交互
 *   - help               显示帮助
 */
object AiriCommand : Command("airi", "ai") {

    override fun execute(args: Array<String>) {
        val usedAlias = args[0].lowercase()

        if (args.size < 2) {
            printHelp(usedAlias)
            return
        }

        when (args[1].lowercase()) {
            "on" -> {
                Airi.toggle()
                chat("§b[Airi] §7Module toggled.")
            }
            "off" -> {
                if (Airi.state) Airi.toggle()
                chat("§b[Airi] §7Module disabled.")
            }
            "send", "ask" -> {
                if (args.size < 3) {
                    chatSyntax("$usedAlias send <message...>")
                    return
                }
                val message = args.drop(2).joinToString(" ")
                chat("§b[Airi] §7You: §f$message")
                Airi.sendAsync(message) { reply, success ->
                    chat(reply)
                }
            }
            "endpoint" -> {
                if (args.size < 3) {
                    chat("§b[Airi] §7Current endpoint: §f${AiriSettings.endpoint}")
                    chatSyntax("$usedAlias endpoint <url>")
                    return
                }
                AiriSettings.endpoint = args[2]
                saveConfig(airiConfig)
                chat("§b[Airi] §7Endpoint set to: §f${AiriSettings.endpoint}")
            }
            "key" -> {
                if (args.size < 3) {
                    val masked = if (AiriSettings.apiKey.isNotEmpty()) {
                        "${AiriSettings.apiKey.take(3)}***${AiriSettings.apiKey.takeLast(3)}"
                    } else "(not set)"
                    chat("§b[Airi] §7Current key: §f$masked")
                    chatSyntax("$usedAlias key <sk-...>")
                    return
                }
                AiriSettings.apiKey = args[2]
                saveConfig(airiConfig)
                chat("§b[Airi] §7API key updated.")
            }
            "model" -> {
                if (args.size < 3) {
                    chat("§b[Airi] §7Current model: §f${AiriSettings.model}")
                    chat("§7Available: §f${AiriSettings.models.joinToString(", ")}")
                    chatSyntax("$usedAlias model <name>")
                    return
                }
                val m = args[2]
                if (m !in AiriSettings.models) {
                    AiriSettings.models.add(m)
                }
                AiriSettings.model = m
                saveConfig(airiConfig)
                chat("§b[Airi] §7Model set to: §f$m")
            }
            "models" -> {
                if (args.size >= 3) {
                    when (args[2].lowercase()) {
                        "add" -> {
                            if (args.size < 4) {
                                chatSyntax("$usedAlias models add <name>")
                                return
                            }
                            val name = args[3]
                            if (name in AiriSettings.models) {
                                chat("§cModel '$name' already exists.")
                                return
                            }
                            AiriSettings.models.add(name)
                            saveConfig(airiConfig)
                            chat("§b[Airi] §7Added model: §f$name")
                            return
                        }
                        "remove", "del" -> {
                            if (args.size < 4) {
                                chatSyntax("$usedAlias models remove <name|index>")
                                return
                            }
                            val arg = args[3]
                            val target = arg.toIntOrNull()?.let { idx ->
                                AiriSettings.models.getOrNull(idx)
                            } ?: arg
                            if (target == null || target !in AiriSettings.models) {
                                chat("§cModel not found.")
                                return
                            }
                            if (AiriSettings.models.size <= 1) {
                                chat("§cCannot remove the last model.")
                                return
                            }
                            AiriSettings.models.remove(target)
                            if (AiriSettings.model == target) {
                                AiriSettings.model = AiriSettings.models.first()
                            }
                            saveConfig(airiConfig)
                            chat("§b[Airi] §7Removed model: §f$target")
                            return
                        }
                        else -> {
                            chatSyntax("$usedAlias models [add|remove]")
                            return
                        }
                    }
                }
                chat("§b[Airi] §7Available models:")
                AiriSettings.models.forEachIndexed { i, name ->
                    val marker = if (name == AiriSettings.model) " §a(current)" else ""
                    chat("§8$i: §f$name$marker")
                }
            }
            "style" -> {
                val supported = listOf("Minimal", "Card", "Glass", "Drawer", "DualPanel", "Collapsible", "Immersive")
                if (args.size < 3) {
                    chat("§b[Airi] §7Current style: §f${AiriSettings.uiStyle}")
                    chat("§7Available: §f${supported.joinToString(", ")}")
                    chatSyntax("$usedAlias style <name>")
                    return
                }
                val s = args[2].replaceFirstChar { it.uppercase() }
                if (s !in supported) {
                    chat("§cInvalid style. Use: ${supported.joinToString(", ")}")
                    return
                }
                AiriSettings.uiStyle = s
                saveConfig(airiConfig)
                chat("§b[Airi] §7UI style set to: §f$s §7(reopen Airi GUI to apply)")
            }
            "mode" -> {
                if (args.size < 3) {
                    chat("§b[Airi] §7Current mode: §f${AiriSettings.mode}")
                    chatSyntax("$usedAlias mode <chat|script|roleplay>")
                    return
                }
                val m = args[2].lowercase()
                if (m !in listOf("chat", "script", "roleplay")) {
                    chat("§cInvalid mode. Use: chat | script | roleplay")
                    return
                }
                AiriSettings.mode = m
                saveConfig(airiConfig)
                chat("§b[Airi] §7Mode set to: §f$m")
            }
            "think" -> {
                if (args.size < 3) {
                    chat("§b[Airi] §7Think enabled: §f${AiriSettings.thinkEnabled}")
                    chatSyntax("$usedAlias think <on|off>")
                    return
                }
                AiriSettings.thinkEnabled = args[2].lowercase() == "on"
                saveConfig(airiConfig)
                chat("§b[Airi] §7Think: §f${AiriSettings.thinkEnabled}")
            }
            "thinkstrength", "thinkstr" -> {
                if (args.size < 3) {
                    chat("§b[Airi] §7Think strength: §f${AiriSettings.thinkStrength}")
                    chatSyntax("$usedAlias thinkstrength <0.0-1.0>")
                    return
                }
                val v = args[2].toFloatOrNull()
                if (v == null || v < 0f || v > 1f) {
                    chat("§cInvalid value. Use 0.0 - 1.0")
                    return
                }
                AiriSettings.thinkStrength = v
                saveConfig(airiConfig)
                chat("§b[Airi] §7Think strength: §f$v")
            }
            "interact" -> {
                if (args.size < 3) {
                    chat("§b[Airi] §7Interaction allowed: §f${AiriSettings.interactionAllowed}")
                    chatSyntax("$usedAlias interact <on|off>")
                    return
                }
                AiriSettings.interactionAllowed = args[2].lowercase() == "on"
                saveConfig(airiConfig)
                chat("§b[Airi] §7Interaction: §f${AiriSettings.interactionAllowed}")
            }
            "list" -> {
                val all = ConversationManager.all()
                if (all.isEmpty()) {
                    chat("§b[Airi] §7No conversations.")
                    return
                }
                chat("§b[Airi] §7Conversations:")
                all.forEachIndexed { i, c ->
                    val current = if (c.id == AiriSettings.currentConversationId) " §a(current)" else ""
                    chat("§8$i: §f${c.title} §7(${c.messages.size} msgs, ${c.totalTokens()} tokens)$current")
                }
            }
            "new" -> {
                val title = if (args.size >= 3) args.drop(2).joinToString(" ") else "New Chat"
                val c = ConversationManager.createConversation(title)
                saveConfig(airiConfig)
                chat("§b[Airi] §7Created: §f${c.title}")
            }
            "switch" -> {
                if (args.size < 3) {
                    chatSyntax("$usedAlias switch <index>")
                    return
                }
                val idx = args[2].toIntOrNull()
                if (idx == null || !ConversationManager.switchByIndex(idx)) {
                    chat("§cInvalid index.")
                    return
                }
                saveConfig(airiConfig)
                chat("§b[Airi] §7Switched to: §f${ConversationManager.current.title}")
            }
            "delete", "del" -> {
                if (args.size < 3) {
                    chatSyntax("$usedAlias delete <index>")
                    return
                }
                val idx = args[2].toIntOrNull()
                val all = ConversationManager.all()
                if (idx == null || idx < 0 || idx >= all.size) {
                    chat("§cInvalid index.")
                    return
                }
                val target = all[idx]
                if (ConversationManager.deleteConversation(target.id)) {
                    saveConfig(airiConfig)
                    chat("§b[Airi] §7Deleted: §f${target.title}")
                } else {
                    chat("§cDelete failed.")
                }
            }
            "clear" -> {
                ConversationManager.clearCurrent()
                saveConfig(airiConfig)
                chat("§b[Airi] §7Current conversation cleared.")
            }
            "status" -> {
                chat("§b===== Airi Status =====")
                chat("§7Endpoint: §f${AiriSettings.endpoint}")
                val masked = if (AiriSettings.apiKey.isNotEmpty()) "${AiriSettings.apiKey.take(3)}***${AiriSettings.apiKey.takeLast(3)}" else "(not set)"
                chat("§7Key: §f$masked")
                chat("§7Model: §f${AiriSettings.model}")
                chat("§7Mode: §f${AiriSettings.mode}")
                chat("§7Think: §f${AiriSettings.thinkEnabled} (strength=${AiriSettings.thinkStrength})")
                chat("§7Interact: §f${AiriSettings.interactionAllowed}")
                chat("§7Trust mode: §f${AiriSettings.trustMode}")
                chat("§7Conversations: §f${ConversationManager.all().size}")
                chat("§7Current: §f${ConversationManager.current.title}")
            }
            "help" -> printHelp(usedAlias)
            else -> chatSyntaxError()
        }
    }

    override fun tabComplete(args: Array<String>): List<String> {
        if (args.isEmpty()) return emptyList()
        return when (args.size) {
            1 -> listOf(
                "on", "off", "send", "endpoint", "key", "model", "models", "mode",
                "think", "thinkstrength", "interact", "list", "new", "switch",
                "delete", "clear", "status", "style", "help"
            ).filter { it.startsWith(args[0], true) }

            2 -> when (args[0].lowercase()) {
                "mode" -> listOf("chat", "script", "roleplay").filter { it.startsWith(args[1], true) }
                "think", "interact" -> listOf("on", "off").filter { it.startsWith(args[1], true) }
                "models" -> listOf("add", "remove").filter { it.startsWith(args[1], true) }
                "style" -> listOf("Minimal", "Card", "Glass", "Drawer", "DualPanel", "Collapsible", "Immersive").filter { it.startsWith(args[1], true) }
                else -> emptyList()
            }

            else -> emptyList()
        }
    }

    private fun printHelp(usedAlias: String) {
        chat("§b===== Airi Help =====")
        chat("§7$usedAlias send <msg> §8- §fSend a message to AI")
        chat("§7$usedAlias endpoint <url> §8- §fSet API endpoint")
        chat("§7$usedAlias key <sk-...> §8- §fSet API key")
        chat("§7$usedAlias model <name> §8- §fSwitch model")
        chat("§7$usedAlias models §8- §fList available models")
        chat("§7$usedAlias models add <name> §8- §fAdd a custom model")
        chat("§7$usedAlias models remove <name|index> §8- §fRemove a model")
        chat("§7$usedAlias style <Minimal|Card|Glass|Drawer> §8- §fSwitch Airi GUI style")
        chat("§7$usedAlias mode <chat|script|roleplay> §8- §fSwitch mode")
        chat("§7$usedAlias think <on|off> §8- §fToggle thinking")
        chat("§7$usedAlias thinkstrength <0-1> §8- §fThinking strength")
        chat("§7$usedAlias interact <on|off> §8- §fToggle client interaction")
        chat("§7$usedAlias new [title] §8- §fNew conversation")
        chat("§7$usedAlias list §8- §fList conversations")
        chat("§7$usedAlias switch <index> §8- §fSwitch conversation")
        chat("§7$usedAlias delete <index> §8- §fDelete conversation")
        chat("§7$usedAlias clear §8- §fClear current conversation")
        chat("§7$usedAlias status §8- §fShow current status")
    }
}
