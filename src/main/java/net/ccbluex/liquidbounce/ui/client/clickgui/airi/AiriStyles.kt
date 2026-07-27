// skid AIRI
/*
 * AirClient Hacked Client
 * A free open source mixin-based injection hacked client built on Liquidbounce legacy codebase.
 */
package net.ccbluex.liquidbounce.ui.client.clickgui.airi

import net.ccbluex.liquidbounce.features.module.modules.client.ClickGUI
import net.ccbluex.liquidbounce.ui.font.Fonts
import net.ccbluex.liquidbounce.ui.font.GameFontRenderer
import java.awt.Color

/**
 * Airi GUI 样式配置
 *
 * 3 种样式 (均为 SIDEBAR 布局: 左固定侧边栏 + 右聊天区):
 *   - Minimal:     极简扁平,细线分隔
 *   - Card:        卡片式,glow+shadow
 *   - Glass:       玻璃质感,blur+大圆角
 */
data class AiriStyleConfig(
    val name: String,
    // ===== 颜色 =====
    val bgColor: Color,
    val sidebarColor: Color,
    val aiBubbleColor: Color,
    val userBubbleColor: Color,
    val inputBgColor: Color,
    val controlBgColor: Color,
    val textColor: Color,
    val mutedColor: Color,
    // ===== 字体 =====
    val titleFont: GameFontRenderer,
    val bodyFont: GameFontRenderer,
    val titleScale: Float,
    // ===== 圆角 =====
    val cornerRadius: Float,
    val bubbleRadius: Float,
    val controlRadius: Float,
    // ===== 效果开关 =====
    val useGlow: Boolean,
    val forceBlur: Boolean,
    val lightTheme: Boolean,
    val useShadow: Boolean,
    // ===== 布局差异化字段 =====
    val useBorder: Boolean,
    val borderColor: Color,
    val sidebarSeparator: Boolean,
    val bubbleBorder: Boolean,
    val showTag: Boolean,
    val bubbleGap: Float,
    val bubblePaddingX: Float,
    val bubblePaddingTop: Float,
    val headerSeparator: Boolean,
    val contentMargin: Float,
    // ===== 兼容字段 (默认值不激活) =====
    val fullWidthBubbles: Boolean,
    val bubbleLeftBorder: Boolean,
    val leftBorderColor: Color,
    val transparentSidebar: Boolean,
    val centerTitle: Boolean,
    val bubbleNoFill: Boolean,
    val bubbleAlphaScale: Float,
    // ===== 区域高度 =====
    val headerHeight: Float,
    val controlBarHeight: Float,
    val inputHeight: Float
) {
    /** 获取实际 accent 色 */
    fun resolveAccent(): Color = if (lightTheme) Color(125, 75, 40) else ClickGUI.nlAccentColor

    companion object {
        /** Minimal: 极简扁平,细线分隔,无标签,紧凑 */
        val MINIMAL = AiriStyleConfig(
            name = "Minimal",
            bgColor = Color(18, 20, 28, 235),
            sidebarColor = Color(14, 16, 22, 240),
            aiBubbleColor = Color(28, 31, 42, 220),
            userBubbleColor = Color(74, 144, 217, 190),
            inputBgColor = Color(14, 16, 22, 225),
            controlBgColor = Color(20, 23, 32, 220),
            textColor = Color(232, 234, 240),
            mutedColor = Color(150, 155, 168),
            titleFont = Fonts.fontRise40,
            bodyFont = Fonts.fontRise35,
            titleScale = 1.1f,
            cornerRadius = 2f,
            bubbleRadius = 2f,
            controlRadius = 2f,
            useGlow = false,
            forceBlur = false,
            lightTheme = false,
            useShadow = false,
            useBorder = true,
            borderColor = Color(255, 255, 255, 25),
            sidebarSeparator = true,
            bubbleBorder = false,
            showTag = false,
            bubbleGap = 4f,
            bubblePaddingX = 8f,
            bubblePaddingTop = 6f,
            headerSeparator = true,
            contentMargin = 12f,
            fullWidthBubbles = false,
            bubbleLeftBorder = false,
            leftBorderColor = Color(0, 0, 0, 0),
            transparentSidebar = false,
            centerTitle = false,
            bubbleNoFill = false,
            bubbleAlphaScale = 1.0f,
            headerHeight = 30f,
            controlBarHeight = 30f,
            inputHeight = 40f
        )

        /** Card: 卡片式,填充分隔,有标签,明显间距,glow+shadow */
        val CARD = AiriStyleConfig(
            name = "Card",
            bgColor = Color(24, 27, 38, 242),
            sidebarColor = Color(20, 23, 32, 248),
            aiBubbleColor = Color(38, 44, 60, 232),
            userBubbleColor = Color(74, 144, 217, 200),
            inputBgColor = Color(20, 23, 32, 238),
            controlBgColor = Color(28, 32, 44, 232),
            textColor = Color(238, 240, 246),
            mutedColor = Color(170, 175, 188),
            titleFont = Fonts.fontRise50,
            bodyFont = Fonts.fontRise35,
            titleScale = 1.3f,
            cornerRadius = 10f,
            bubbleRadius = 10f,
            controlRadius = 6f,
            useGlow = true,
            forceBlur = false,
            lightTheme = false,
            useShadow = true,
            useBorder = false,
            borderColor = Color(0, 0, 0, 0),
            sidebarSeparator = false,
            bubbleBorder = false,
            showTag = true,
            bubbleGap = 10f,
            bubblePaddingX = 10f,
            bubblePaddingTop = 8f,
            headerSeparator = true,
            contentMargin = 14f,
            fullWidthBubbles = false,
            bubbleLeftBorder = false,
            leftBorderColor = Color(0, 0, 0, 0),
            transparentSidebar = false,
            centerTitle = false,
            bubbleNoFill = false,
            bubbleAlphaScale = 1.0f,
            headerHeight = 36f,
            controlBarHeight = 36f,
            inputHeight = 48f
        )

        /** Glass: 玻璃质感,半透明+blur+渐变边框,大圆角 */
        val GLASS = AiriStyleConfig(
            name = "Glass",
            bgColor = Color(28, 32, 44, 165),
            sidebarColor = Color(22, 26, 36, 175),
            aiBubbleColor = Color(44, 50, 68, 175),
            userBubbleColor = Color(74, 144, 217, 165),
            inputBgColor = Color(20, 24, 36, 175),
            controlBgColor = Color(28, 32, 44, 175),
            textColor = Color(245, 247, 252),
            mutedColor = Color(180, 185, 198),
            titleFont = Fonts.fontRise50,
            bodyFont = Fonts.fontRise35,
            titleScale = 1.3f,
            cornerRadius = 14f,
            bubbleRadius = 12f,
            controlRadius = 8f,
            useGlow = true,
            forceBlur = true,
            lightTheme = false,
            useShadow = true,
            useBorder = true,
            borderColor = Color(255, 255, 255, 40),
            sidebarSeparator = false,
            bubbleBorder = true,
            showTag = true,
            bubbleGap = 8f,
            bubblePaddingX = 10f,
            bubblePaddingTop = 8f,
            headerSeparator = false,
            contentMargin = 16f,
            fullWidthBubbles = false,
            bubbleLeftBorder = false,
            leftBorderColor = Color(0, 0, 0, 0),
            transparentSidebar = false,
            centerTitle = false,
            bubbleNoFill = false,
            bubbleAlphaScale = 1.0f,
            headerHeight = 38f,
            controlBarHeight = 38f,
            inputHeight = 50f
        )

        fun byName(name: String): AiriStyleConfig = when (name) {
            "Minimal" -> MINIMAL
            "Glass" -> GLASS
            else -> CARD
        }
    }
}

/** 当前激活的样式配置 */
val currentAiriStyle: AiriStyleConfig get() = AiriStyleConfig.byName(net.ccbluex.liquidbounce.ai.api.AiriSettings.uiStyle)
