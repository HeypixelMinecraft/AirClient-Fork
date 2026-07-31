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
 * 顶部条形主菜单。
 *
 * 风格：杂志报头 / 高级时装画册风格，极简横线分割，大量留白。
 * 布局：顶部完整横向导航条（左 LOGO，中水平按钮，右版本）；下方大面积展示区显示超大标题与细装饰线。
 * 配色：深紫红 #3D2A35 蒙层，暖灰 #D4C5C9 文字，强调色 #8B6478 玫瑰紫。
 */
class HeaderMainMenu : AbstractScreen() {

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

        // 图片背景 + 深紫红蒙层
        RenderUtils.drawImage(
            MainMenuStyles.backgroundImage(ClientConfiguration.customMenuBackgroundImageIndex),
            0, 0, width, height
        )
        RenderUtils.drawRect(0f, 0f, width.toFloat(), height.toFloat(), Color(48, 32, 42, 160).rgb)

        val accent = Color(139, 100, 120)
        val headerBg = Color(30, 20, 26, 210)
        val headerH = 64

        val titleFont = Fonts.fontBold180
        val labelFont = Fonts.fontSemibold35
        val smallFont = Fonts.fontRegular35
        val descFont = Fonts.fontRegular30

        // ===== 顶部导航条 =====
        RenderUtils.drawRect(0f, 0f, width.toFloat(), headerH.toFloat(), headerBg.rgb)
        // 底部强调线
        RenderUtils.drawRect(0f, headerH.toFloat(), width.toFloat(), (headerH + 1).toFloat(), accent.rgb)

        // 左侧 LOGO
        val padX = 40f
        val logoY = 18f
        RenderUtils.drawRoundedRect(padX, logoY, padX + 28f, logoY + 28f, accent.rgb, 5f)
        val logoChar = "A"
        Fonts.fontExtraBold35.drawString(
            logoChar, padX + 14f - Fonts.fontExtraBold35.getStringWidth(logoChar) / 2f,
            logoY + 14f - Fonts.fontExtraBold35.fontHeight / 2f + 1f,
            Color(245, 240, 232).rgb, false
        )
        labelFont.drawString(CLIENT_NAME, padX + 40f, logoY + 1f, Color(232, 222, 226).rgb, false)
        descFont.drawString("v" + clientVersionText, padX + 40f, logoY + 4f + labelFont.fontHeight, Color(180, 170, 174).rgb, false)

        // 右侧版本信息
        val rightText = "MINECRAFT 1.8.9"
        descFont.drawString(
            rightText, width - padX - descFont.getStringWidth(rightText), logoY + 6f,
            Color(180, 170, 174).rgb, false
        )
        val rightText2 = "FORGE"
        descFont.drawString(
            rightText2, width - padX - descFont.getStringWidth(rightText2), logoY + 6f + descFont.fontHeight + 4f,
            Color(139, 100, 120, 200).rgb, false
        )

        // 中间水平按钮组
        val btnW = 110
        val btnH = 32
        val btnGap = 8
        val totalBtnW = entries.size * btnW + (entries.size - 1) * btnGap
        var bx = width / 2f - totalBtnW / 2f
        val by = (headerH - btnH) / 2f

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
                    Color(139, 100, 120, (60 * p).toInt()).rgb, 4f
                )
            }

            // 文字
            labelFont.drawString(
                e.label, bx + btnW / 2f - labelFont.getStringWidth(e.label) / 2f,
                by + (btnH - labelFont.fontHeight) / 2f,
                mixColor(Color(220, 214, 218), Color(245, 240, 232), p).rgb, false
            )

            // 悬停底部短横线
            if (p > 0.05f) {
                val lineW = 24f * p
                RenderUtils.drawRect(
                    bx + btnW / 2f - lineW / 2f, by + btnH - 4f,
                    bx + btnW / 2f + lineW / 2f, by + btnH - 3f,
                    Color(139, 100, 120, (220 * p).toInt()).rgb
                )
            }

            bx += btnW + btnGap
        }

        // ===== 下方展示区：超大标题居中 + 装饰 =====
        val showY = headerH + (height - headerH) * 0.32f
        val bigTitle = CLIENT_NAME
        val bigW = titleFont.getStringWidth(bigTitle)
        val maxW = width - 120f
        val useBig = bigW <= maxW
        val usedFont = if (useBig) titleFont else labelFont
        val usedW = usedFont.getStringWidth(bigTitle)
        usedFont.drawString(
            bigTitle, width / 2f - usedW / 2f + 3f, showY + 3f,
            Color(0, 0, 0, 50).rgb, false
        )
        usedFont.drawString(
            bigTitle, width / 2f - usedW / 2f, showY,
            Color(232, 222, 226, 220).rgb, false
        )

        // 标题下方装饰：细横线 + 副标题 + 细横线（对称居中）
        val decoY = showY + usedFont.fontHeight + 24f
        val lineLen = 60f
        val subText = "AIR · CLIENT"
        val subW = smallFont.getStringWidth(subText)
        val totalDecoW = lineLen * 2 + 16f + subW
        val decoStart = width / 2f - totalDecoW / 2f
        RenderUtils.drawRect(decoStart, decoY + smallFont.fontHeight / 2f, decoStart + lineLen, decoY + smallFont.fontHeight / 2f + 1f, accent.rgb)
        smallFont.drawString(
            subText, decoStart + lineLen + 8f, decoY,
            Color(180, 170, 174).rgb, false
        )
        RenderUtils.drawRect(decoStart + lineLen + 16f + subW, decoY + smallFont.fontHeight / 2f, decoStart + lineLen * 2 + 16f + subW, decoY + smallFont.fontHeight / 2f + 1f, accent.rgb)

        // 装饰副标题
        descFont.drawCenteredString(
            "An open source mixin-based client", width / 2f, decoY + smallFont.fontHeight + 16f,
            Color(160, 150, 154, 200).rgb, false
        )

        // 左下角装饰：竖向小标记
        val markX = padX
        val markY = height - 80f
        RenderUtils.drawRect(markX, markY, markX + 2f, markY + 40f, Color(139, 100, 120, 180).rgb)
        smallFont.drawString(
            "© AirClient", markX + 10f, markY + 12f,
            Color(160, 150, 154, 200).rgb, false
        )

        // 左下角按钮
        drawCornerButtons(mouseX, mouseY)

        super.drawScreen(mouseX, mouseY, partialTicks)
    }

    override fun mouseClicked(mouseX: Int, mouseY: Int, mouseButton: Int) {
        if (mouseButton == 0 && timer.hasTimePassed(200)) {
            val headerH = 64
            val btnW = 110
            val btnH = 32
            val btnGap = 8
            val totalBtnW = entries.size * btnW + (entries.size - 1) * btnGap
            var bx = width / 2f - totalBtnW / 2f
            val by = (headerH - btnH) / 2f

            for (i in entries.indices) {
                if (mouseX >= bx && mouseX <= bx + btnW && mouseY >= by && mouseY <= by + btnH) {
                    entries[i].action()
                    timer.reset()
                    return
                }
                bx += btnW + btnGap
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
            if (hovBg) Color(60, 44, 52, 230).rgb else Color(40, 28, 36, 210).rgb, 3f
        )
        val bgName = MainMenuStyles.backgroundDisplayName(ClientConfiguration.customMenuBackgroundImageIndex)
        Fonts.fontSemibold35.drawCenteredString(
            bgName,
            bgBtnX + bgBtnW / 2f,
            bgBtnY + (bgBtnH - Fonts.fontSemibold35.fontHeight) / 2f,
            if (hovBg) Color(230, 226, 218).rgb else Color(170, 166, 168).rgb,
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
            if (hovSw) Color(139, 100, 120, 230).rgb else Color(100, 70, 86, 200).rgb, 3f
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
