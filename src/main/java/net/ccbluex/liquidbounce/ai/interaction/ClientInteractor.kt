// skid AIRI
/*
 * Air Client
 * A free open source mixin-based injection hacked client for Minecraft using Liquidbounce legacybase.
 */
package net.ccbluex.liquidbounce.ai.interaction

import net.ccbluex.liquidbounce.LiquidBounce
import net.ccbluex.liquidbounce.ai.api.AiriSettings
import net.ccbluex.liquidbounce.utils.client.ClientUtils.LOGGER
import net.minecraft.client.Minecraft
import net.minecraft.client.settings.KeyBinding
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement
import net.minecraft.network.play.client.C0APacketAnimation
import net.minecraft.util.BlockPos
import net.minecraft.util.ChatComponentText
import net.minecraft.util.EnumChatFormatting
import org.lwjgl.input.Keyboard
import kotlin.random.Random

/**
 * 客户端交互器 - 让 AI 能与 Minecraft 客户端交互
 *
 * 支持的操作:
 *   - sendChat(message)         以用户身份发送聊天消息
 *   - executeCommand(cmd)       执行客户端命令(以 . 开头或不带)
 *   - toggleModule(name)        开关指定模块
 *   - holdKey(key, ticks)       按住某键 N tick(含鼠标键)
 *   - pressKey(key)             短按一次
 *
 * 所有操作前检查 [AiriSettings.interactionAllowed] 与破坏性命令权限。
 * 所有操作都会记录到 [ActionLogger] 并在聊天栏通知用户。
 *
 * 按键模拟原理:
 *   Minecraft runTick() 执行顺序:
 *     1. dispatchKeypresses() - 处理 LWJGL 键盘事件,更新 KeyBinding.pressed
 *     2. [GameTickEvent] - 我们的 onTick() 在这里执行
 *     3. sendClickBlockToController() - 持续左键攻击(检查 keyBindAttack.pressed)
 *     4. rightClickMouse() 不在这里,只在 dispatchKeypresses 中触发
 *     5. movementInput.updatePlayerMoveState() - 检查 WASD pressed
 *
 *   关键问题:
 *     - dispatchKeypresses() 只处理 LWJGL 真实输入,无法伪造
 *     - KeyBinding.pressed 在 dispatchKeypresses 后可能被重置为 false
 *       (如果没有真实键盘事件,pressed 保持上次值;有事件才更新)
 *     - setKeyBindState() 设置的 pressed 会在下一个 runTick 中被
 *       dispatchKeypresses() 根据实际键盘状态覆盖
 *
 *   解决方案:
 *     1. 将按键操作放入 pendingActions 队列
 *     2. 在 onTick() (GameTickEvent) 中执行这些操作
 *     3. 此时 dispatchKeypresses 已执行完毕,我们设置的状态不会被覆盖
 *     4. 设置 heldKeys 跟踪,在后续 tick 中保持按键状态
 *     5. 对于鼠标按键,在 onTick 中直接反射调用 clickMouse/rightClickMouse
 *        并发送对应的网络包
 */
object ClientInteractor {

    private val mc: Minecraft get() = Minecraft.getMinecraft()

    /** 当前正在按住的按键列表(key -> 剩余 tick 数) */
    private val heldKeys = mutableMapOf<Int, Int>()

    /** 待执行的动作队列 (在 onTick 中消费) */
    private data class PendingAction(
        val key: Int,
        val isMouse: Boolean,
        val ticks: Int,
        val isPress: Boolean
    )
    private val pendingActions = mutableListOf<PendingAction>()

    /** 反射缓存: Minecraft.clickMouse() 方法 */
    private var clickMouseMethod: java.lang.reflect.Method? = null
    /** 反射缓存: Minecraft.rightClickMouse() 方法 */
    private var rightClickMouseMethod: java.lang.reflect.Method? = null

    /** 交互未启用时的提示消息 (返回给 AI) */
    const val INTERACTION_DISABLED_MSG = "Interaction is not enabled. Please ask the user to enable 'Interaction Allowed' in Airi Settings (the gear icon in Airi GUI) to allow you to perform actions."

    init {
        try {
            clickMouseMethod = Minecraft::class.java.getDeclaredMethod("clickMouse")
            clickMouseMethod?.isAccessible = true
            LOGGER.info("[Airi] clickMouse method reflection OK")
        } catch (t: Throwable) {
            LOGGER.warn("[Airi] Failed to get clickMouse method: ${t.message}")
        }
        try {
            rightClickMouseMethod = Minecraft::class.java.getDeclaredMethod("rightClickMouse")
            rightClickMouseMethod?.isAccessible = true
            LOGGER.info("[Airi] rightClickMouse method reflection OK")
        } catch (t: Throwable) {
            LOGGER.warn("[Airi] Failed to get rightClickMouse method: ${t.message}")
        }
    }

    /**
     * 发送聊天消息(以用户身份)
     * @return true 发送成功
     */
    fun sendChat(message: String): Boolean {
        if (!checkInteractAllowed("sendChat($message)")) return false
        if (mc.thePlayer == null) {
            ActionLogger.log("sendChat", "FAIL: no player", message)
            return false
        }
        try {
            mc.thePlayer.sendChatMessage(message)
            ActionLogger.log("sendChat", "OK", message)
            notifyChat("Sent chat: ${message.take(50)}")
            return true
        } catch (t: Throwable) {
            ActionLogger.log("sendChat", "FAIL: ${t.message}", message)
            return false
        }
    }

    /**
     * 执行客户端命令
     * @param cmd 命令字符串(可带或不带 . 前缀)
     */
    fun executeCommand(cmd: String): Boolean {
        if (!checkInteractAllowed("executeCommand($cmd)")) return false

        // 检查破坏性命令权限
        val isDestructive = isDestructiveCommand(cmd)
        if (isDestructive && !AiriSettings.destructiveCommandsAllowed) {
            ActionLogger.log("executeCommand", "BLOCKED: destructive not allowed", cmd)
            LOGGER.warn("[Airi] Destructive command blocked (not allowed): $cmd")
            return false
        }

        val fullCmd = if (cmd.startsWith(".")) cmd else ".$cmd"
        try {
            LiquidBounce.commandManager.executeCommands(fullCmd)
            ActionLogger.log("executeCommand", "OK" + if (isDestructive) " (destructive)" else "", fullCmd)
            notifyChat("Executed command: ${fullCmd.take(50)}")
            return true
        } catch (t: Throwable) {
            ActionLogger.log("executeCommand", "FAIL: ${t.message}", fullCmd)
            return false
        }
    }

    /**
     * 开关指定模块
     */
    fun toggleModule(moduleName: String): Boolean {
        if (!checkInteractAllowed("toggleModule($moduleName)")) return false
        val module = LiquidBounce.moduleManager[moduleName]
        if (module == null) {
            ActionLogger.log("toggleModule", "FAIL: not found", moduleName)
            return false
        }
        // 检查破坏性:COMBAT/EXPLOIT 类模块
        val isDestructive = module.category.name in listOf("COMBAT", "EXPLOIT")
        if (isDestructive && !AiriSettings.destructiveCommandsAllowed) {
            ActionLogger.log("toggleModule", "BLOCKED: destructive category", "$moduleName (${module.category})")
            return false
        }
        try {
            module.toggle()
            val stateStr = if (module.state) "ON" else "OFF"
            ActionLogger.log("toggleModule", "OK: $stateStr", "$moduleName (${module.category})")
            notifyChat("Module $moduleName → $stateStr")
            return true
        } catch (t: Throwable) {
            ActionLogger.log("toggleModule", "FAIL: ${t.message}", moduleName)
            return false
        }
    }

    /**
     * 按住某键 N tick
     *
     * @param key 键码(Keyboard.KEY_* 或特殊鼠标键码)
     * @param ticks 持续 tick 数(被 [AiriSettings.keyHoldMaxTicks] 限制)
     * @param isMouse true 表示这是鼠标按键
     */
    fun holdKey(key: Int, ticks: Int, isMouse: Boolean = false): Boolean {
        if (!checkInteractAllowed("holdKey($key, $ticks, mouse=$isMouse)")) return false
        val limitedTicks = ticks.coerceIn(1, AiriSettings.keyHoldMaxTicks)
        try {
            // 将操作放入队列,在 onPostInput 中执行 (正确的时序)
            synchronized(pendingActions) {
                pendingActions.add(PendingAction(key, isMouse, limitedTicks, isPress = false))
            }
            ActionLogger.log("holdKey", "OK ($limitedTicks ticks)", "key=$key mouse=$isMouse")
            notifyChat("Holding key ${keyName(key, isMouse)} for ${limitedTicks}ticks (${limitedTicks / 20.0}s)")
            return true
        } catch (t: Throwable) {
            ActionLogger.log("holdKey", "FAIL: ${t.message}", "key=$key")
            return false
        }
    }

    /**
     * 短按一次按键 (press + release)
     */
    fun pressKey(key: Int, isMouse: Boolean = false): Boolean {
        if (!checkInteractAllowed("pressKey($key, mouse=$isMouse)")) return false
        try {
            // 将操作放入队列,在 onPostInput 中执行 (正确的时序)
            synchronized(pendingActions) {
                pendingActions.add(PendingAction(key, isMouse, if (isMouse) 1 else 2, isPress = true))
            }
            ActionLogger.log("pressKey", "OK", "key=$key mouse=$isMouse")
            notifyChat("Pressed key ${keyName(key, isMouse)}")
            return true
        } catch (t: Throwable) {
            ActionLogger.log("pressKey", "FAIL: ${t.message}", "key=$key")
            return false
        }
    }

    /**
     * 在 PostInputEvent 中调用 (dispatchKeypresses 之后, sendClickBlockToController 之前)
     *
     * 执行顺序:
     * 1. 执行待处理的按键动作 (pendingActions)
     * 2. 保持 heldKeys 中按键的 pressed 状态
     * 3. 释放到期的按键
     */
    fun onPostInput() {
        // 0. 如果有待处理的动作,先关闭 GUI (在主线程安全执行)
        synchronized(pendingActions) {
            if (pendingActions.isNotEmpty()) {
                closeGuiIfNeeded()
            }
        }

        // 1. 执行待处理的动作 (在 dispatchKeypresses 之后,sendClickBlockToController 之前)
        synchronized(pendingActions) {
            if (pendingActions.isNotEmpty()) {
                LOGGER.info("[Airi] onPostInput: executing ${pendingActions.size} pending action(s), currentScreen=${mc.currentScreen}")
                for (action in pendingActions) {
                    executeKeyAction(action)
                }
                pendingActions.clear()
            }
        }

        // 2. 保持 heldKeys 中的按键状态 & 处理释放
        if (heldKeys.isEmpty()) return
        val toRemove = mutableListOf<Int>()
        heldKeys.forEach { (key, remaining) ->
            // 重新设置 pressed=true,确保按键在当前 tick 生效
            KeyBinding.setKeyBindState(key, true)

            // 对于鼠标攻击键,持续调用 clickMouse (模拟持续按住左键)
            if (key == mc.gameSettings.keyBindAttack.keyCode && remaining > 1) {
                try {
                    clickMouseMethod?.invoke(mc)
                } catch (_: Throwable) {}
            }

            val newRemaining = remaining - 1
            if (newRemaining <= 0) {
                KeyBinding.setKeyBindState(key, false)
                toRemove.add(key)
            } else {
                heldKeys[key] = newRemaining
            }
        }
        toRemove.forEach { heldKeys.remove(it) }
    }

    /**
     * 在 onTick 中执行按键动作
     *
     * 此时 dispatchKeypresses() 已执行完毕,按键状态不会被立即覆盖。
     * 设置 pressed=true 后,后续的 sendClickBlockToController/movementInput
     * 能检测到按键状态。
     */
    private fun executeKeyAction(action: PendingAction) {
        val (key, isMouse, ticks, _) = action
        LOGGER.info("[Airi] Executing key action: key=$key mouse=$isMouse ticks=$ticks")

        if (isMouse) {
            // 鼠标按键: 使用实际 keyBinding 的 keyCode
            val actualKeyCode = when (key) {
                -100 -> mc.gameSettings.keyBindAttack.keyCode
                -99 -> mc.gameSettings.keyBindUseItem.keyCode
                else -> key
            }
            KeyBinding.setKeyBindState(actualKeyCode, true)
            KeyBinding.onTick(actualKeyCode)

            when (key) {
                -100 -> {
                    // 左键攻击: 反射调用 clickMouse + 发送网络包
                    try {
                        clickMouseMethod?.invoke(mc)
                        LOGGER.info("[Airi] clickMouse() invoked")
                    } catch (t: Throwable) {
                        LOGGER.warn("[Airi] clickMouse() failed: ${t.message}")
                    }
                    sendMousePacket(key)
                }
                -99 -> {
                    // 右键使用: 反射调用 rightClickMouse + 发送网络包
                    try {
                        rightClickMouseMethod?.invoke(mc)
                        LOGGER.info("[Airi] rightClickMouse() invoked")
                    } catch (t: Throwable) {
                        LOGGER.warn("[Airi] rightClickMouse() failed: ${t.message}")
                    }
                    sendMousePacket(key)
                }
            }
            heldKeys[actualKeyCode] = ticks
        } else {
            // 普通按键: 直接设置 KeyBinding 状态
            KeyBinding.setKeyBindState(key, true)
            KeyBinding.onTick(key)
            heldKeys[key] = ticks
        }
    }

    /** 立即释放所有按住的键(用于禁用交互模式时) */
    fun releaseAllKeys() {
        heldKeys.keys.forEach { KeyBinding.setKeyBindState(it, false) }
        heldKeys.clear()
        synchronized(pendingActions) { pendingActions.clear() }
    }

    /**
     * 关闭当前 GUI 以让游戏接收按键输入 (在主线程上调用)
     */
    private fun closeGuiIfNeeded() {
        if (mc.currentScreen != null) {
            LOGGER.info("[Airi] Closing GUI for key simulation")
            mc.displayGuiScreen(null)
        }
    }

    /**
     * 直接发送鼠标点击的网络包
     */
    private fun sendMousePacket(key: Int) {
        try {
            val player = mc.thePlayer ?: return
            val conn = player.sendQueue ?: return
            when (key) {
                -100 -> {
                    conn.addToSendQueue(C0APacketAnimation())
                }
                -99 -> {
                    conn.addToSendQueue(C08PacketPlayerBlockPlacement(
                        BlockPos(-1, -1, -1), 255,
                        player.inventory.getCurrentItem(),
                        0f, 0f, 0f
                    ))
                }
            }
        } catch (t: Throwable) {
            LOGGER.warn("[Airi] Failed to send mouse packet: ${t.message}")
        }
    }

    /** 检查交互是否被允许,记录拒绝原因 */
    private fun checkInteractAllowed(operation: String): Boolean {
        if (!AiriSettings.interactionAllowed) {
            ActionLogger.log(operation.substringBefore('('), "BLOCKED: interaction not allowed", operation)
            return false
        }
        if (mc.thePlayer == null && !operation.startsWith("executeCommand")) {
            ActionLogger.log(operation.substringBefore('('), "BLOCKED: no player", operation)
            return false
        }
        return true
    }

    /** 判断是否为破坏性命令 */
    private fun isDestructiveCommand(cmd: String): Boolean {
        val lower = cmd.lowercase().trimStart('.', ' ')
        val destructivePrefixes = listOf(
            "bind", "unbind", "delete", "del", "remove",
            "xray", "nuker", "autobuy",
            "say",
            "irc ", "friend remove"
        )
        return destructivePrefixes.any { lower.startsWith(it) }
    }

    /** 解析按键名转为键码(供 AI 工具调用使用) */
    fun parseKey(keyStr: String): Pair<Int, Boolean>? {
        val lower = keyStr.lowercase()
        when (lower) {
            "lmb", "mouse_left", "mouse0", "attack" -> return Pair(-100, true)
            "rmb", "mouse_right", "mouse1", "use" -> return Pair(-99, true)
            "mmb", "mouse_middle", "mouse2" -> return Pair(-98, true)
        }
        when (lower) {
            "forward", "w" -> return Pair(mc.gameSettings.keyBindForward.keyCode, false)
            "back", "s" -> return Pair(mc.gameSettings.keyBindBack.keyCode, false)
            "left", "a" -> return Pair(mc.gameSettings.keyBindLeft.keyCode, false)
            "right", "d" -> return Pair(mc.gameSettings.keyBindRight.keyCode, false)
            "jump", "space" -> return Pair(mc.gameSettings.keyBindJump.keyCode, false)
            "sneak", "shift" -> return Pair(mc.gameSettings.keyBindSneak.keyCode, false)
            "sprint" -> return Pair(mc.gameSettings.keyBindSprint.keyCode, false)
        }
        return try {
            val field = Keyboard::class.java.getField("KEY_${keyStr.uppercase()}")
            Pair(field.getInt(null) as Int, false)
        } catch (t: Throwable) {
            null
        }
    }

    /** 随机选择一个移动键(W/A/S/D)用于角色扮演的"捣蛋"行为 */
    fun randomMoveKey(): Pair<Int, Boolean> {
        val keys = listOf(
            mc.gameSettings.keyBindForward.keyCode to false,
            mc.gameSettings.keyBindBack.keyCode to false,
            mc.gameSettings.keyBindLeft.keyCode to false,
            mc.gameSettings.keyBindRight.keyCode to false,
            mc.gameSettings.keyBindJump.keyCode to false,
            mc.gameSettings.keyBindSneak.keyCode to false
        )
        return keys.random(Random)
    }

    /** 将键码转为可读名称 (用于聊天通知) */
    private fun keyName(key: Int, isMouse: Boolean): String {
        if (isMouse) {
            return when (key) {
                -100 -> "Mouse Left (Attack)"
                -99 -> "Mouse Right (Use)"
                -98 -> "Mouse Middle"
                else -> "Mouse$key"
            }
        }
        return when (key) {
            mc.gameSettings.keyBindForward.keyCode -> "W"
            mc.gameSettings.keyBindBack.keyCode -> "S"
            mc.gameSettings.keyBindLeft.keyCode -> "A"
            mc.gameSettings.keyBindRight.keyCode -> "D"
            mc.gameSettings.keyBindJump.keyCode -> "Space"
            mc.gameSettings.keyBindSneak.keyCode -> "Shift"
            mc.gameSettings.keyBindSprint.keyCode -> "Sprint"
            else -> Keyboard.getKeyName(key)
        }
    }

    /** 在 Minecraft 聊天栏显示通知 */
    private fun notifyChat(message: String) {
        try {
            val prefix = ChatComponentText("[Airi] ")
            prefix.chatStyle.color = EnumChatFormatting.AQUA
            val body = ChatComponentText(message)
            body.chatStyle.color = EnumChatFormatting.WHITE
            prefix.appendSibling(body)
            mc.ingameGUI?.getChatGUI()?.printChatMessage(prefix)
        } catch (t: Throwable) {
            LOGGER.warn("[Airi] Failed to send chat notification: ${t.message}")
        }
    }
}
