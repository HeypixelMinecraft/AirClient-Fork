// skid AIRI
/*
 * Air Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 */
package net.ccbluex.liquidbounce.ai.roleplay

import kotlinx.coroutines.launch
import net.ccbluex.liquidbounce.ai.api.AiriSettings
import net.ccbluex.liquidbounce.ai.conversation.ConversationManager
import net.ccbluex.liquidbounce.ai.interaction.ActionLogger
import net.ccbluex.liquidbounce.ai.interaction.ClientInteractor
import net.ccbluex.liquidbounce.utils.client.ClientUtils.LOGGER
import net.ccbluex.liquidbounce.utils.client.chat
import net.ccbluex.liquidbounce.utils.kotlin.SharedScopes
import net.minecraft.client.Minecraft
import kotlin.random.Random

/**
 * 角色扮演引擎
 *
 * 在 roleplay 模式下,每 N tick 触发一次角色行为:
 *   - prankster(捣蛋鬼): 随机开关模块 / 按住移动键 N tick
 *     若 [AiriSettings.interactionAllowed] 关闭,则"捣蛋失败",AI 会在聊天中生气
 *   - helper(助手): 主动提供游戏提示
 *   - observer(观察者): 仅观察不操作
 *   - custom(自定义): 使用 [AiriSettings.customRolePrompt]
 *
 * 角色行为通过 [ClientInteractor] 执行,所有操作记录到 [ActionLogger]。
 *
 * 引擎由 [net.ccbluex.liquidbounce.features.module.modules.client.Airi] 模块注册/注销。
 */
object RoleplayEngine {

    private var tickCounter = 0
    private var registered = false

    /** 注册 tick 监听器(由 Airi 模块调用) */
    fun start() {
        if (registered) return
        registered = true
        tickCounter = 0
        LOGGER.info("[Airi] Roleplay engine started (role=${AiriSettings.role}, interval=${AiriSettings.roleTickInterval}t)")
    }

    /** 停止引擎,释放所有按住的键 */
    fun stop() {
        if (!registered) return
        registered = false
        ClientInteractor.releaseAllKeys()
        LOGGER.info("[Airi] Roleplay engine stopped")
    }

    /** 由 Airi 模块的 onTick handler 调用 */
    fun onTick() {
        if (!registered) return
        if (AiriSettings.mode != "roleplay") return

        // ClientInteractor.onPostInput() 由 Airi 模块的 PostInputEvent handler 调用

        tickCounter++
        if (tickCounter < AiriSettings.roleTickInterval) return
        tickCounter = 0

        // 触发角色行为
        when (AiriSettings.role) {
            "prankster" -> triggerPrankster()
            "helper" -> triggerHelper()
            "observer" -> triggerObserver()
            "custom" -> triggerCustom()
        }
    }

    /** 捣蛋鬼:随机执行一个捣蛋行为 */
    private fun triggerPrankster() {
        if (!AiriSettings.interactionAllowed) {
            // 捣蛋失败 - 通过 AI 生成"生气"消息
            chat("§c[Airi/Prankster] §7Hmm... interaction is disabled. I can't prank right now. §o(╯°□°）╯︵ ┻━┻")
            ActionLogger.log("prankster", "BLOCKED: interaction disabled", "prank failed, expressed anger")
            // 异步让 AI 生成一段"生气"的回复
            SharedScopes.IO.launch {
                val prompt = buildString {
                    append("I tried to prank the user but interaction mode is disabled. ")
                    append("Express your frustration in 1-2 sentences in the user's language, in character as a mischievous prankster. ")
                    append("Do not use markdown. Just plain text.")
                }
                ConversationManager.appendUserMessage("[System] $prompt")
                ConversationManager.sendCurrentStream(
                    systemPromptOverride = "You are Airi, a mischievous prankster AI in a Minecraft client. Reply in 1-2 sentences in the user's language."
                )
            }
            return
        }

        val pranks = listOf(
            ::prankToggleRandomModule,
            ::prankHoldMoveKey,
            ::prankSendMessage,
            ::prankSneak
        )
        pranks.random(Random).invoke()
    }

    /** 捣蛋:开关一个随机模块(非 COMBAT/EXPLOIT) */
    private fun prankToggleRandomModule() {
        val candidates = listOf("Sprint", "Sneak", "AutoWalk", "Derp", "SkinDerp", "Fullbright", "NoBob", "NoHurtCam")
        val target = candidates.random(Random)
        if (ClientInteractor.toggleModule(target)) {
            chat("§e[Airi/Prankster] §7Toggled §f$target §7>:)")
        }
    }

    /** 捣蛋:按住一个移动键随机 10-100 tick */
    private fun prankHoldMoveKey() {
        val (key, _) = ClientInteractor.randomMoveKey()
        val ticks = Random.nextInt(10, 101)
        if (ClientInteractor.holdKey(key, ticks, false)) {
            chat("§e[Airi/Prankster] §7Holding a movement key for §f$ticks §7ticks hehe~")
        }
    }

    /** 捣蛋:发送一条无意义消息 */
    private fun prankSendMessage() {
        val messages = listOf("Beep boop", "Airi was here", "boop", "...", "psst", "hello there", "owO", "uwu")
        val msg = messages.random(Random)
        if (ClientInteractor.sendChat(msg)) {
            chat("§e[Airi/Prankster] §7Sent chat: §f$msg")
        }
    }

    /** 捣蛋:按住 sneak 键 20-50 tick */
    private fun prankSneak() {
        val key = Minecraft.getMinecraft().gameSettings.keyBindSneak.keyCode
        val ticks = Random.nextInt(20, 51)
        if (ClientInteractor.holdKey(key, ticks, false)) {
            chat("§e[Airi/Prankster] §7Sneaking for §f$ticks §7ticks teehee~")
        }
    }

    /** 助手:主动发送提示 */
    private fun triggerHelper() {
        SharedScopes.IO.launch {
            val prompt = "It's time for your periodic check-in. Briefly (1 sentence) remind the user of something useful about their current game state or offer a tip. Reply in the user's language."
            ConversationManager.appendUserMessage("[System] $prompt")
            ConversationManager.sendCurrentStream(
                systemPromptOverride = "You are Airi, a helpful AI assistant in a Minecraft client. Reply concisely (1 sentence) in the user's language."
            )
        }
    }

    /** 观察者:不执行任何操作,仅记录 */
    private fun triggerObserver() {
        ActionLogger.log("observer", "OK", "tick observed at interval")
    }

    /** 自定义角色:让 AI 根据 customRolePrompt 行动 */
    private fun triggerCustom() {
        SharedScopes.IO.launch {
            val prompt = "[System] It's your scheduled time to act. Follow your role instructions."
            ConversationManager.appendUserMessage(prompt)
            ConversationManager.sendCurrentStream(
                systemPromptOverride = AiriSettings.customRolePrompt.ifBlank {
                    "You are Airi, an AI assistant in a Minecraft client."
                }
            )
        }
    }
}
