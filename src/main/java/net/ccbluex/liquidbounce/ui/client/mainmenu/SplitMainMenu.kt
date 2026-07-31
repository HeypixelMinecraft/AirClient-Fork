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
 * 分屏对开主菜单。
 *
 * 风格：杂志封面风格，对比强烈，层次分明。
 * 布局：左右 40:60 分屏。左侧深炭块垂直居中放 LOGO 大标题 + 版本 + 装饰横线；右侧暖白磨砂面板垂直居中放右对齐按钮组，每项带描述文字与序号。
 * 配色：左侧深炭 #26282B，右侧暖白 #EDE8DD，强调色 #B5651D（琥珀棕）。
 */
class SplitMainMenu : AbstractScreen() {

    private val timer = MSTimer()

    private data class Entry(val label: String, val desc: String, val action: () -> Unit)

    private val entries = listOf(
        Entry("Singleplayer", "Start your local adventure") { mc.displayGuiScreen(GuiSelectWorld(this)) },
        Entry("Multiplayer", "Connect to online servers") { mc.displayGuiScreen(GuiMultiplayer(this)) },
        Entry("Alt Manager", "Manage your accounts") { mc.displayGuiScreen(GuiAltManager(this)) },
        Entry("Options", "Tune game settings") { mc.displayGuiScreen(GuiOptions(this, mc.gameSettings)) },
        Entry("Mods", "Browse client modules") { mc.displayGuiScreen(GuiModsMenu(this)) },
        Entry("Quit", "Exit the game") { mc.shutdown() }
    )

    private val hoverProgress = FloatArray(entries.size)

    override fun initGui() {
        timer.reset()
    }

    override fun drawScreen(mouseX: Int, mouseY: Int, partialTicks: Float) {
        val sr = ScaledResolution(mc)
        width = sr.scaledWidth
        height = sr.scaledHeight

        // 图片背景
        RenderUtils.drawImage(
            MainMenuStyles.backgroundImage(ClientConfiguration.customMenuBackgroundImageIndex),
            0, 0, width, height
        )

        val splitX = (width * 0.40f).toInt().coerceIn(280, 480)
        val leftBg = Color(38, 40, 43, 235)
        val rightBg = Color(237, 232, 221, 110)
        val accent = Color(181, 101, 29)

        // 左侧深色块
        RenderUtils.drawRect(0f, 0f, splitX.toFloat(), height.toFloat(), leftBg.rgb)
        // 右侧暖白蒙层
        RenderUtils.drawRect(splitX.toFloat(), 0f, width.toFloat(), height.toFloat(), rightBg.rgb)
        // 分割线（强调色）
        RenderUtils.drawRect(
            (splitX - 1).toFloat(), 0f, splitX.toFloat(), height.toFloat(),
            accent.rgb
        )

        val titleFont = Fonts.fontZenDots40
        val bigTitleFont = Fonts.fontBold180
        val labelFont = Fonts.fontSemibold35
        val descFont = Fonts.fontRegular30
        val idxFont = Fonts.fontRegular30
        val labelH = labelFont.fontHeight
        val descH = descFont.fontHeight

        // ===== 左侧：LOGO + 大标题 + 版本 =====
        val leftCx = splitX / 2f
        // 小 LOGO 方块
        val logoSize = 36f
        val logoX = leftCx - logoSize / 2f
        val logoY = height * 0.26f
        RenderUtils.drawRoundedRect(logoX, logoY, logoX + logoSize, logoY + logoSize, accent.rgb, 6f)
        val logoChar = "A"
        Fonts.fontExtraBold40.drawString(
            logoChar, leftCx - Fonts.fontExtraBold40.getStringWidth(logoChar) / 2f,
            logoY + logoSize / 2f - Fonts.fontExtraBold40.fontHeight / 2f + 1f,
            Color(245, 240, 232).rgb, false
        )

        // 大标题：优先用 Bold180，过宽则退回到 titleFont
        val bigTitle = CLIENT_NAME
        val bigW = bigTitleFont.getStringWidth(bigTitle)
        val maxW = splitX - 60f
        val useBig = bigW <= maxW
        val usedFont = if (useBig) bigTitleFont else titleFont
        val usedW = usedFont.getStringWidth(bigTitle)
        val bigX = leftCx - usedW / 2f
        val bigY = logoY + logoSize + 28f

        usedFont.drawString(bigTitle, bigX + 2f, bigY + 2f, Color(0, 0, 0, 60).rgb, false)
        usedFont.drawString(bigTitle, bigX, bigY, Color(238, 232, 222).rgb, false)

        // 装饰横线
        val lineY = bigY + usedFont.fontHeight + 18f
        RenderUtils.drawRect(leftCx - 28f, lineY, leftCx + 28f, lineY + 2f, accent.rgb)

        // 版本副标题
        titleFont.drawCenteredString(
            clientVersionText, leftCx, lineY + 14f,
            Color(180, 176, 168).rgb, false
        )

        // 左侧底部脚注
        val footFont = Fonts.fontRegular30
        footFont.drawCenteredString(
            "Minecraft 1.8.9  •  Forge",
            leftCx, height - 40f,
            Color(130, 126, 118).rgb, false
        )

        // ===== 右侧：按钮组（右对齐） =====
        val rightPad = 50f
        val rightStart = splitX + rightPad
        val rightW = width - splitX - rightPad * 2
        val btnH = 58f
        val btnGap = 10f
        val totalH = entries.size * btnH + (entries.size - 1) * btnGap
        var by = height / 2f - totalH / 2f

        for (i in entries.indices) {
            val e = entries[i]
            val bx = rightStart
            val bw = rightW
            val hovered = mouseX >= bx && mouseX <= bx + bw &&
                    mouseY >= by && mouseY <= by + btnH

            val target = if (hovered) 1f else 0f
            hoverProgress[i] += (target - hoverProgress[i]) * 0.2f
            val p = hoverProgress[i]

            // 悬停背景
            if (p > 0.02f) {
                RenderUtils.drawRoundedRect(
                    bx, by, bx + bw, by + btnH,
                    Color(38, 40, 43, (45 * p).toInt()).rgb, 6f
                )
            }

            // 序号（左侧，垂直居中）
            val idxStr = String.format("%02d", i + 1)
            val idxY = by + (btnH - idxFont.fontHeight) / 2f
            idxFont.drawString(
                idxStr, bx + 6f, idxY,
                mixColor(Color(160, 155, 145), accent, p).rgb, false
            )

            // 标签（右对齐，上方）
            val labelW = labelFont.getStringWidth(e.label)
            val labelY = by + (btnH - labelH - descH - 4f) / 2f
            labelFont.drawString(
                e.label, bx + bw - labelW, labelY,
                mixColor(Color(60, 58, 54), Color(38, 40, 43), p).rgb, false
            )

            // 描述（右对齐，下方，紧跟标签）
            val descW = descFont.getStringWidth(e.desc)
            descFont.drawString(
                e.desc, bx + bw - descW, labelY + labelH + 4f,
                Color(130, 126, 118).rgb, false
            )

            // 悬停时右侧强调竖条
            if (p > 0.05f) {
                RenderUtils.drawRect(
                    bx + bw - 3f * p, by + 10f, bx + bw, by + btnH - 10f,
                    Color(accent.red, accent.green, accent.blue, (220 * p).toInt()).rgb
                )
            }

            by += btnH + btnGap
        }

        // 左下角按钮
        drawCornerButtons(mouseX, mouseY, splitX)

        super.drawScreen(mouseX, mouseY, partialTicks)
    }

    override fun mouseClicked(mouseX: Int, mouseY: Int, mouseButton: Int) {
        if (mouseButton == 0 && timer.hasTimePassed(200)) {
            val splitX = (width * 0.40f).toInt().coerceIn(280, 480)
            val rightPad = 50f
            val rightStart = splitX + rightPad
            val rightW = width - splitX - rightPad * 2
            val btnH = 58f
            val btnGap = 10f
            val totalH = entries.size * btnH + (entries.size - 1) * btnGap
            var by = height / 2f - totalH / 2f

            for (i in entries.indices) {
                val bx = rightStart
                val bw = rightW
                if (mouseX >= bx && mouseX <= bx + bw && mouseY >= by && mouseY <= by + btnH) {
                    entries[i].action()
                    timer.reset()
                    return
                }
                by += btnH + btnGap
            }

            if (handleCornerClick(mouseX, mouseY, splitX)) {
                timer.reset()
                return
            }
        }
        super.mouseClicked(mouseX, mouseY, mouseButton)
    }

    // ===== 左下角按钮 =====

    private fun drawCornerButtons(mouseX: Int, mouseY: Int, splitX: Int) {
        val bgBtnX = 5
        val bgBtnY = height - 50
        val bgBtnW = 90
        val bgBtnH = 20
        val hovBg = mouseX >= bgBtnX && mouseX <= bgBtnX + bgBtnW &&
                mouseY >= bgBtnY && mouseY <= bgBtnY + bgBtnH
        RenderUtils.drawRoundedRect(
            bgBtnX.toFloat(), bgBtnY.toFloat(),
            (bgBtnX + bgBtnW).toFloat(), (bgBtnY + bgBtnH).toFloat(),
            if (hovBg) Color(80, 76, 70, 230).rgb else Color(60, 56, 50, 210).rgb, 3f
        )
        val bgName = MainMenuStyles.backgroundDisplayName(ClientConfiguration.customMenuBackgroundImageIndex)
        Fonts.fontSemibold35.drawCenteredString(
            bgName,
            bgBtnX + bgBtnW / 2f,
            bgBtnY + (bgBtnH - Fonts.fontSemibold35.fontHeight) / 2f,
            Color(220, 216, 208).rgb,
            false
        )

        // 切换风格按钮放在右侧暖白区域左下角
        val swX = splitX + 5
        val swY = height - 25
        val swW = 90
        val swH = 20
        val hovSw = mouseX >= swX && mouseX <= swX + swW &&
                mouseY >= swY && mouseY <= swY + swH
        RenderUtils.drawRoundedRect(
            swX.toFloat(), swY.toFloat(),
            (swX + swW).toFloat(), (swY + swH).toFloat(),
            if (hovSw) Color(181, 101, 29, 230).rgb else Color(150, 85, 25, 200).rgb, 3f
        )
        Fonts.fontSemibold35.drawCenteredString(
            MainMenuStyles.displayName(ClientConfiguration.mainMenuStyle),
            swX + swW / 2f,
            swY + (swH - Fonts.fontSemibold35.fontHeight) / 2f,
            Color(245, 240, 232).rgb,
            false
        )
    }

    private fun handleCornerClick(mouseX: Int, mouseY: Int, splitX: Int): Boolean {
        val bgBtnX = 5; val bgBtnY = height - 50; val bgBtnW = 90; val bgBtnH = 20
        if (mouseX >= bgBtnX && mouseX <= bgBtnX + bgBtnW &&
            mouseY >= bgBtnY && mouseY <= bgBtnY + bgBtnH) {
            ClientConfiguration.customMenuBackgroundImageIndex =
                MainMenuStyles.backgroundImageIndex(ClientConfiguration.customMenuBackgroundImageIndex + 1)
            FileManager.saveConfig(valuesConfig)
            return true
        }

        val swX = splitX + 5; val swY = height - 25; val swW = 90; val swH = 20
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
