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
import net.minecraft.util.ResourceLocation
import java.awt.Color

/**
 * 底部 Dock 主菜单。
 *
 * 风格：macOS 风格 Dock，简洁现代，呼吸感强。
 * 布局：上方大面积留白显示左对齐超大标题与版本副标题；底部居中胶囊 Dock 水平排列圆形图标按钮，悬停放大并显示文字提示。
 * 配色：深石板渐变背景蒙层，Dock 浅米色半透明 #E8E2D5，图标文字深炭 #2A2A2A，强调色 #6E8B8B（青灰）。
 */
class DockMainMenu : AbstractScreen() {

    private val timer = MSTimer()

    private data class DockItem(val icon: ResourceLocation, val label: String, val action: () -> Unit)

    private val items = listOf(
        DockItem(ResourceLocation("airclient/watermark_images/user3.png"), "Singleplayer") { mc.displayGuiScreen(GuiSelectWorld(this)) },
        DockItem(ResourceLocation("airclient/watermark_images/ping2.png"), "Multiplayer") { mc.displayGuiScreen(GuiMultiplayer(this)) },
        DockItem(ResourceLocation("airclient/watermark_images/fps.png"), "Alt Manager") { mc.displayGuiScreen(GuiAltManager(this)) },
        DockItem(ResourceLocation("airclient/clickgui/setting.png"), "Options") { mc.displayGuiScreen(GuiOptions(this, mc.gameSettings)) },
        DockItem(ResourceLocation("airclient/clickgui/folder.png"), "Mods") { mc.displayGuiScreen(GuiModsMenu(this)) },
        DockItem(ResourceLocation("airclient/clickgui/close.png"), "Quit") { mc.shutdown() }
    )

    private val hoverProgress = FloatArray(items.size)
    private var hoveredIndex = -1

    override fun initGui() {
        timer.reset()
    }

    override fun drawScreen(mouseX: Int, mouseY: Int, partialTicks: Float) {
        val sr = ScaledResolution(mc)
        width = sr.scaledWidth
        height = sr.scaledHeight

        // 图片背景 + 深色渐变蒙层
        RenderUtils.drawImage(
            MainMenuStyles.backgroundImage(ClientConfiguration.customMenuBackgroundImageIndex),
            0, 0, width, height
        )
        RenderUtils.drawGradientRect(
            0, 0, width, height,
            Color(20, 22, 26, 120).rgb,
            Color(20, 22, 26, 180).rgb,
            0f
        )

        val titleFont = Fonts.fontBold180
        val subFont = Fonts.fontSemibold40
        val smallFont = Fonts.fontRegular35
        val tipFont = Fonts.fontSemibold35

        // ===== 上方大标题区（左对齐偏上） =====
        val padX = 50f
        val titleY = height * 0.16f
        titleFont.drawString(CLIENT_NAME, padX, titleY, Color(245, 240, 232).rgb, false)
        // 标题下方强调短横线
        val lineY = titleY + titleFont.fontHeight + 14f
        RenderUtils.drawRect(padX, lineY, padX + 54f, lineY + 2f, Color(110, 139, 139).rgb)
        // 副标题
        subFont.drawString(
            "Minecraft 1.8.9", padX, lineY + 12f,
            Color(190, 186, 178).rgb, false
        )
        smallFont.drawString(
            clientVersionText, padX, lineY + 12f + subFont.fontHeight + 4f,
            Color(140, 136, 128).rgb, false
        )

        // ===== 底部 Dock =====
        val dockItemSize = 56f
        val dockGap = 16f
        val dockPadX = 26f
        val dockPadY = 16f
        val tipSpace = 28f

        val dockContentW = items.size * dockItemSize + (items.size - 1) * dockGap
        val dockW = dockContentW + dockPadX * 2
        val dockH = dockItemSize + dockPadY * 2 + tipSpace
        val dockX = width / 2f - dockW / 2f
        val dockY = height - dockH - 28f

        // 计算悬停索引
        hoveredIndex = -1
        var ix = dockX + dockPadX
        val iy = dockY + dockPadY + tipSpace
        for (i in items.indices) {
            if (mouseX >= ix && mouseX <= ix + dockItemSize &&
                mouseY >= iy && mouseY <= iy + dockItemSize) {
                hoveredIndex = i
            }
            ix += dockItemSize + dockGap
        }

        // 绘制 dock 项
        ix = dockX + dockPadX
        for (i in items.indices) {
            val it = items[i]
            val hovered = i == hoveredIndex

            val target = if (hovered) 1f else 0f
            hoverProgress[i] += (target - hoverProgress[i]) * 0.22f
            val p = hoverProgress[i]

            val scale = 1f + 0.22f * p
            val size = dockItemSize * scale
            val cx = ix + dockItemSize / 2f
            val cy = iy + dockItemSize / 2f
            val halfS = size / 2f

            // 提示文字（在 dock 项上方）
            if (p > 0.1f) {
                val tipW = tipFont.getStringWidth(it.label)
                val tipX = cx - tipW / 2f
                val tipY = iy - tipFont.fontHeight - 8f
                RenderUtils.drawRoundedRect(
                    tipX - 8f, tipY - 3f, tipX + tipW + 8f, tipY + tipFont.fontHeight + 3f,
                    Color(40, 38, 34, (220 * p).toInt()).rgb, 5f
                )
                tipFont.drawString(
                    it.label, tipX, tipY,
                    Color(245, 240, 232, (255 * p).toInt()).rgb, false
                )
            }

            // 图标方块
            val iconColor = if (hovered) Color(110, 139, 139) else Color(70, 72, 76)
            RenderUtils.drawRoundedRect(
                cx - halfS, cy - halfS, cx + halfS, cy + halfS,
                iconColor.rgb, 12f * scale
            )
            // 图片图标（居中绘制）
            val iconSize = 30
            RenderUtils.drawImage(
                it.icon,
                (cx - iconSize / 2f).toInt(),
                (cy - iconSize / 2f).toInt(),
                iconSize,
                iconSize,
                Color(245, 240, 232)
            )

            // 悬停时底部小圆点指示
            if (p > 0.1f) {
                val dotR = 2.2f
                RenderUtils.drawRoundedRect(
                    cx - dotR, iy + dockItemSize + 8f,
                    cx + dotR, iy + dockItemSize + 8f + dotR * 2f,
                    Color(110, 139, 139, (220 * p).toInt()).rgb, dotR
                )
            }

            ix += dockItemSize + dockGap
        }

        // 左下角按钮
        drawCornerButtons(mouseX, mouseY)

        super.drawScreen(mouseX, mouseY, partialTicks)
    }

    override fun mouseClicked(mouseX: Int, mouseY: Int, mouseButton: Int) {
        if (mouseButton == 0 && timer.hasTimePassed(200)) {
            val dockItemSize = 56f
            val dockGap = 16f
            val dockPadX = 26f
            val dockPadY = 16f
            val tipSpace = 28f
            val dockContentW = items.size * dockItemSize + (items.size - 1) * dockGap
            val dockW = dockContentW + dockPadX * 2
            val dockH = dockItemSize + dockPadY * 2 + tipSpace
            val dockX = width / 2f - dockW / 2f
            val dockY = height - dockH - 28f
            val iy = dockY + dockPadY + tipSpace

            var ix = dockX + dockPadX
            for (i in items.indices) {
                val hitPad = 6f
                if (mouseX >= ix - hitPad && mouseX <= ix + dockItemSize + hitPad &&
                    mouseY >= iy - hitPad && mouseY <= iy + dockItemSize + hitPad) {
                    items[i].action()
                    timer.reset()
                    return
                }
                ix += dockItemSize + dockGap
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
}
