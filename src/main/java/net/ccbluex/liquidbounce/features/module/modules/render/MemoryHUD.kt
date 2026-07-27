/*
 * Air Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 */
// skid some
package net.ccbluex.liquidbounce.features.module.modules.render

import net.ccbluex.liquidbounce.event.Render2DEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.ui.font.Fonts
import net.ccbluex.liquidbounce.utils.render.RenderUtils
import net.minecraft.client.gui.ScaledResolution
import org.lwjgl.opengl.GL11.*
import java.awt.Color

/**
 * Displays memory usage information on HUD.
 * Shows used/total/max memory and a usage bar.
 */
object MemoryHUD : Module("MemoryHUD", Category.RENDER, gameDetecting = false) {
    private val posX by float("X", 0.01f, 0f..1f)
    private val posY by float("Y", 0.85f, 0f..1f)
    private val backgroundAlpha by int("Alpha", 150, 0..255)
    private val scale by float("Scale", 1f, 0.5f..2f)
    private val showBar by boolean("ShowBar", true)
    private val barWidth by int("BarWidth", 100, 50..200) { showBar }
    private val barHeight by int("BarHeight", 6, 2..15) { showBar }

    val onRender2D = handler<Render2DEvent> {
        val sr = ScaledResolution(mc)
        val font = Fonts.fontRegular35

        val runtime = Runtime.getRuntime()
        val usedMemory = runtime.totalMemory() - runtime.freeMemory()
        val totalMemory = runtime.totalMemory()
        val maxMemory = runtime.maxMemory()

        val usedMB = usedMemory / (1024 * 1024)
        val totalMB = totalMemory / (1024 * 1024)
        val maxMB = maxMemory / (1024 * 1024)
        val usagePercent = (usedMemory.toDouble() / maxMemory.toDouble())

        val x = sr.scaledWidth * posX
        val y = sr.scaledHeight * posY

        glPushMatrix()
        glScalef(scale, scale, 1f)

        val drawX = x / scale
        val drawY = y / scale

        // Text
        val memoryText = "Memory: ${usedMB}MB / ${maxMB}MB"
        val usageText = String.format("(%.1f%%)", usagePercent * 100)
        val fullText = "$memoryText $usageText"

        val textWidth = font.getStringWidth(fullText) + 10f
        val totalHeight = if (showBar) font.FONT_HEIGHT + barHeight + 8f else font.FONT_HEIGHT + 6f
        val bgWidth = if (showBar) maxOf(textWidth, barWidth.toFloat() + 10f) else textWidth

        // Background
        RenderUtils.drawRect(drawX, drawY, drawX + bgWidth, drawY + totalHeight,
            Color(0, 0, 0, backgroundAlpha).rgb)

        // Memory text
        font.drawString(memoryText, drawX + 5, drawY + 3, Color.WHITE.rgb)
        val usageColor = when {
            usagePercent > 0.85 -> Color(255, 80, 80)
            usagePercent > 0.65 -> Color(255, 200, 80)
            else -> Color(150, 255, 150)
        }
        font.drawString(usageText, drawX + 5 + font.getStringWidth(memoryText) + 3, drawY + 3, usageColor.rgb)

        // Usage bar
        if (showBar) {
            val barX = drawX + 5
            val barY = drawY + font.FONT_HEIGHT + 5
            val bw = barWidth.toFloat()
            val bh = barHeight.toFloat()

            // Bar background
            RenderUtils.drawRect(barX, barY, barX + bw, barY + bh,
                Color(50, 50, 50, 200).rgb)

            // Bar fill
            val fillWidth = (usagePercent * bw).toFloat().coerceIn(0f, bw)
            if (fillWidth > 0) {
                RenderUtils.drawRect(barX, barY, barX + fillWidth, barY + bh,
                    usageColor.rgb)
            }
        }

        glPopMatrix()
    }

    override val tag: String?
        get() {
            val runtime = Runtime.getRuntime()
            val usedMB = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
            return "${usedMB}MB"
        }
}
