/*
 * Air Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 */
package net.ccbluex.liquidbounce.ui.client.mainmenu

import net.ccbluex.liquidbounce.LiquidBounce
import net.ccbluex.liquidbounce.LiquidBounce.CLIENT_NAME
import net.ccbluex.liquidbounce.LiquidBounce.clientVersionText
import net.ccbluex.liquidbounce.file.FileManager
import net.ccbluex.liquidbounce.file.FileManager.valuesConfig
import net.ccbluex.liquidbounce.file.configs.models.ClientConfiguration
import net.ccbluex.liquidbounce.ui.client.GuiModsMenu
import net.ccbluex.liquidbounce.ui.client.altmanager.GuiAltManager
import net.ccbluex.liquidbounce.ui.font.Fonts
import net.ccbluex.liquidbounce.utils.render.RenderUtils
import net.ccbluex.liquidbounce.utils.timing.MSTimer
import net.ccbluex.liquidbounce.utils.ui.AbstractScreen
import net.minecraft.client.gui.GuiMultiplayer
import net.minecraft.client.gui.GuiOptions
import net.minecraft.client.gui.GuiSelectWorld
import net.minecraft.client.gui.ScaledResolution
import java.awt.Color

/**
 * 极简居中主菜单。
 *
 * 风格：纸质极简、编辑器式留白美学。
 * 布局：完全居中对称，顶部超大细体衬线标题，中央单列细长按钮，底部小字脚注。
 * 配色：暖白半透明面板，深炭灰文字，悬停浅米色高亮。
 */
class MinimalMainMenu : AbstractScreen() {

    private val timer = MSTimer()

    private data class Entry(val label: String, val action: () -> Unit)

    private val entries = listOf(
        Entry("Singleplayer") { mc.displayGuiScreen(GuiSelectWorld(this)) },
        Entry("Multiplayer") { mc.displayGuiScreen(GuiMultiplayer(this)) },
        Entry("Alt Manager") { mc.displayGuiScreen(GuiAltManager(this)) },
        Entry("Options") { mc.displayGuiScreen(GuiOptions(this, mc.gameSettings)) },
        Entry("Mods") { mc.displayGuiScreen(GuiModsMenu(this)) },
        Entry("Quit") { mc.shutdown() }
    )

    private val hoverProgress = FloatArray(entries.size)

    override fun initGui() {
        timer.reset()
    }

    override fun drawScreen(mouseX: Int, mouseY: Int, partialTicks: Float) {
        val sr = ScaledResolution(mc)
        width = sr.scaledWidth
        height = sr.scaledHeight

        // 图片背景 + 暖白半透明蒙层
        RenderUtils.drawImage(
            MainMenuStyles.backgroundImage(ClientConfiguration.customMenuBackgroundImageIndex),
            0, 0, width, height
        )
        RenderUtils.drawRect(0f, 0f, width.toFloat(), height.toFloat(), Color(245, 242, 235, 150).rgb)

        val titleFont = Fonts.fontNeutonExtraLight40
        val labelFont = Fonts.fontSemibold35
        val smallFont = Fonts.fontRegular35
        val labelH = labelFont.fontHeight
        val smallH = smallFont.fontHeight

        // 顶部标题区
        val titleY = height / 5f
        titleFont.drawCenteredString(
            CLIENT_NAME, width / 2f, titleY, Color(38, 38, 40).rgb, false
        )

        // 标题下方细横线 + 版本号
        val lineY = titleY + titleFont.fontHeight + 14f
        val lineWidth = 60f
        RenderUtils.drawRect(
            width / 2f - lineWidth / 2f, lineY,
            width / 2f + lineWidth / 2f, lineY + 1f,
            Color(180, 170, 150, 160).rgb
        )
        smallFont.drawCenteredString(
            clientVersionText, width / 2f, lineY + 10f,
            Color(120, 116, 108).rgb, false
        )

        // 中央按钮列
        val btnW = 240
        val btnH = 36
        val gap = 8
        val totalH = entries.size * btnH + (entries.size - 1) * gap
        val by0 = height / 2f - totalH / 2f + 40f

        val panelPadX = 28f
        val panelPadY = 20f
        // 整体淡色背板
        RenderUtils.drawRoundedRect(
            width / 2f - btnW / 2f - panelPadX, by0 - panelPadY,
            width / 2f + btnW / 2f + panelPadX, by0 + totalH + panelPadY,
            Color(245, 242, 235, 120).rgb, 8f
        )

        var by = by0
        for (i in entries.indices) {
            val e = entries[i]
            val bx = width / 2f - btnW / 2f
            val hovered = mouseX >= bx && mouseX <= bx + btnW &&
                    mouseY >= by && mouseY <= by + btnH

            val target = if (hovered) 1f else 0f
            hoverProgress[i] += (target - hoverProgress[i]) * 0.18f
            val p = hoverProgress[i]

            // 悬停背景
            val bgAlpha = (110 * p).toInt()
            if (bgAlpha > 1) {
                RenderUtils.drawRoundedRect(
                    bx, by, bx + btnW, by + btnH,
                    Color(232, 224, 205, bgAlpha).rgb, 5f
                )
            }

            // 左侧小圆点（悬停时出现）
            if (p > 0.05f) {
                val dotR = 2.4f * p
                val dotX = bx + 16f
                val dotY = by + btnH / 2f
                RenderUtils.drawRoundedRect(
                    dotX - dotR, dotY - dotR, dotX + dotR, dotY + dotR,
                    Color(150, 110, 70, (220 * p).toInt()).rgb, dotR
                )
            }

            // 文字垂直居中（用 fontHeight）
            val textY = by + (btnH - labelH) / 2f
            val textColor = mixColor(Color(70, 68, 64), Color(40, 38, 34), p)
            labelFont.drawString(
                e.label, bx + 28f, textY, textColor.rgb, false
            )

            // 右侧箭头（悬停时出现）
            if (p > 0.05f) {
                val arrow = "→"
                val aw = labelFont.getStringWidth(arrow)
                labelFont.drawString(
                    arrow, bx + btnW - aw - 16f, textY,
                    Color(150, 110, 70, (220 * p).toInt()).rgb, false
                )
            }

            by += btnH + gap
        }

        // 底部脚注
        val footY = height - 32f
        smallFont.drawCenteredString(
            "© AirClient",
            width / 2f, footY, Color(110, 106, 98).rgb, false
        )

        // 左下角风格切换按钮
        drawCornerButtons(mouseX, mouseY)

        super.drawScreen(mouseX, mouseY, partialTicks)
    }

    override fun mouseClicked(mouseX: Int, mouseY: Int, mouseButton: Int) {
        if (mouseButton == 0 && timer.hasTimePassed(200)) {
            val btnW = 240
            val btnH = 36
            val gap = 8
            val totalH = entries.size * btnH + (entries.size - 1) * gap
            var by = height / 2f - totalH / 2f + 40f
            for (i in entries.indices) {
                val bx = width / 2f - btnW / 2f
                if (mouseX >= bx && mouseX <= bx + btnW && mouseY >= by && mouseY <= by + btnH) {
                    entries[i].action()
                    timer.reset()
                    return
                }
                by += btnH + gap
            }

            if (handleCornerClick(mouseX, mouseY)) {
                timer.reset()
                return
            }
        }

        super.mouseClicked(mouseX, mouseY, mouseButton)
    }

    // ===== 左下角按钮 =====

    private fun drawCornerButtons(mouseX: Int, mouseY: Int) {
        val bgBtnX = 5
        val bgBtnY = height - 50
        val bgBtnW = 90
        val bgBtnH = 20
        val hovBg = mouseX >= bgBtnX && mouseX <= bgBtnX + bgBtnW &&
                mouseY >= bgBtnY && mouseY <= bgBtnY + bgBtnH
        RenderUtils.drawRoundedRect(
            bgBtnX.toFloat(), bgBtnY.toFloat(),
            (bgBtnX + bgBtnW).toFloat(), (bgBtnY + bgBtnH).toFloat(),
            if (hovBg) Color(60, 56, 50, 210).rgb else Color(40, 38, 34, 200).rgb, 3f
        )
        val bgName = MainMenuStyles.backgroundDisplayName(ClientConfiguration.customMenuBackgroundImageIndex)
        Fonts.fontSemibold35.drawCenteredString(
            bgName,
            bgBtnX + bgBtnW / 2f,
            bgBtnY + (bgBtnH - Fonts.fontSemibold35.fontHeight) / 2f,
            if (hovBg) Color(230, 226, 218).rgb else Color(170, 166, 158).rgb,
            false
        )

        val swX = 5
        val swY = height - 25
        val swW = 90
        val swH = 20
        val hovSw = mouseX >= swX && mouseX <= swX + swW &&
                mouseY >= swY && mouseY <= swY + swH
        RenderUtils.drawRoundedRect(
            swX.toFloat(), swY.toFloat(),
            (swX + swW).toFloat(), (swY + swH).toFloat(),
            if (hovSw) Color(60, 56, 50, 210).rgb else Color(40, 38, 34, 200).rgb, 3f
        )
        Fonts.fontSemibold35.drawCenteredString(
            MainMenuStyles.displayName(ClientConfiguration.mainMenuStyle),
            swX + swW / 2f,
            swY + (swH - Fonts.fontSemibold35.fontHeight) / 2f,
            if (hovSw) Color(230, 226, 218).rgb else Color(170, 166, 158).rgb,
            false
        )
    }

    private fun handleCornerClick(mouseX: Int, mouseY: Int): Boolean {
        val bgBtnX = 5; val bgBtnY = height - 50; val bgBtnW = 90; val bgBtnH = 20
        if (mouseX >= bgBtnX && mouseX <= bgBtnX + bgBtnW &&
            mouseY >= bgBtnY && mouseY <= bgBtnY + bgBtnH) {
            ClientConfiguration.customMenuBackgroundImageIndex =
                MainMenuStyles.backgroundImageIndex(ClientConfiguration.customMenuBackgroundImageIndex + 1)
            FileManager.saveConfig(valuesConfig)
            return true
        }

        val swX = 5; val swY = height - 25; val swW = 90; val swH = 20
        if (mouseX >= swX && mouseX <= swX + swW &&
            mouseY >= swY && mouseY <= swY + swH) {
            ClientConfiguration.mainMenuStyle = MainMenuStyles.next(ClientConfiguration.mainMenuStyle)
            FileManager.saveConfig(valuesConfig)
            mc.displayGuiScreen(MainMenuStyles.createScreen(ClientConfiguration.mainMenuStyle) as net.minecraft.client.gui.GuiScreen)
            return true
        }
        return false
    }

    private fun mixColor(a: Color, b: Color, t: Float): Color {
        val tt = t.coerceIn(0f, 1f)
        return Color(
            (a.red + (b.red - a.red) * tt).toInt(),
            (a.green + (b.green - a.green) * tt).toInt(),
            (a.blue + (b.blue - a.blue) * tt).toInt()
        )
    }
}
