/*
 * Air Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 */
// Migrated from Leader-Lite InventoryClicker
package net.ccbluex.liquidbounce.features.module.modules.player

import net.ccbluex.liquidbounce.event.GameTickEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.minecraft.client.gui.inventory.GuiContainer
import org.lwjgl.input.Mouse
import java.lang.reflect.Method

/**
 * InventoryClicker - 在 GUI 容器中按住左键自动连点
 *
 * 迁移自 Leader-Lite InventoryClicker。
 *
 * 当玩家在 GuiContainer 中按住鼠标左键超过 triggerTicks 时，
 * 每个 tick 反射调用 GuiScreen.mouseClicked 模拟一次点击，实现快速移动物品。
 *
 * 注意：原版使用 IAccessorGuiScreen mixin 调用 callMouseClicked，
 * AirClient 无此 mixin，改用反射访问 protected mouseClicked 方法。
 */
object InventoryClicker : Module("InventoryClicker", Category.PLAYER, defaultState = false) {

    private val triggerTicks by int("ticks", 2, 0..20)

    private var ticks = 0

    // 缓存反射方法以避免每次查找
    private var mouseClickedMethod: Method? = null
    private var methodLookupFailed = false

    val onTick = handler<GameTickEvent> {
        if (!state) return@handler
        val player = mc.thePlayer ?: return@handler

        val screen = mc.currentScreen
        if (screen !is GuiContainer) {
            ticks = 0
            return@handler
        }

        val mouseX = Mouse.getEventX() * screen.width / mc.displayWidth
        // 修正原版的 bug: 原代码 mc.displayHeight / mc.displayHeight，这里改为正确公式
        val mouseY = screen.height - Mouse.getEventY() * screen.height / mc.displayHeight - 1

        if (Mouse.isButtonDown(0)) {
            ticks++
            if (ticks > triggerTicks) {
                invokeMouseClicked(screen, mouseX, mouseY, 0)
            }
        } else {
            ticks = 0
        }
    }

    private fun invokeMouseClicked(screen: GuiContainer, mouseX: Int, mouseY: Int, button: Int) {
        if (methodLookupFailed) return
        try {
            val method = mouseClickedMethod ?: run {
                val m = GuiContainer::class.java.superclass
                    .getDeclaredMethod("mouseClicked", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                m.isAccessible = true
                mouseClickedMethod = m
                m
            }
            method.invoke(screen, mouseX, mouseY, button)
        } catch (e: Throwable) {
            // 反射失败则禁用该功能以避免每 tick 报错
            methodLookupFailed = true
            mouseClickedMethod = null
        }
    }

    override fun onDisable() {
        ticks = 0
    }

    override val tag
        get() = "$triggerTicks ticks"
}
