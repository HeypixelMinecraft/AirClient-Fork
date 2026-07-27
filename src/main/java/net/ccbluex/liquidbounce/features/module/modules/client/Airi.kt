// skid AIRI
/*
 * Air Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 */
package net.ccbluex.liquidbounce.features.module.modules.client

import kotlinx.coroutines.launch
import net.ccbluex.liquidbounce.ai.api.AiriSettings
import net.ccbluex.liquidbounce.ai.conversation.ConversationManager
import net.ccbluex.liquidbounce.ai.interaction.ClientInteractor
import net.ccbluex.liquidbounce.ai.roleplay.RoleplayEngine
import net.ccbluex.liquidbounce.event.GameTickEvent
import net.ccbluex.liquidbounce.event.PostInputEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.file.FileManager.airiConfig
import net.ccbluex.liquidbounce.file.FileManager.saveConfig
import net.ccbluex.liquidbounce.ui.client.clickgui.airi.AiriClickGui
import net.ccbluex.liquidbounce.utils.client.chat
import net.ccbluex.liquidbounce.utils.kotlin.SharedScopes
import org.lwjgl.input.Keyboard

/**
 * Airi - 客户端 AI 集成模块
 *
 * - 开关逻辑参考 ClickGUI:onEnable 打开 GUI,关 GUI 自动关闭
 * - AI 调用(命令/角色扮演)不依赖模块 state,由 [AiriSettings.interactionAllowed] 控制
 * - tick 事件驱动 [RoleplayEngine] 与 [ClientInteractor] 按键释放
 */
object Airi : Module("Airi", Category.CLIENT, Keyboard.KEY_NONE, canBeEnabled = false) {

    override fun onEnable() {
        // 打开 Airi GUI(参考 ClickGUI 的 canBeEnabled=false 模式)
        mc.displayGuiScreen(AiriClickGui())
        Keyboard.enableRepeatEvents(true)
        // 启动角色扮演引擎(若当前为 roleplay 模式)
        RoleplayEngine.start()
    }

    override fun onDisable() {
        saveConfig(airiConfig)
        Keyboard.enableRepeatEvents(false)
        RoleplayEngine.stop()
        ClientInteractor.releaseAllKeys()
    }

    /** Tick 事件处理:驱动角色扮演引擎
     *  使用 always=true 确保即使 Airi GUI 已关闭也能正常工作
     *  (RoleplayEngine.onTick 内部有 registered 守卫,未启动时直接 return) */
    @Suppress("unused")
    private val onTick = handler<GameTickEvent>(always = true) {
        RoleplayEngine.onTick()
    }

    /** PostInput 事件处理:在 dispatchKeypresses 之后执行按键模拟
     *  这是按键模拟的正确时机 — dispatchKeypresses 已执行完毕,
     *  后续的 sendClickBlockToController/movementInput 能检测到设置的按键状态 */
    @Suppress("unused")
    private val onPostInput = handler<PostInputEvent>(always = true) {
        ClientInteractor.onPostInput()
    }

    /**
     * 发送一条用户消息并异步获取 AI 回复
     *
     * - 自动追加到当前对话
     * - 在 IO 协程中执行,不阻塞调用方
     * - 结果通过 [onReply] 回调(主线程同步上下文,调用方需自行线程切换)
     */
    fun sendAsync(content: String, onReply: (String, Boolean) -> Unit) {
        if (AiriSettings.apiKey.isBlank()) {
            onReply("§cAPI key not set. Use §f.airi key <sk-...>§c to set it.", false)
            return
        }

        // 速率限制
        val now = System.currentTimeMillis()
        if (now - AiriSettings.lastRequestTimestamp < 60_000L) {
            if (AiriSettings.requestCountInWindow >= AiriSettings.rateLimitPerMinute) {
                onReply("§cRate limit exceeded. Try again later.", false)
                return
            }
            AiriSettings.requestCountInWindow++
        } else {
            AiriSettings.lastRequestTimestamp = now
            AiriSettings.requestCountInWindow = 1
        }

        ConversationManager.appendUserMessage(content)

        SharedScopes.IO.launch {
            val msg = ConversationManager.sendCurrentStream()
            val display = if (msg.error != null) {
                "§cError: ${msg.error}"
            } else {
                "§b[Airi] §f${msg.content} §7(${msg.durationMs}ms, ${msg.tokens} tokens)"
            }
            onReply(display, msg.error == null)

            // 异步保存对话
            saveConfig(airiConfig)
        }
    }
}
