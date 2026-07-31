/*
 * Air Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 */
package net.ccbluex.liquidbounce.ui.client.mainmenu

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
import org.lwjgl.opengl.GL11
import java.awt.Color

/**
 * 对角线主菜单。
 *
 * 风格：复古画册 / 皮革质感美学，非对称对角分布，沉静优雅。
 * 布局：左上 LOGO + 标题；右下垂直按钮组（右对齐）；中间倾斜大字水印 + 对角细斜线装饰。
 * 配色：深棕 #3D2E26 蒙层，奶白 #EDE0D4 文字，强调色 #A0784F 暖棕。
 */
class DiagonalMainMenu : AbstractScreen() {

    private val timer = MSTimer()

    private data class Entry(val label: String, val desc: String, val action: () -> Unit)

    private val entries = listOf(
        Entry("Singleplayer", "Local adventure") { mc.displayGuiScreen(GuiSelectWorld(this)) },
        Entry("Multiplayer", "Online servers") { mc.displayGuiScreen(GuiMultiplayer(this)) },
        Entry("Alt Manager", "Accounts") { mc.displayGuiScreen(GuiAltManager(this)) },
        Entry("Options", "Settings") { mc.displayGuiScreen(GuiOptions(this, mc.gameSettings)) },
        Entry("Mods", "Modules") { mc.displayGuiScreen(GuiModsMenu(this)) },
        Entry("Quit", "Exit game") { mc.shutdown() }
    )

    private val hoverProgress = FloatArray(entries.size)

    override fun initGui() {
        timer.reset()
    }

    override fun drawScreen(mouseX: Int, mouseY: Int, partialTicks: Float) {
        val sr = ScaledResolution(mc)
        width = sr.scaledWidth
        height = sr.scaledHeight

        // 图片背景 + 深棕蒙层
        RenderUtils.drawImage(
            MainMenuStyles.backgroundImage(ClientConfiguration.customMenuBackgroundImageIndex),
            0, 0, width, height
        )
        RenderUtils.drawRect(0f, 0f, width.toFloat(), height.toFloat(), Color(50, 38, 32, 165).rgb)

        val accent = Color(160, 120, 79)
        val lineColor = Color(160, 120, 79, 70)

        val titleFont = Fonts.fontBold180
        val labelFont = Fonts.fontSemibold35
        val smallFont = Fonts.fontRegular35
        val descFont = Fonts.fontRegular30
        val idxFont = Fonts.fontRegular30

        // ===== 左上：LOGO + 标题 =====
        val padX = 50f
        val topY = 46f
        RenderUtils.drawRoundedRect(padX, topY, padX + 30f, topY + 30f, accent.rgb, 5f)
        val logoChar = "A"
        Fonts.fontExtraBold40.drawString(
            logoChar, padX + 15f - Fonts.fontExtraBold40.getStringWidth(logoChar) / 2f,
            topY + 15f - Fonts.fontExtraBold40.fontHeight / 2f + 1f,
            Color(245, 240, 232).rgb, false
        )
        labelFont.drawString(CLIENT_NAME, padX + 42f, topY + 1f, Color(237, 224, 212).rgb, false)
        descFont.drawString(clientVersionText, padX + 42f, topY + 4f + labelFont.fontHeight, Color(170, 158, 146).rgb, false)

        // 左上 LOGO 下方装饰：短横线 + 小字
        val decoY1 = topY + 50f
        RenderUtils.drawRect(padX, decoY1, padX + 36f, decoY1 + 1f, accent.rgb)
        smallFont.drawString(
            "Air · 2026", padX, decoY1 + 8f,
            Color(160, 120, 79, 200).rgb, false
        )

        // ===== 中间：倾斜大字水印 + 对角斜线 =====
        GL11.glPushMatrix()
        val centerX = width / 2f
        val centerY = height / 2f
        GL11.glTranslatef(centerX, centerY, 0f)
        GL11.glRotatef(-18f, 0f, 0f, 1f)

        val bigTitle = CLIENT_NAME
        val bigW = titleFont.getStringWidth(bigTitle)
        titleFont.drawString(
            bigTitle, -bigW / 2f + 4f, -titleFont.fontHeight / 2f + 4f,
            Color(0, 0, 0, 40).rgb, false
        )
        titleFont.drawString(
            bigTitle, -bigW / 2f, -titleFont.fontHeight / 2f,
            Color(160, 120, 79, 55).rgb, false
        )
        GL11.glPopMatrix()

        // 对角细斜线装饰（左下到右上若干平行线）
        for (i in -3..3) {
            val offset = i * 24f
            drawDiagonalLine(
                60f, height - 120f + offset,
                width - 60f, 120f + offset,
                lineColor.rgb
            )
        }

        // ===== 右下：垂直按钮组（右对齐） =====
        val btnW = 240f
        val btnH = 44f
        val btnGap = 8f
        val totalH = entries.size * btnH + (entries.size - 1) * btnGap
        val rightPad = 50f
        val bx = width - rightPad - btnW
        var by = height - 80f - totalH

        for (i in entries.indices) {
            val e = entries[i]
            val hovered = mouseX >= bx && mouseX <= bx + btnW &&
                    mouseY >= by && mouseY <= by + btnH

            val target = if (hovered) 1f else 0f
            hoverProgress[i] += (target - hoverProgress[i]) * 0.2f
            val p = hoverProgress[i]

            // 悬停背景
            if (p > 0.02f) {
                RenderUtils.drawRoundedRect(
                    bx, by, bx + btnW, by + btnH,
                    Color(245, 232, 218, (40 * p).toInt()).rgb, 5f
                )
            }

            // 右侧强调竖条（悬停时）
            if (p > 0.05f) {
                RenderUtils.drawRect(
                    bx + btnW - 3f * p, by + 8f, bx + btnW, by + btnH - 8f,
                    Color(accent.red, accent.green, accent.blue, (220 * p).toInt()).rgb
                )
            }

            // 序号（左侧，垂直居中）
            val idxStr = String.format("%02d", i + 1)
            idxFont.drawString(
                idxStr, bx + 12f, by + (btnH - idxFont.fontHeight) / 2f,
                mixColor(Color(150, 138, 126), accent, p).rgb, false
            )
            // 序号右侧分隔竖线
            RenderUtils.drawRect(bx + 40f, by + 10f, bx + 41f, by + btnH - 10f, Color(160, 120, 79, 60).rgb)

            // 标签（左对齐，上方）
            labelFont.drawString(
                e.label, bx + 50f, by + (btnH - labelFont.fontHeight - descFont.fontHeight - 2f) / 2f,
                mixColor(Color(225, 218, 206), Color(248, 242, 232), p).rgb, false
            )
            // 描述（左对齐，下方）
            descFont.drawString(
                e.desc, bx + 50f, by + (btnH - labelFont.fontHeight - descFont.fontHeight - 2f) / 2f + labelFont.fontHeight + 2f,
                Color(150, 142, 132).rgb, false
            )

            by += btnH + btnGap
        }

        // 右下角装饰小字
        smallFont.drawString(
            "© AirClient",
            width - rightPad - smallFont.getStringWidth("© AirClient"),
            height - 32f,
            Color(160, 120, 79, 160).rgb, false
        )

        // 左下角按钮
        drawCornerButtons(mouseX, mouseY)

        super.drawScreen(mouseX, mouseY, partialTicks)
    }

    private fun drawDiagonalLine(x1: Float, y1: Float, x2: Float, y2: Float, color: Int) {
        val steps = 40
        val dx = (x2 - x1) / steps
        val dy = (y2 - y1) / steps
        for (i in 0 until steps) {
            val sx = x1 + dx * i
            val sy = y1 + dy * i
            RenderUtils.drawRect(sx, sy, sx + 1.5f, sy + 1.5f, color)
        }
    }

    override fun mouseClicked(mouseX: Int, mouseY: Int, mouseButton: Int) {
        if (mouseButton == 0 && timer.hasTimePassed(200)) {
            val btnW = 240f
            val btnH = 44f
            val btnGap = 8f
            val totalH = entries.size * btnH + (entries.size - 1) * btnGap
            val rightPad = 50f
            val bx = width - rightPad - btnW
            var by = height - 80f - totalH

            for (i in entries.indices) {
                if (mouseX >= bx && mouseX <= bx + btnW && mouseY >= by && mouseY <= by + btnH) {
                    entries[i].action()
                    timer.reset()
                    return
                }
                by += btnH + btnGap
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
            if (hovBg) Color(66, 50, 42, 230).rgb else Color(45, 34, 28, 210).rgb, 3f
        )
        val bgName = MainMenuStyles.backgroundDisplayName(ClientConfiguration.customMenuBackgroundImageIndex)
        Fonts.fontSemibold35.drawCenteredString(
            bgName,
            bgBtnX + bgBtnW / 2f,
            bgBtnY + (bgBtnH - Fonts.fontSemibold35.fontHeight) / 2f,
            if (hovBg) Color(237, 224, 212).rgb else Color(170, 158, 146).rgb,
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
            if (hovSw) Color(160, 120, 79, 230).rgb else Color(120, 88, 56, 200).rgb, 3f
        )
        Fonts.fontSemibold35.drawCenteredString(
            MainMenuStyles.displayName(ClientConfiguration.mainMenuStyle),
            swX + swW / 2f,
            swY + (swH - Fonts.fontSemibold35.fontHeight) / 2f,
            Color(245, 240, 232).rgb,
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
