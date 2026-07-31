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
import java.awt.Color

/**
 * 网格卡片主菜单。
 *
 * 风格：建筑制图 / 蓝图风格，精确几何对齐，科技感来自网格秩序。
 * 布局：顶部左对齐 LOGO + 标题；中央 2x3 卡片网格，每张卡片独立成块含序号/标签/描述；右下角装饰坐标。
 * 配色：深蓝灰 #2B3A4A 蒙层，香槟金 #C9A961 强调，卡片半透明深底 + 细金线。
 */
class GridMainMenu : AbstractScreen() {

    private val timer = MSTimer()

    private data class Entry(val label: String, val desc: String, val action: () -> Unit)

    private val entries = listOf(
        Entry("Singleplayer", "Local world") { mc.displayGuiScreen(GuiSelectWorld(this)) },
        Entry("Multiplayer", "Join server") { mc.displayGuiScreen(GuiMultiplayer(this)) },
        Entry("Alt Manager", "Accounts") { mc.displayGuiScreen(GuiAltManager(this)) },
        Entry("Options", "Settings") { mc.displayGuiScreen(GuiOptions(this, mc.gameSettings)) },
        Entry("Mods", "Modules") { mc.displayGuiScreen(GuiModsMenu(this)) },
        Entry("Quit", "Exit") { mc.shutdown() }
    )

    private val hoverProgress = FloatArray(entries.size)

    override fun initGui() {
        timer.reset()
    }

    override fun drawScreen(mouseX: Int, mouseY: Int, partialTicks: Float) {
        val sr = ScaledResolution(mc)
        width = sr.scaledWidth
        height = sr.scaledHeight

        // 图片背景 + 深蓝灰蒙层
        RenderUtils.drawImage(
            MainMenuStyles.backgroundImage(ClientConfiguration.customMenuBackgroundImageIndex),
            0, 0, width, height
        )
        RenderUtils.drawRect(0f, 0f, width.toFloat(), height.toFloat(), Color(27, 36, 46, 165).rgb)

        val accent = Color(201, 169, 97)
        val cardBg = Color(20, 28, 36, 200)
        val cardBgHover = Color(40, 54, 68, 230)
        val gridLineColor = Color(201, 169, 97, 40)

        val titleFont = Fonts.fontBold180
        val labelFont = Fonts.fontSemibold35
        val descFont = Fonts.fontRegular30
        val idxFont = Fonts.fontRegular30
        val smallFont = Fonts.fontRegular35

        // ===== 顶部 LOGO + 标题（左对齐） =====
        val padX = 50f
        val topY = 42f
        // 小方块 LOGO
        RenderUtils.drawRoundedRect(padX, topY, padX + 28f, topY + 28f, accent.rgb, 5f)
        val logoChar = "A"
        Fonts.fontExtraBold35.drawString(
            logoChar, padX + 14f - Fonts.fontExtraBold35.getStringWidth(logoChar) / 2f,
            topY + 14f - Fonts.fontExtraBold35.fontHeight / 2f + 1f,
            Color(245, 240, 232).rgb, false
        )
        labelFont.drawString(CLIENT_NAME, padX + 40f, topY + 1f, Color(238, 232, 222).rgb, false)
        descFont.drawString(clientVersionText, padX + 40f, topY + 4f + labelFont.fontHeight, Color(170, 166, 158).rgb, false)

        // 顶部右侧装饰：坐标式文字 + 细横线
        val coordText = "N 00°00'  E 00°00'"
        val coordW = descFont.getStringWidth(coordText)
        descFont.drawString(
            coordText, width - padX - coordW, topY + 6f,
            Color(201, 169, 97, 180).rgb, false
        )
        RenderUtils.drawRect(width - padX - coordW, topY + 6f + descFont.fontHeight + 6f, width - padX, topY + 6f + descFont.fontHeight + 7f, gridLineColor.rgb)

        // ===== 中央 2x3 网格 =====
        val cols = 3
        val rows = 2
        val cardW = 180f
        val cardH = 110f
        val gapX = 18f
        val gapY = 18f
        val gridW = cols * cardW + (cols - 1) * gapX
        val gridH = rows * cardH + (rows - 1) * gapY
        val gridX0 = width / 2f - gridW / 2f
        val gridY0 = height / 2f - gridH / 2f + 20f

        // 网格背景细线（科技感）
        for (c in 0..cols) {
            val lx = gridX0 + c * (cardW + gapX) - gapX / 2f
            RenderUtils.drawRect(lx, gridY0 - 30f, lx + 1f, gridY0 + gridH + 30f, gridLineColor.rgb)
        }

        for (i in entries.indices) {
            val r = i / cols
            val c = i % cols
            val cx = gridX0 + c * (cardW + gapX)
            val cy = gridY0 + r * (cardH + gapY)
            val hovered = mouseX >= cx && mouseX <= cx + cardW &&
                    mouseY >= cy && mouseY <= cy + cardH

            val target = if (hovered) 1f else 0f
            hoverProgress[i] += (target - hoverProgress[i]) * 0.2f
            val p = hoverProgress[i]

            // 卡片背景
            val bg = mixColor(cardBg, cardBgHover, p)
            RenderUtils.drawRoundedRect(cx, cy, cx + cardW, cy + cardH, bg.rgb, 6f)

            // 卡片细金线边框（悬停时增强）
            val borderAlpha = (60 + 160 * p).toInt()
            drawCardBorder(cx, cy, cx + cardW, cy + cardH, Color(accent.red, accent.green, accent.blue, borderAlpha).rgb, 6f, p)

            // 左上角序号
            val idxStr = String.format("%02d", i + 1)
            idxFont.drawString(
                idxStr, cx + 14f, cy + 14f,
                mixColor(Color(160, 155, 145), accent, p).rgb, false
            )
            // 序号下方短横线
            RenderUtils.drawRect(cx + 14f, cy + 14f + idxFont.fontHeight + 6f, cx + 30f, cy + 14f + idxFont.fontHeight + 7f,
                Color(accent.red, accent.green, accent.blue, (120 + 100 * p).toInt()).rgb)

            // 标签（左下）
            labelFont.drawString(
                entries[i].label, cx + 14f, cy + cardH - 14f - labelFont.fontHeight - descFont.fontHeight - 4f,
                mixColor(Color(220, 214, 204), Color(245, 240, 232), p).rgb, false
            )
            // 描述
            descFont.drawString(
                entries[i].desc, cx + 14f, cy + cardH - 14f - descFont.fontHeight,
                Color(150, 146, 138).rgb, false
            )

            // 悬停时右上角小方块标记
            if (p > 0.05f) {
                val ms = 6f
                RenderUtils.drawRoundedRect(
                    cx + cardW - 14f - ms, cy + 14f,
                    cx + cardW - 14f, cy + 14f + ms,
                    Color(accent.red, accent.green, accent.blue, (220 * p).toInt()).rgb, 2f
                )
            }
        }

        // 右下角装饰坐标
        smallFont.drawString(
            "© AirClient",
            width - padX - smallFont.getStringWidth("© AirClient"),
            height - 32f,
            Color(201, 169, 97, 160).rgb, false
        )

        // 左下角按钮
        drawCornerButtons(mouseX, mouseY)

        super.drawScreen(mouseX, mouseY, partialTicks)
    }

    private fun drawCardBorder(x1: Float, y1: Float, x2: Float, y2: Float, color: Int, radius: Float, p: Float) {
        if (p < 0.02f) return
        RenderUtils.drawRoundedRect(x1, y1, x2, y2, color, radius)
    }

    override fun mouseClicked(mouseX: Int, mouseY: Int, mouseButton: Int) {
        if (mouseButton == 0 && timer.hasTimePassed(200)) {
            val cols = 3
            val cardW = 180f
            val cardH = 110f
            val gapX = 18f
            val gapY = 18f
            val gridW = cols * cardW + (cols - 1) * gapX
            val gridH = 2 * cardH + (2 - 1) * gapY
            val gridX0 = width / 2f - gridW / 2f
            val gridY0 = height / 2f - gridH / 2f + 20f

            for (i in entries.indices) {
                val r = i / cols
                val c = i % cols
                val cx = gridX0 + c * (cardW + gapX)
                val cy = gridY0 + r * (cardH + gapY)
                if (mouseX >= cx && mouseX <= cx + cardW && mouseY >= cy && mouseY <= cy + cardH) {
                    entries[i].action()
                    timer.reset()
                    return
                }
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
            if (hovBg) Color(40, 54, 68, 230).rgb else Color(25, 35, 45, 210).rgb, 3f
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
            if (hovSw) Color(201, 169, 97, 230).rgb else Color(150, 120, 60, 200).rgb, 3f
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
