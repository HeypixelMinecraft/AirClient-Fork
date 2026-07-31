// skid AIRI
/*
 * AirClient Hacked Client
 * A free open source mixin-based injection hacked client built on Liquidbounce legacy codebase.
 */
package net.ccbluex.liquidbounce.ui.client.clickgui.airi

import kotlinx.coroutines.launch
import net.ccbluex.liquidbounce.ai.api.AiriSettings
import net.ccbluex.liquidbounce.ai.conversation.Conversation
import net.ccbluex.liquidbounce.ai.conversation.ConversationManager
import net.ccbluex.liquidbounce.ai.conversation.Message
import net.ccbluex.liquidbounce.features.module.modules.client.ClickGUI
import net.ccbluex.liquidbounce.file.FileManager.airiConfig
import net.ccbluex.liquidbounce.file.FileManager.saveConfig
import net.ccbluex.liquidbounce.ui.font.Fonts
import net.ccbluex.liquidbounce.utils.GlowUtils
import net.ccbluex.liquidbounce.utils.kotlin.SharedScopes
import net.ccbluex.liquidbounce.utils.render.BlurUtils
import net.ccbluex.liquidbounce.utils.render.RenderUtils
import net.ccbluex.liquidbounce.utils.render.RoundedUtil
import net.minecraft.client.gui.GuiScreen
import net.minecraft.client.gui.ScaledResolution
import net.minecraft.util.ResourceLocation
import org.lwjgl.input.Keyboard
import org.lwjgl.input.Mouse
import org.lwjgl.opengl.GL11
import java.awt.Color
import java.io.IOException
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Airi 主 GUI
 *
 * 参考 RiseClickGui 风格,布局:
 *   - 左侧 Sidebar(110px): 对话列表 + 底部 Settings 按钮
 *   - 右侧主区:
 *       - 顶部 Header(36px): "Airi" 标题(1.3x 放大)
 *       - 中部 Chat Area: 对话气泡(AI 左 / 用户右),可滚动,AI 气泡下方显示耗时+tokens
 *       - 底部 Input(48px): 多行输入框(Enter 发送 / Shift+Enter 换行)
 *       - 最底 Control Bar(36px): 模式/可交互/模型/思考开关/思考强度 下拉与开关
 *   - Settings 面板(覆盖主区): 主题色点 / Glow / Blur / Opacity
 *
 * 主题色复用 ClickGUI.nl*,与 Rise 共享
 */
class AiriClickGui : GuiScreen() {

    private var x = -1f
    private var y = -1f
    private val w = 480f
    private val h = 320f
    private val sidebarW = 110f

    /** 当前样式配置 (随 AiriSettings.uiStyle 切换) */
    private val style: AiriStyleConfig get() = currentAiriStyle
    private val headerH get() = style.headerHeight
    private val controlBarH get() = style.controlBarHeight
    private val inputH get() = style.inputHeight

    private var dragging = false
    private var dragX = 0f
    private var dragY = 0f
    private var showSettings = false
    private var openProgress = 0f

    // ===== 对话区滚动 =====
    private var chatScroll = 0f        // 目标滚动偏移(负值,新消息到底部用)
    private var animChatScroll = 0f    // 渲染时插值显示的滚动偏移
    private var pendingStickToBottom = true

    // ===== 对话列表滚动 =====
    private var listScroll = 0f
    private var animListScroll = 0f

    // ===== 输入框 =====
    private val inputBuffer = StringBuilder()
    private var inputFocused = true
    private var cursorBlink = 0L

    // ===== Settings 面板可编辑字段 =====
    /** 当前正在编辑的字段 id, null 表示无: "endpoint" | "apiKey" | "newModel" */
    private var editingField: String? = null
    private val endpointBuffer = StringBuilder()
    private val apiKeyBuffer = StringBuilder()
    private val newModelBuffer = StringBuilder()
    private var settingsScroll = 0f
    private var animSettingsScroll = 0f

    // ===== 删除确认对话框 =====
    private var pendingDeleteConv: Conversation? = null

    // ===== 下拉菜单 =====
    /** 当前打开的下拉菜单 id, null 表示无 */
    private var openDropdown: String? = null

    // ===== 设置面板滑块拖动 =====
    private var sidebarDraggingSlider: String? = null // "glow" | "opacity" | "blur"
    private var valuesDirty = false

    // ===== 发送状态 =====
    @Volatile
    private var sending = false

    // ===== 主题色过渡(与 Rise 一致) =====
    private var displayedAccent: Color = lastDisplayedAccent ?: ClickGUI.nlAccentColor
    private var displayedBg: Color = lastDisplayedBg ?: ClickGUI.nlThemeBgColor

    /** 浅色样式(Paper)直接使用样式自带色;深色样式使用 ClickGUI 主题色 */
    private val background: Color
        get() = if (style.lightTheme) {
            Color(style.bgColor.red, style.bgColor.green, style.bgColor.blue, style.bgColor.alpha)
        } else {
            Color(displayedBg.red, displayedBg.green, displayedBg.blue, (255f * ClickGUI.nlBgOpacity).toInt().coerceIn(0, 255))
        }
    private val sidebar: Color
        get() = if (style.lightTheme) {
            style.sidebarColor
        } else {
            Color(displayedBg.red - 5, displayedBg.green - 6, displayedBg.blue - 8, (255f * ClickGUI.nlBgOpacity).toInt().coerceIn(0, 255))
        }
    private val overlay = Color(0, 0, 0, 50)
    private val overlayHover = Color(255, 255, 255, 20)
    private val text: Color get() = style.textColor
    private val muted: Color get() = style.mutedColor
    private val accent: Color get() = if (style.lightTheme) style.resolveAccent() else displayedAccent
    private val userBubble: Color get() = style.userBubbleColor
    private val aiBubble: Color get() = style.aiBubbleColor

    /** 控制栏专用字体 (更小,避免文字重叠) */
    private val controlFont get() = Fonts.fontRegular30

    // Glow helpers - sqrt 曲线
    private fun glowAlpha(baseAlpha: Int): Int = (baseAlpha * sqrt(ClickGUI.nlGlowIntensity.toDouble()).toFloat()).roundToInt().coerceIn(0, 255)
    private fun glowRadius(baseRadius: Int): Int = (baseRadius * (0.3f + 0.7f * ClickGUI.nlGlowIntensity)).roundToInt().coerceAtLeast(0)

    private val riseBgOpacity get() = ClickGUI.nlBgOpacity
    private val riseGlowIntensity get() = ClickGUI.nlGlowIntensity

    private fun lerpColor(a: Color, b: Color, t: Float): Color {
        val r = (a.red + (b.red - a.red) * t).roundToInt().coerceIn(0, 255)
        val g = (a.green + (b.green - a.green) * t).roundToInt().coerceIn(0, 255)
        val bl = (a.blue + (b.blue - a.blue) * t).roundToInt().coerceIn(0, 255)
        return Color(r, g, bl)
    }

    // ===== Layout helpers =====
    // SIDEBAR 布局: 左固定侧边栏 sidebarW, 右侧主区
    private val contentX: Float get() = x + sidebarW
    private val contentW: Float get() = w - sidebarW
    private val chatTop get() = y + headerH
    private val chatBottom get() = y + h - inputH - controlBarH
    private val chatH get() = chatBottom - chatTop
    private val inputTop get() = y + h - inputH - controlBarH
    private val controlTop get() = y + h - controlBarH

    override fun initGui() {
        Keyboard.enableRepeatEvents(true)
        if (lastX >= 0f && lastY >= 0f && lastX + w <= width && lastY + h <= height) {
            x = lastX; y = lastY
        } else if (x < 0f || y < 0f || x + w > width || y + h > height) {
            x = width / 2f - w / 2f
            y = height / 2f - h / 2f
        }
        openProgress = 0f
        pendingStickToBottom = true
        super.initGui()
    }

    override fun onGuiClosed() {
        Keyboard.enableRepeatEvents(false)
        // 提交未保存的编辑字段
        if (editingField != null) {
            commitEditingField()
        }
        flushConfigs()
        saveConfig(airiConfig)
        lastX = x; lastY = y
        lastDisplayedAccent = displayedAccent
        lastDisplayedBg = displayedBg
        super.onGuiClosed()
    }

    override fun doesGuiPauseGame() = false

    override fun drawScreen(mouseX: Int, mouseY: Int, partialTicks: Float) {
        if (dragging) {
            x = (mouseX + dragX).coerceIn(0f, width - w)
            y = (mouseY + dragY).coerceIn(0f, height - h)
        }
        if (!Mouse.isButtonDown(0)) {
            sidebarDraggingSlider = null
        }
        if (sidebarDraggingSlider != null && Mouse.isButtonDown(0)) {
            handleSidebarSliderDrag(mouseX)
        }

        handleWheel(mouseX, mouseY)
        openProgress = animate(openProgress, 1f, 0.18f)
        val scale = 0.92f + 0.08f * easeOut(openProgress)

        // 主题色过渡
        displayedAccent = lerpColor(displayedAccent, ClickGUI.nlAccentColor, 0.02f)
        displayedBg = lerpColor(displayedBg, ClickGUI.nlThemeBgColor, 0.02f)

        // 光标闪烁
        cursorBlink = (cursorBlink + 1) % 60

        // Blur 背景(必须在 GL11 变换之前调用)
        // Glass 样式强制启用 blur,即使 ClickGUI.nlBlur=false
        val blurEnabled = ClickGUI.nlBlur || style.forceBlur
        if (blurEnabled) {
            BlurUtils.blurAreaRounded(x, y, x + w, y + h, style.cornerRadius, ClickGUI.nlBlurStrength)
        }

        GL11.glPushMatrix()
        GL11.glTranslatef(x + w / 2f, y + h / 2f, 0f)
        GL11.glScalef(scale, scale, 1f)
        GL11.glTranslatef(-(x + w / 2f), -(y + h / 2f), 0f)
        if (style.useShadow) drawShadow()
        if (style.useBorder) {
            RoundedUtil.drawRound(x - 1f, y - 1f, w + 2f, h + 2f, style.cornerRadius + 1f, style.borderColor)
        }
        RoundedUtil.drawRound(x, y, w, h, style.cornerRadius, background)
        startScissor(x, y, w, h)
        drawSidebar(mouseX, mouseY)
        if (showSettings) {
            drawSettingsContent(contentX, contentW, mouseX, mouseY)
        } else {
            drawHeader(mouseX, mouseY)
            drawChatArea(mouseX, mouseY)
            drawInputBox(mouseX, mouseY)
            drawControlBar(mouseX, mouseY)
        }
        endScissor()
        GL11.glPopMatrix()

        // 下拉菜单(在 scissor 之外渲染,允许超出窗口边界)
        if (openDropdown != null) {
            drawDropdown(mouseX, mouseY)
        }

        // 删除确认对话框(顶层)
        pendingDeleteConv?.let { drawDeleteConfirm(it, mouseX, mouseY) }

        super.drawScreen(mouseX, mouseY, partialTicks)
    }

    @Throws(IOException::class)
    override fun mouseClicked(mouseX: Int, mouseY: Int, mouseButton: Int) {
        // 优先处理删除确认对话框
        if (pendingDeleteConv != null) {
            handleDeleteConfirmClick(mouseX, mouseY, mouseButton)
            return
        }
        // 优先处理下拉菜单点击
        if (openDropdown != null) {
            handleDropdownClick(mouseX, mouseY, mouseButton)
            return
        }

        // Settings 面板打开时:优先处理 Settings 点击
        // (跳过 header 拖动检查,因为 Settings 内容从 y+10f 开始,与 header 区域重叠,
        //  会导致点击 blur 开关和输入框被误判为拖动窗口)
        if (showSettings) {
            if (!isHovered(x, y, w, h, mouseX, mouseY)) return
            clickSidebar(mouseX, mouseY, mouseButton)
            clickSettings(contentX, contentW, mouseX, mouseY, mouseButton)
            super.mouseClicked(mouseX, mouseY, mouseButton)
            return
        }

        if (isHovered(x, y, w, headerH, mouseX, mouseY) && mouseButton == 0) {
            dragging = true
            dragX = x - mouseX
            dragY = y - mouseY
            return
        }

        if (!isHovered(x, y, w, h, mouseX, mouseY)) {
            inputFocused = false
            return
        }

        clickSidebar(mouseX, mouseY, mouseButton)
        clickHeader(mouseX, mouseY, mouseButton)
        clickChatArea(mouseX, mouseY, mouseButton)
        clickInputBox(mouseX, mouseY, mouseButton)
        clickControlBar(mouseX, mouseY, mouseButton)

        super.mouseClicked(mouseX, mouseY, mouseButton)
    }

    override fun mouseReleased(mouseX: Int, mouseY: Int, state: Int) {
        dragging = false
        sidebarDraggingSlider = null
        flushConfigs()
        super.mouseReleased(mouseX, mouseY, state)
    }

    @Throws(IOException::class)
    override fun keyTyped(typedChar: Char, keyCode: Int) {
        // ===== Settings 面板可编辑字段优先处理 =====
        val editing = editingField
        if (showSettings && editing != null) {
            when (keyCode) {
                Keyboard.KEY_RETURN -> {
                    if (editing == "newModel") {
                        commitNewModel()
                    } else {
                        commitEditingField()
                    }
                    return
                }
                Keyboard.KEY_ESCAPE -> {
                    // 取消编辑,不提交
                    editingField = null
                    endpointBuffer.clear()
                    apiKeyBuffer.clear()
                    newModelBuffer.clear()
                    return
                }
                Keyboard.KEY_BACK -> {
                    val buf = when (editing) {
                        "endpoint" -> endpointBuffer
                        "apiKey" -> apiKeyBuffer
                        "newModel" -> newModelBuffer
                        else -> return
                    }
                    if (buf.isNotEmpty()) {
                        buf.deleteCharAt(buf.length - 1)
                    }
                    return
                }
            }
            if (!Character.isISOControl(typedChar)) {
                val buf = when (editing) {
                    "endpoint" -> endpointBuffer
                    "apiKey" -> apiKeyBuffer
                    "newModel" -> newModelBuffer
                    else -> return
                }
                buf.append(typedChar)
                return
            }
        }

        if (inputFocused && !showSettings) {
            when (keyCode) {
                Keyboard.KEY_RETURN -> {
                    if (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT)) {
                        inputBuffer.append('\n')
                    } else {
                        sendCurrentInput()
                        return
                    }
                    return
                }
                Keyboard.KEY_BACK -> {
                    if (inputBuffer.isNotEmpty()) {
                        // 删除一个字符(处理 \n 的情况)
                        val last = inputBuffer.last()
                        inputBuffer.deleteCharAt(inputBuffer.length - 1)
                        if (last == '\n') {
                            // 已删除换行符
                        }
                    }
                    return
                }
                Keyboard.KEY_ESCAPE -> {
                    inputFocused = false
                    return
                }
            }
            if (!Character.isISOControl(typedChar)) {
                inputBuffer.append(typedChar)
                return
            }
        }

        if (keyCode == Keyboard.KEY_ESCAPE) {
            if (editingField != null) {
                editingField = null
                endpointBuffer.clear()
                apiKeyBuffer.clear()
                newModelBuffer.clear()
                return
            }
            if (openDropdown != null) {
                openDropdown = null
                return
            }
            if (pendingDeleteConv != null) {
                pendingDeleteConv = null
                return
            }
            mc.displayGuiScreen(null)
            return
        }

        super.keyTyped(typedChar, keyCode)
    }

    // ===== Drawing =====

    private fun drawShadow() {
        for (i in 0..5) {
            RoundedUtil.drawRound(x - i, y - i, w + i * 2f, h + i * 2f, 12f + i, Color(0, 0, 0, 18 - i * 2))
        }
    }

    private fun drawHeader(mouseX: Int, mouseY: Int) {
        val bodyF = style.bodyFont
        val bodyFh = bodyF.fontHeight
        // Airi 标题(按样式缩放) - Stream 样式居中显示
        val titleTextW = style.titleFont.getStringWidth("Airi") * style.titleScale
        val titleX = if (style.centerTitle) {
            contentX + (contentW - titleTextW) / 2f
        } else {
            contentX + 12f
        }
        GL11.glPushMatrix()
        GL11.glTranslatef(titleX, y + (headerH - style.titleFont.fontHeight * style.titleScale) / 2f, 0f)
        GL11.glScalef(style.titleScale, style.titleScale, 1f)
        style.titleFont.drawString("Airi", 0f, 0f, text.rgb)
        GL11.glPopMatrix()

        // 模式状态指示(右上)
        val modeText = "[${AiriSettings.mode}]"
        val modeW = bodyF.getStringWidth(modeText)
        bodyF.drawString(modeText, contentX + contentW - modeW - 12f, y + (headerH - bodyFh) / 2f + 1f, accent.rgb)

        // 当前对话标题(中间) — 动态计算可用宽度, 避免与模式文字重叠
        val conv = ConversationManager.current
        val titleStart = contentX + 70f
        val titleEnd = (contentX + contentW - modeW - 20f).coerceAtLeast(titleStart + 60f)
        val titleMaxW = (titleEnd - titleStart).toInt().coerceAtLeast(60)
        val titleText = trimToWidth(conv.title, titleMaxW)
        bodyF.drawString(titleText, titleStart, y + (headerH - bodyFh) / 2f + 1f, muted.rgb)

        // 底部分隔线 (根据样式决定是否绘制)
        if (style.headerSeparator) {
            val lineColor = if (style.lightTheme) Color(0, 0, 0, 30) else Color(255, 255, 255, 20)
            RenderUtils.drawRect(contentX + 8f, y + headerH - 1f, contentX + contentW - 8f, y + headerH, lineColor.rgb)
        }
    }

    private fun drawSidebar(mouseX: Int, mouseY: Int) {
        if (style.transparentSidebar) {
            // Ocean: 侧边栏完全透明,与主背景融合,不画填充也不画分隔线
        } else if (!style.sidebarSeparator) {
            // 填充背景分隔 (Card/Glass)
            RoundedUtil.drawRound(x, y, sidebarW, h, style.cornerRadius, sidebar)
            // Blur 开启时不画装饰圆圈,避免光圈效果
            val blurActive = ClickGUI.nlBlur || style.forceBlur
            if (!blurActive && !style.lightTheme) {
                for (i in 0..7) {
                    val radius = i * 42f
                    drawCircle(x + sidebarW - radius / 2f, y + h / 2f - radius / 2f, radius, Color(accent.red, accent.green, accent.blue, 10))
                }
            }
        } else {
            // 线条分隔 (Minimal/Paper) - 不画填充背景,只画右侧分隔线
            val lineColor = if (style.lightTheme) Color(0, 0, 0, 30) else Color(255, 255, 255, 20)
            RenderUtils.drawRect(x + sidebarW - 1f, y + 1f, x + sidebarW, y + h - 1f, lineColor.rgb)
        }

        // "Airi" 标题(按样式缩放)
        GL11.glPushMatrix()
        GL11.glTranslatef(x + 10f, y + 12f, 0f)
        GL11.glScalef(style.titleScale, style.titleScale, 1f)
        style.titleFont.drawString("Airi", 0f, 0f, text.rgb)
        GL11.glPopMatrix()

        // 对话列表(裁剪滚动)
        val listTop = y + headerH + 6f
        val listBottom = y + h - 36f - 6f  // 留出底部 Settings 按钮空间
        val listH = listBottom - listTop

        // 对话总数(若超出可视区域则启用滚动)
        val convs = ConversationManager.all()
        val itemH = 24f
        val totalH = convs.size * itemH
        val maxScrollDown = (totalH - listH).coerceAtLeast(0f)
        listScroll = listScroll.coerceIn(-maxScrollDown, 0f)
        animListScroll = animate(animListScroll, listScroll, 0.18f)

        startScissor(x + 4f, listTop, sidebarW - 8f, listH)
        var iy = listTop + animListScroll
        convs.forEachIndexed { index, conv ->
            val isCurrent = conv.id == ConversationManager.current.id
            val hovered = isHovered(x + 4f, iy, sidebarW - 8f, itemH, mouseX, mouseY)
            if (isCurrent) {
                RoundedUtil.drawRound(x + 6f, iy + 2f, sidebarW - 12f, itemH - 4f, style.controlRadius, Color(accent.red, accent.green, accent.blue, 105))
            } else if (hovered) {
                val hoverCol = if (style.lightTheme) Color(0, 0, 0, 25) else Color(255, 255, 255, 18)
                RoundedUtil.drawRound(x + 6f, iy + 2f, sidebarW - 12f, itemH - 4f, style.controlRadius, hoverCol)
            }
            // 标题(若空则显示 "New Chat")
            val titleStr = if (conv.title.isBlank()) "New Chat" else conv.title
            val trimmed = trimToWidth(titleStr, (sidebarW - 36f).toInt())
            val titleColor = if (isCurrent) text.rgb else muted.rgb
            style.bodyFont.drawString(trimmed, x + 10f, iy + 9f, titleColor)

            // 删除图标(close.png)
            val closeIconX = x + sidebarW - 18f
            val closeIconY = iy + 6f
            val closeHovered = isHovered(closeIconX, closeIconY, 12f, 12f, mouseX, mouseY)
            val iconColor = if (closeHovered) Color(255, 80, 80, 255) else Color(text.red, text.green, text.blue, if (isCurrent) 200 else 120)
            RenderUtils.drawImage(closeIcon, closeIconX.toInt(), closeIconY.toInt(), 12, 12, iconColor)

            iy += itemH
        }
        endScissor()

        // 对话列表滚动条
        if (totalH > listH) {
            val barH = (listH * (listH / totalH)).coerceAtLeast(20f)
            val progress = (-animListScroll / (totalH - listH)).coerceIn(0f, 1f)
            val trackCol = if (style.lightTheme) Color(0, 0, 0, 30) else Color(255, 255, 255, 28)
            RoundedUtil.drawRound(x + sidebarW - 4f, listTop, 2f, listH, 1f, trackCol)
            RoundedUtil.drawRound(x + sidebarW - 4f, listTop + (listH - barH) * progress, 2f, barH, 1f, accent)
        }

        // 底部:新建对话 + Settings 按钮
        val bottomY = y + h - 36f
        val btnHoverCol = if (style.lightTheme) Color(0, 0, 0, 25) else Color(255, 255, 255, 18)
        // New Chat 按钮
        val newHovered = isHovered(x + 6f, bottomY, sidebarW - 12f, 16f, mouseX, mouseY)
        if (newHovered) {
            RoundedUtil.drawRound(x + 6f, bottomY, sidebarW - 12f, 16f, style.controlRadius, btnHoverCol)
        }
        style.bodyFont.drawString("+ New Chat", x + 10f, bottomY + 5f, text.rgb)

        // Settings 按钮
        val settingsY = bottomY + 18f
        val settingsHovered = isHovered(x + 6f, settingsY, sidebarW - 12f, 16f, mouseX, mouseY)
        val settingsSelected = showSettings
        if (settingsSelected) {
            RoundedUtil.drawRound(x + 6f, settingsY, sidebarW - 12f, 16f, style.controlRadius, Color(accent.red, accent.green, accent.blue, 105))
        } else if (settingsHovered) {
            RoundedUtil.drawRound(x + 6f, settingsY, sidebarW - 12f, 16f, style.controlRadius, btnHoverCol)
        }
        Fonts.fontRiseIcon35.drawString("e", x + 10f, settingsY + 5f, if (settingsSelected) Color.WHITE.rgb else text.rgb)
        style.bodyFont.drawString("Settings", x + 26f, settingsY + 5f, if (settingsSelected) Color.WHITE.rgb else text.rgb)
    }

    private fun drawChatArea(mouseX: Int, mouseY: Int) {
        val conv = ConversationManager.current
        val messages = conv.messages.toList()

        // 计算每个气泡高度,得到总高度 (根据样式差异化)
        data class MeasuredMsg(val msg: Message, val bubbleH: Float, val bubbleW: Float)
        val margin = style.contentMargin
        val padX = style.bubblePaddingX
        val tagH = if (style.showTag) 14f else 0f
        val maxBubbleW = contentW - margin * 2f
        val measured = messages.map { msg ->
            val (textW, textH) = measureMessage(msg.content, maxBubbleW - padX * 2f)
            val footerH = if (msg.isAssistant && msg.error == null) 12f else if (msg.error != null) 12f else 0f
            // Stream 样式: 气泡占满内容区宽度
            val bubbleW = if (style.fullWidthBubbles) maxBubbleW else (textW + padX * 2f).coerceAtMost(maxBubbleW)
            val bubbleH = style.bubblePaddingTop + tagH + textH + 4f + footerH
            MeasuredMsg(msg, bubbleH, bubbleW)
        }
        val totalH = measured.sumOf { (it.bubbleH + style.bubbleGap).toDouble() }.toFloat()
        val visibleH = chatH - 4f

        // 自动贴底:新消息到底部
        if (pendingStickToBottom) {
            chatScroll = -(totalH - visibleH).coerceAtLeast(0f)
            pendingStickToBottom = false
        }
        val maxDown = (totalH - visibleH).coerceAtLeast(0f)
        chatScroll = chatScroll.coerceIn(-maxDown, 0f)
        animChatScroll = animate(animChatScroll, chatScroll, 0.2f)

        startScissor(contentX + 4f, chatTop + 2f, contentW - 8f, chatH - 4f)
        var my = chatTop + 6f + animChatScroll
        measured.forEach { m ->
            val msg = m.msg
            if (style.fullWidthBubbles) {
                // Stream: 全宽气泡,统一左对齐(不区分 user/assistant)
                val bx = contentX + margin
                drawBubble(bx, my, m.bubbleW, m.bubbleH, msg, msg.isUser)
            } else if (msg.isUser) {
                val bx = contentX + contentW - margin - m.bubbleW
                drawBubble(bx, my, m.bubbleW, m.bubbleH, msg, true)
            } else {
                val bx = contentX + margin
                drawBubble(bx, my, m.bubbleW, m.bubbleH, msg, false)
            }
            my += m.bubbleH + style.bubbleGap
        }

        // 发送中提示
        if (sending) {
            val loadingText = "Thinking..."
            val lw = style.bodyFont.getStringWidth(loadingText) + padX * 2f
            RoundedUtil.drawRound(contentX + margin, my, lw, 22f, style.bubbleRadius, aiBubble)
            style.bodyFont.drawString(loadingText, contentX + margin + padX, my + 7f, muted.rgb)
        }
        endScissor()

        // 滚动条
        if (totalH > visibleH) {
            val barH = (visibleH * (visibleH / totalH)).coerceAtLeast(20f)
            val progress = (-animChatScroll / (totalH - visibleH)).coerceIn(0f, 1f)
            val sx = contentX + contentW - 5f
            val trackCol = if (style.lightTheme) Color(0, 0, 0, 30) else Color(255, 255, 255, 28)
            RoundedUtil.drawRound(sx, chatTop + 4f, 2f, visibleH, 1f, trackCol)
            RoundedUtil.drawRound(sx, chatTop + 4f + (visibleH - barH) * progress, 2f, barH, 1f, accent)
        }
    }

    private fun drawBubble(bx: Float, by: Float, bw: Float, bh: Float, msg: Message, isUser: Boolean) {
        val rawColor = if (isUser) userBubble else aiBubble
        // 应用透明度倍率 (Focus: 半透明背景)
        val color = if (style.bubbleAlphaScale < 1.0f) {
            Color(rawColor.red, rawColor.green, rawColor.blue, (rawColor.alpha * style.bubbleAlphaScale).toInt().coerceIn(0, 255))
        } else rawColor
        // 气泡边框 (Glass/Paper/Ocean)
        if (style.bubbleBorder) {
            RoundedUtil.drawRound(bx - 1f, by - 1f, bw + 2f, bh + 2f, style.bubbleRadius + 1f, style.borderColor)
        }
        // Focus: 无填充背景,仅左侧色条;其他样式正常填充
        if (!style.bubbleNoFill) {
            RoundedUtil.drawRound(bx, by, bw, bh, style.bubbleRadius, color)
        }
        // 左侧色条 (Focus 独有: 用户色条略亮)
        if (style.bubbleLeftBorder) {
            val stripColor = if (isUser) {
                Color(style.leftBorderColor.red, style.leftBorderColor.green, style.leftBorderColor.blue, 240)
            } else style.leftBorderColor
            RoundedUtil.drawRound(bx, by, 3f, bh, 1.5f, stripColor)
        }

        val padX = style.bubblePaddingX
        val tagH = if (style.showTag) 14f else 0f

        // 标签 (Minimal 不显示,用对齐区分)
        if (style.showTag) {
            val tagText = if (isUser) "You" else "Airi"
            val tagColor = if (isUser) {
                if (style.lightTheme) Color(100, 70, 30, 240) else Color(180, 220, 255, 220)
            } else accent
            style.bodyFont.drawString(tagText, bx + padX, by + 4f, tagColor.rgb)
        }

        // 消息内容
        val lines = wrapText(msg.content, bw - padX * 2f)
        var ty = by + style.bubblePaddingTop + tagH
        lines.forEach { line ->
            style.bodyFont.drawString(line, bx + padX, ty, text.rgb)
            ty += 12f
        }

        // 元数据:耗时 + tokens(AI 气泡)
        if (!isUser && msg.error == null) {
            val metaText = buildString {
                if (msg.durationMs > 0) append("${msg.durationMs}ms")
                if (msg.tokens > 0) {
                    if (isNotEmpty()) append(" · ")
                    append("${msg.tokens} tokens")
                }
                if (msg.isReasoning) {
                    if (isNotEmpty()) append(" · ")
                    append("reasoning")
                }
            }
            if (metaText.isNotBlank()) {
                val metaCol = if (style.lightTheme) Color(80, 60, 40, 180) else Color(255, 255, 255, 90)
                style.bodyFont.drawString(metaText, bx + padX, by + bh - 12f, metaCol.rgb)
            }
        }

        // 错误显示
        val err = msg.error
        if (err != null) {
            style.bodyFont.drawString("Error: ${trimToWidth(err, (bw - padX * 2f).toInt())}", bx + padX, by + bh - 12f, Color(255, 80, 80, 220).rgb)
        }
    }

    private fun measureMessage(content: String, maxWidth: Float): Pair<Float, Float> {
        val lines = wrapText(content, maxWidth)
        val textW: Float = (lines.maxOfOrNull { style.bodyFont.getStringWidth(it) } ?: 0).toFloat()
        val textH: Float = lines.size.toFloat() * 12f
        return textW to textH
    }

    private fun wrapText(text: String, maxWidth: Float): List<String> {
        if (text.isBlank()) return listOf("")
        val result = mutableListOf<String>()
        text.split('\n').forEach { paragraph ->
            if (paragraph.isBlank()) {
                result.add("")
                return@forEach
            }
            val words = paragraph.split(' ')
            var current = StringBuilder()
            for (word in words) {
                val candidate = if (current.isEmpty()) word else "$current $word"
                if (style.bodyFont.getStringWidth(candidate) <= maxWidth) {
                    current = StringBuilder(candidate)
                } else {
                    if (current.isNotEmpty()) {
                        result.add(current.toString())
                        current = StringBuilder()
                    }
                    // 处理超长单词(强制截断)
                    var remaining = word
                    while (style.bodyFont.getStringWidth(remaining) > maxWidth && remaining.length > 1) {
                        var cut = remaining.length - 1
                        while (cut > 1 && style.bodyFont.getStringWidth(remaining.substring(0, cut)) > maxWidth) {
                            cut--
                        }
                        result.add(remaining.substring(0, cut))
                        remaining = remaining.substring(cut)
                    }
                    current = StringBuilder(remaining)
                }
            }
            result.add(current.toString())
        }
        return result
    }

    private fun drawInputBox(mouseX: Int, mouseY: Int) {
        val ix = contentX + 12f
        val iy = inputTop + 6f
        val iw = contentW - 24f
        val ih = inputH - 12f

        val bgColor = if (style.lightTheme) {
            style.inputBgColor
        } else if (inputFocused) Color(20, 24, 36, 220) else Color(15, 18, 28, 200)
        RoundedUtil.drawRound(ix, iy, iw, ih, style.controlRadius, bgColor)
        if (inputFocused) {
            RenderUtils.drawRect(ix, iy + ih - 1f, ix + iw, iy + ih, accent.rgb)
        }

        // 显示输入内容(支持多行,显示最后 2 行)
        val displayLines = if (inputBuffer.isEmpty()) {
            listOf(if (sending) "Waiting for response..." else "Type message... (Enter to send, Shift+Enter for newline)")
        } else {
            val allLines = inputBuffer.toString().split('\n')
            allLines.takeLast(2)
        }
        var ty = iy + 6f
        displayLines.forEach { line ->
            val displayLine = if (inputBuffer.isEmpty()) line else trimToWidth(line, (iw - 16f).toInt())
            val color = if (inputBuffer.isEmpty()) muted.rgb else text.rgb
            style.bodyFont.drawString(displayLine, ix + 8f, ty, color)
            ty += 12f
        }

        // 光标(闪烁)
        if (inputFocused && cursorBlink < 30) {
            val allLines = inputBuffer.toString().split('\n')
            val lastLine = allLines.lastOrNull() ?: ""
            val cursorX = ix + 8f + style.bodyFont.getStringWidth(trimToWidth(lastLine, (iw - 16f).toInt()))
            val cursorY = iy + 6f + (displayLines.size - 1) * 12f
            RenderUtils.drawRect(cursorX, cursorY, cursorX + 1f, cursorY + 10f, accent.rgb)
        }

        // 发送按钮(右侧) - 扁平化设计,高度更小
        val sendBtnW = 44f
        val sendBtnH = 18f  // 原 ih-8f=28f,改为更扁的 18f
        val sendBtnX = ix + iw - sendBtnW - 6f
        val sendBtnY = iy + (ih - sendBtnH) / 2f  // 垂直居中
        val sendHovered = isHovered(sendBtnX, sendBtnY, sendBtnW, sendBtnH, mouseX, mouseY)
        val canSend = inputBuffer.isNotBlank() && !sending
        val sendColor = if (canSend) {
            if (sendHovered) Color(accent.red, accent.green, accent.blue, 220) else accent
        } else {
            if (style.lightTheme) Color(180, 170, 150, 200) else Color(60, 65, 80, 200)
        }
        RoundedUtil.drawRound(sendBtnX, sendBtnY, sendBtnW, sendBtnH, style.controlRadius, sendColor)
        style.bodyFont.drawString("Send", sendBtnX + (sendBtnW - style.bodyFont.getStringWidth("Send")) / 2f, sendBtnY + (sendBtnH - style.bodyFont.fontHeight) / 2f + 1f, Color.WHITE.rgb)
    }

    private fun drawControlBar(mouseX: Int, mouseY: Int) {
        // 背景分隔
        val lineColor = if (style.lightTheme) Color(0, 0, 0, 30) else Color(255, 255, 255, 20)
        RenderUtils.drawRect(contentX + 4f, controlTop, contentX + contentW - 4f, controlTop + 1f, lineColor.rgb)

        val barY = controlTop + 5f
        val barH = controlBarH - 10f
        val layout = controlBarLayout()

        for (btn in layout) {
            if (btn.isToggle) {
                val value = when (btn.id) {
                    "interact" -> AiriSettings.interactionAllowed
                    "think" -> AiriSettings.thinkEnabled
                    else -> false
                }
                drawToggleControl(btn.id, btn.label, value, btn.x, barY, btn.w, barH, mouseX, mouseY)
            } else {
                val value = when (btn.id) {
                    "mode" -> AiriSettings.mode
                    "model" -> AiriSettings.model
                    "thinkstr" -> "%.2f".format(AiriSettings.thinkStrength)
                    "role" -> AiriSettings.role
                    else -> ""
                }
                drawDropdownControl(btn.id, btn.label, value, btn.x, barY, btn.w, barH, mouseX, mouseY)
            }
        }
    }

    /** 计算控制栏按钮布局 (根据实际文字宽度动态分配, 自动缩放避免溢出) */
    private data class ControlBtn(val id: String, val label: String, val x: Float, val w: Float, val isToggle: Boolean)

    private fun controlBarLayout(): List<ControlBtn> {
        val pad = 6f
        val gap = 4f
        val font = controlFont
        // 根据当前设置预计算每个控件的 value 文字
        data class Ctrl(val id: String, val label: String, val value: String, val isToggle: Boolean)
        val controls = mutableListOf<Ctrl>()
        controls.add(Ctrl("mode", "Mode", AiriSettings.mode, false))
        controls.add(Ctrl("interact", "Int", if (AiriSettings.interactionAllowed) "ON" else "OFF", true))
        controls.add(Ctrl("model", "Model", AiriSettings.model, false))
        controls.add(Ctrl("think", "Think", if (AiriSettings.thinkEnabled) "ON" else "OFF", true))
        if (AiriSettings.thinkEnabled) controls.add(Ctrl("thinkstr", "T-Str", "%.2f".format(AiriSettings.thinkStrength), false))
        if (AiriSettings.mode == "roleplay") controls.add(Ctrl("role", "Role", AiriSettings.role, false))

        // 按实际文字宽度计算每个控件所需宽度: label + gap + value + arrow/toggle + padding
        val neededWidths = controls.map { c ->
            val labelW = font.getStringWidth(c.label)
            val valueW = font.getStringWidth(c.value)
            val extraW = if (c.isToggle) 24f else 14f  // toggle=开关宽度, dropdown=箭头宽度
            (labelW + 6f + valueW + extraW + 8f).coerceAtLeast(50f)
        }
        val totalDesired = neededWidths.sum() + (controls.size - 1) * gap
        val availableW = contentW - 2 * pad
        val scale = if (totalDesired > availableW) availableW / totalDesired else 1f

        val result = mutableListOf<ControlBtn>()
        var cx = contentX + pad
        for ((i, c) in controls.withIndex()) {
            val actualW = neededWidths[i] * scale
            result.add(ControlBtn(c.id, c.label, cx, actualW, c.isToggle))
            cx += actualW + gap
        }
        return result
    }

    private fun drawDropdownControl(
        id: String, label: String, value: String,
        cx: Float, cy: Float, cw: Float, ch: Float,
        mouseX: Int, mouseY: Int
    ) {
        val font = controlFont
        val hovered = isHovered(cx, cy, cw, ch, mouseX, mouseY)
        val isOpen = openDropdown == id
        val bgColor = when {
            isOpen -> Color(accent.red, accent.green, accent.blue, 80)
            hovered -> if (style.lightTheme) Color(0, 0, 0, 25) else Color(255, 255, 255, 24)
            else -> style.controlBgColor
        }
        RoundedUtil.drawRound(cx, cy, cw, ch, style.controlRadius, bgColor)

        // 单行布局: 标签(浅色) + 值(白色) + 下拉箭头(右), 使用 controlFont
        val fh = font.fontHeight
        val labelY = cy + (ch - fh) / 2f + 1f
        font.drawString(label, cx + 4f, labelY, muted.rgb)
        val labelW = font.getStringWidth(label)
        // 箭头占 12px, 留 4px 间距
        val valueMaxW = (cw - labelW - 16f).toInt().coerceAtLeast(10)
        val valueDisplay = trimToWidth(value, valueMaxW)
        font.drawString(valueDisplay, cx + 4f + labelW + 4f, labelY, text.rgb)
        // 下拉箭头
        val arrowX = cx + cw - 10f
        val arrowY = cy + (ch - fh) / 2f + 1f
        font.drawString(if (isOpen) "▲" else "▼", arrowX, arrowY, muted.rgb)
    }

    private fun drawToggleControl(
        id: String, label: String, value: Boolean,
        cx: Float, cy: Float, cw: Float, ch: Float,
        mouseX: Int, mouseY: Int
    ) {
        val font = controlFont
        val hovered = isHovered(cx, cy, cw, ch, mouseX, mouseY)
        val bgColor = if (hovered) {
            if (style.lightTheme) Color(0, 0, 0, 25) else Color(255, 255, 255, 24)
        } else style.controlBgColor
        RoundedUtil.drawRound(cx, cy, cw, ch, style.controlRadius, bgColor)

        // 单行布局: 标签(浅色, 左) + 状态文字(中) + 开关(右, 垂直居中)
        val fh = font.fontHeight
        val labelY = cy + (ch - fh) / 2f + 1f
        font.drawString(label, cx + 4f, labelY, muted.rgb)
        val labelW = font.getStringWidth(label)
        val stateText = if (value) "ON" else "OFF"
        font.drawString(stateText, cx + 4f + labelW + 4f, labelY, if (value) accent.rgb else muted.rgb)
        // 开关
        val toggleW = 16f
        val toggleH = 8f
        val toggleX = cx + cw - toggleW - 5f
        val toggleY = cy + (ch - toggleH) / 2f
        val toggleColor = if (value) accent else if (style.lightTheme) Color(160, 150, 130, 200) else Color(47, 53, 68, 255)
        RoundedUtil.drawRound(toggleX, toggleY, toggleW, toggleH, 4f, toggleColor)
        // 小圆点(垂直居中:toggleH=8, dotSize=6, 偏移 = (8-6)/2 = 1)
        val dotX = if (value) toggleX + toggleW - 6f else toggleX + 1f
        RoundedUtil.drawRound(dotX, toggleY + 1f, 6f, 6f, 3f, Color.WHITE)
        // Glow
        if (value && style.useGlow && riseGlowIntensity > 0f) {
            GlowUtils.drawGlow(toggleX - 2f, toggleY - 4f, toggleW + 4f, toggleH + 6f, glowRadius(14), Color(accent.red, accent.green, accent.blue, glowAlpha(180)))
        }
    }

    private fun drawDropdown(mouseX: Int, mouseY: Int) {
        val id = openDropdown ?: return
        val font = controlFont
        val barY = controlTop + 5f
        val barH = controlBarH - 10f

        // 用 controlBarLayout() 获取按钮位置 (与 drawControlBar 一致)
        val btn = controlBarLayout().firstOrNull { it.id == id } ?: return
        val dropX = btn.x
        val dropW = btn.w

        val items = when (id) {
            "mode" -> listOf("chat", "script", "roleplay")
            "model" -> AiriSettings.models.toList()
            "thinkstr" -> listOf("0.00", "0.25", "0.50", "0.75", "1.00")
            "role" -> listOf("prankster", "helper", "observer", "custom")
            else -> return
        }

        val dropY = barY + barH + 2f
        val itemH = 16f
        val dropH = items.size * itemH + 4f
        val actualDropX = dropX.coerceIn(x + 4f, x + w - dropW - 4f)
        val actualDropY = dropY.coerceAtMost(y + h - dropH - 4f)

        // 半透明背景
        val dropBg = if (style.lightTheme) Color(248, 242, 226, 250) else Color(20, 24, 36, 240)
        RoundedUtil.drawRound(actualDropX, actualDropY, dropW, dropH, style.controlRadius, dropBg)
        // 边框
        RoundedUtil.drawRound(actualDropX, actualDropY, dropW, dropH, style.controlRadius, Color(accent.red, accent.green, accent.blue, 60))

        var iy = actualDropY + 2f
        items.forEach { item ->
            val selected = when (id) {
                "mode" -> AiriSettings.mode == item
                "model" -> AiriSettings.model == item
                "thinkstr" -> "%.2f".format(AiriSettings.thinkStrength) == item
                "role" -> AiriSettings.role == item
                else -> false
            }
            val hovered = isHovered(actualDropX, iy, dropW, itemH, mouseX, mouseY)
            if (selected) {
                RoundedUtil.drawRound(actualDropX + 2f, iy, dropW - 4f, itemH, 3f, Color(accent.red, accent.green, accent.blue, 80))
            } else if (hovered) {
                val hoverCol = if (style.lightTheme) Color(0, 0, 0, 25) else Color(255, 255, 255, 24)
                RoundedUtil.drawRound(actualDropX + 2f, iy, dropW - 4f, itemH, 3f, hoverCol)
            }
            val displayText = trimToWidth(item, (dropW - 10f).toInt())
            val itemY = iy + (itemH - font.fontHeight) / 2f + 1f
            font.drawString(displayText, actualDropX + 5f, itemY, if (selected) Color.WHITE.rgb else text.rgb)
            iy += itemH
        }
    }

    private fun drawSettingsContent(contentX: Float, contentW: Float, mouseX: Int, mouseY: Int) {
        val sx = contentX + 13f
        val sliderW = contentW - 26f
        val settingsTop = y + 10f
        val settingsBottom = y + h - 6f
        val settingsH = settingsBottom - settingsTop

        // 估算内容总高度以启用滚动
        val contentH = estimateSettingsHeight()
        val maxScrollDown = (contentH - settingsH).coerceAtLeast(0f)
        settingsScroll = settingsScroll.coerceIn(-maxScrollDown, 0f)
        animSettingsScroll = animate(animSettingsScroll, settingsScroll, 0.18f)
        val scrollOff = animSettingsScroll

        startScissor(contentX, settingsTop, contentW, settingsH)
        var sy = settingsTop + scrollOff

        // 标题
        style.titleFont.drawString("Airi Settings", sx, sy, text.rgb)
        sy += 22f

        // ===== Style 选择 (3 种样式, 3列1行) =====
        style.bodyFont.drawString("Style", sx, sy, muted.rgb)
        val supportedStyles = arrayOf("Minimal", "Card", "Glass")
        val cols = 3
        val styleBtnW = (sliderW - (cols - 1) * 3f) / cols
        val styleBtnH = 18f
        supportedStyles.forEachIndexed { i, name ->
            val row = i / cols
            val col = i % cols
            val bx = sx + col * (styleBtnW + 3f)
            val by = sy + 14f + row * (styleBtnH + 4f)
            val selected = AiriSettings.uiStyle == name
            val hovered = isHovered(bx, by, styleBtnW, styleBtnH, mouseX, mouseY)
            val bg = when {
                selected -> Color(accent.red, accent.green, accent.blue, 200)
                hovered -> if (style.lightTheme) Color(0, 0, 0, 25) else Color(255, 255, 255, 24)
                else -> style.controlBgColor
            }
            RoundedUtil.drawRound(bx, by, styleBtnW, styleBtnH, style.controlRadius, bg)
            val labelColor = if (selected) Color.WHITE.rgb else text.rgb
            val label = trimToWidth(name, (styleBtnW - 6f).toInt())
            style.bodyFont.drawString(label, bx + (styleBtnW - style.bodyFont.getStringWidth(label)) / 2f, by + (styleBtnH - style.bodyFont.fontHeight) / 2f + 1f, labelColor)
        }
        sy += 14f + 1 * (styleBtnH + 4f)

        // ===== Theme 颜色点 =====
        style.bodyFont.drawString("Theme", sx, sy, muted.rgb)
        sy += 18f
        val themePairs = ClickGUI.nlThemeAccents
        val dotSize = 14f
        val dotSpacing = 24f
        var dotX = sx
        themePairs.forEach { (name, color) ->
            val isSelected = ClickGUI.nlTheme == name
            val dotHovered = isHovered(dotX - 2f, sy + 2f, dotSize + 4f, dotSize + 4f, mouseX, mouseY)
            val dotColor = when {
                isSelected -> color
                dotHovered -> Color(color.red, color.green, color.blue, 120)
                else -> Color(color.red, color.green, color.blue, 60)
            }
            RoundedUtil.drawRound(dotX, sy + 4f, dotSize, dotSize, 7f, dotColor)
            if (isSelected && style.useGlow && riseGlowIntensity > 0f) {
                GlowUtils.drawGlow(dotX - 2f, sy + 2f, dotSize + 4f, dotSize + 4f, glowRadius(16), Color(color.red, color.green, color.blue, glowAlpha(180)))
            }
            dotX += dotSpacing
        }
        sy += 26f

        // ===== Glow Intensity 滑块 =====
        style.bodyFont.drawString("Glow Intensity", sx, sy, muted.rgb)
        val glowVal = "%.0f%%".format(riseGlowIntensity * 100)
        style.bodyFont.drawString(glowVal, sx + sliderW - 30f, sy, accent.rgb)
        sy += 14f
        val glowProgress = riseGlowIntensity.coerceIn(0f, 1f)
        RoundedUtil.drawRound(sx, sy, sliderW, 4f, 2f, if (style.lightTheme) Color(0, 0, 0, 40) else Color(48, 54, 68, 255))
        RoundedUtil.drawRound(sx, sy, sliderW * glowProgress, 4f, 2f, accent)
        val glowThumbX = sx + sliderW * glowProgress - 3f
        RoundedUtil.drawRound(glowThumbX, sy - 3f, 10f, 10f, 5f, Color.WHITE)
        if (riseGlowIntensity > 0f && style.useGlow) GlowUtils.drawGlow(glowThumbX - 1f, sy - 4f, 12f, 12f, glowRadius(14), Color(accent.red, accent.green, accent.blue, glowAlpha(160)))
        sy += 18f

        // ===== BG Opacity 滑块 (浅色样式禁用) =====
        if (!style.lightTheme) {
            style.bodyFont.drawString("Background Opacity", sx, sy, muted.rgb)
            val opVal = "%.0f%%".format(riseBgOpacity * 100)
            style.bodyFont.drawString(opVal, sx + sliderW - 30f, sy, accent.rgb)
            sy += 14f
            val opProgress = ((riseBgOpacity - 0.1f) / 0.9f).coerceIn(0f, 1f)
            RoundedUtil.drawRound(sx, sy, sliderW, 4f, 2f, Color(48, 54, 68, 255))
            RoundedUtil.drawRound(sx, sy, sliderW * opProgress, 4f, 2f, accent)
            val opThumbX = sx + sliderW * opProgress - 3f
            RoundedUtil.drawRound(opThumbX, sy - 3f, 10f, 10f, 5f, Color.WHITE)
            sy += 18f
        }

        // ===== Blur 开关 =====
        style.bodyFont.drawString("Blur", sx, sy, muted.rgb)
        val blurEnabled = ClickGUI.nlBlur
        val toggleX = sx + sliderW - 12f
        val toggleY = sy - 2f
        val toggleW = 18f
        val toggleH = 10f
        RoundedUtil.drawRound(toggleX, toggleY, toggleW, toggleH, 5f, if (blurEnabled) accent else if (style.lightTheme) Color(160, 150, 130, 200) else Color(47, 53, 68, 255))
        val dotX2 = if (blurEnabled) toggleX + toggleW - 8f else toggleX + 2f
        RoundedUtil.drawRound(dotX2, toggleY + 2f, 6f, 6f, 3f, Color.WHITE)
        if (blurEnabled && style.useGlow && riseGlowIntensity > 0f) {
            GlowUtils.drawGlow(toggleX, toggleY, toggleW, toggleH, glowRadius(16), Color(accent.red, accent.green, accent.blue, glowAlpha(200)))
        }
        sy += 18f

        // ===== Blur Strength 滑块 =====
        if (blurEnabled) {
            style.bodyFont.drawString("Blur Strength", sx, sy, muted.rgb)
            val blurStr = ClickGUI.nlBlurStrength
            val blurVal = "%.0f".format(blurStr)
            style.bodyFont.drawString(blurVal, sx + sliderW - 30f, sy, accent.rgb)
            sy += 14f
            val blurProgress = ((blurStr - 1f) / 49f).coerceIn(0f, 1f)
            RoundedUtil.drawRound(sx, sy, sliderW, 4f, 2f, if (style.lightTheme) Color(0, 0, 0, 40) else Color(48, 54, 68, 255))
            RoundedUtil.drawRound(sx, sy, sliderW * blurProgress, 4f, 2f, accent)
            val blurThumbX = sx + sliderW * blurProgress - 3f
            RoundedUtil.drawRound(blurThumbX, sy - 3f, 10f, 10f, 5f, Color.WHITE)
            sy += 18f
        }

        // ===== API Configuration =====
        style.bodyFont.drawString("API Configuration", sx, sy, accent.rgb)
        sy += 18f

        val endpointH = 18f

        // Endpoint 输入框 (inline label)
        drawEditableField("endpoint", "Endpoint", AiriSettings.endpoint, endpointBuffer, sx, sy, sliderW, endpointH, mouseX, mouseY)
        sy += endpointH + 6f

        // API Key 输入框 (inline label, 掩码显示)
        val apiKeyDisplay = if (editingField == "apiKey") apiKeyBuffer.toString() else if (AiriSettings.apiKey.isNotEmpty()) "${AiriSettings.apiKey.take(3)}***${AiriSettings.apiKey.takeLast(3)}" else ""
        drawEditableField("apiKey", "API Key", apiKeyDisplay, apiKeyBuffer, sx, sy, sliderW, endpointH, mouseX, mouseY)
        sy += endpointH + 6f

        // 当前模型 field (inline label) + "+" 按钮
        val addBtnW = 22f
        val modelFieldW = sliderW - addBtnW - 4f
        val modelFieldHovered = isHovered(sx, sy, modelFieldW, endpointH, mouseX, mouseY)
        val modelFieldBg = if (modelFieldHovered) (if (style.lightTheme) Color(0, 0, 0, 20) else Color(255, 255, 255, 20)) else style.inputBgColor
        RoundedUtil.drawRound(sx, sy, modelFieldW, endpointH, style.controlRadius, modelFieldBg)
        val modelLabelStr = "Model: "
        val modelLabelW = style.bodyFont.getStringWidth(modelLabelStr)
        style.bodyFont.drawString(modelLabelStr, sx + 6f, sy + (endpointH - style.bodyFont.fontHeight) / 2f + 1f, muted.rgb)
        val modelDisplay = trimToWidth(AiriSettings.model, (modelFieldW - modelLabelW - 14f).toInt())
        style.bodyFont.drawString(modelDisplay, sx + 6f + modelLabelW, sy + (endpointH - style.bodyFont.fontHeight) / 2f + 1f, text.rgb)
        // + 按钮
        val addBtnX = sx + modelFieldW + 4f
        val addBtnY = sy
        val addHovered = isHovered(addBtnX, addBtnY, addBtnW, endpointH, mouseX, mouseY)
        val addBg = if (addHovered) Color(accent.red, accent.green, accent.blue, 220) else accent
        RoundedUtil.drawRound(addBtnX, addBtnY, addBtnW, endpointH, style.controlRadius, addBg)
        style.bodyFont.drawString("+", addBtnX + (addBtnW - style.bodyFont.getStringWidth("+")) / 2f, addBtnY + (endpointH - style.bodyFont.fontHeight) / 2f + 1f, Color.WHITE.rgb)
        sy += endpointH + 6f

        // 添加模型输入框 (仅当点击 + 后显示)
        if (editingField == "newModel") {
            drawEditableField("newModel", "New Model", "", newModelBuffer, sx, sy, sliderW, endpointH, mouseX, mouseY)
            sy += endpointH + 4f
            style.bodyFont.drawString("Enter to add / Esc to cancel", sx, sy, muted.rgb)
            sy += 14f
        }

        // 模型列表 (紧凑)
        val models = AiriSettings.models
        val mItemH = 14f
        models.forEach { m ->
            val isCurrent = m == AiriSettings.model
            val mHovered = isHovered(sx, sy, sliderW - 16f, mItemH, mouseX, mouseY)
            if (isCurrent) {
                RoundedUtil.drawRound(sx, sy, sliderW - 16f, mItemH, 3f, Color(accent.red, accent.green, accent.blue, 80))
            } else if (mHovered) {
                val hoverCol = if (style.lightTheme) Color(0, 0, 0, 25) else Color(255, 255, 255, 24)
                RoundedUtil.drawRound(sx, sy, sliderW - 16f, mItemH, 3f, hoverCol)
            }
            val mLabel = trimToWidth(m, (sliderW - 30f).toInt())
            style.bodyFont.drawString(mLabel, sx + 4f, sy + 3f, if (isCurrent) Color.WHITE.rgb else text.rgb)
            // 删除小按钮
            val delX = sx + sliderW - 14f
            val delHovered = isHovered(delX, sy, 12f, mItemH, mouseX, mouseY)
            style.bodyFont.drawString("x", delX + 2f, sy + 3f, if (delHovered) Color(255, 80, 80, 255).rgb else muted.rgb)
            sy += mItemH
        }
        endScissor()

        // 滚动条
        if (contentH > settingsH) {
            val barH = (settingsH * (settingsH / contentH)).coerceAtLeast(20f)
            val progress = (-animSettingsScroll / (contentH - settingsH)).coerceIn(0f, 1f)
            val barX = contentX + contentW - 5f
            val trackCol = if (style.lightTheme) Color(0, 0, 0, 30) else Color(255, 255, 255, 28)
            RoundedUtil.drawRound(barX, settingsTop, 2f, settingsH, 1f, trackCol)
            RoundedUtil.drawRound(barX, settingsTop + (settingsH - barH) * progress, 2f, barH, 1f, accent)
        }
    }

    /** 估算 Settings 面板内容总高度 (用于滚动计算) */
    private fun estimateSettingsHeight(): Float {
        var h = 22f + 36f + 26f + 18f + 18f // 标题 + Style(3列1行=14+1*22=36) + Theme + Glow + slider
        if (!style.lightTheme) h += 18f      // Opacity
        h += 18f                              // Blur toggle
        if (ClickGUI.nlBlur) h += 18f         // Blur Strength
        h += 18f + 18f + 6f + 18f + 6f + 18f + 6f // API title + Endpoint + API Key + Model
        if (editingField == "newModel") h += 18f + 14f
        h += AiriSettings.models.size * 14f   // 模型列表
        return h + 8f
    }

    /**
     * 绘制可编辑文本字段 (inline label: 标签在输入框内部左侧,不占用额外垂直空间)
     *
     * @param id 字段 id: "endpoint" | "apiKey" | "newModel"
     * @param label 标签 (显示在 field 内部左侧)
     * @param currentValue 当前实际值 (非编辑状态下显示)
     * @param buffer 编辑时使用的 StringBuilder
     * @param isSecret 是否掩码显示 (apiKey 非编辑时掩码)
     */
    private fun drawEditableField(
        id: String, label: String, currentValue: String, buffer: StringBuilder,
        x: Float, y: Float, w: Float, h: Float,
        mouseX: Int, mouseY: Int, isSecret: Boolean = false
    ) {
        val isEditing = editingField == id
        val hovered = isHovered(x, y, w, h, mouseX, mouseY)
        val bg = when {
            isEditing -> if (style.lightTheme) Color(255, 252, 240, 255) else Color(28, 32, 44, 255)
            hovered -> if (style.lightTheme) Color(0, 0, 0, 20) else Color(255, 255, 255, 20)
            else -> style.inputBgColor
        }
        RoundedUtil.drawRound(x, y, w, h, style.controlRadius, bg)
        if (isEditing) {
            RenderUtils.drawRect(x, y + h - 1f, x + w, y + h, accent.rgb)
        }
        // inline label (左侧)
        val labelStr = "$label: "
        val labelW = style.bodyFont.getStringWidth(labelStr)
        style.bodyFont.drawString(labelStr, x + 6f, y + (h - style.bodyFont.fontHeight) / 2f + 1f, muted.rgb)
        // 值/编辑内容 (右侧)
        val displayText = if (isEditing) buffer.toString() else currentValue
        val valueMaxW = (w - labelW - 14f).coerceAtLeast(20f).toInt()
        val trimmed = trimToWidth(displayText, valueMaxW)
        val valueColor = if (displayText.isEmpty() && !isEditing) muted.rgb else text.rgb
        style.bodyFont.drawString(trimmed, x + 6f + labelW, y + (h - style.bodyFont.fontHeight) / 2f + 1f, valueColor)
        // 光标
        if (isEditing && cursorBlink < 30) {
            val cursorX = x + 6f + labelW + style.bodyFont.getStringWidth(trimmed)
            RenderUtils.drawRect(cursorX, y + 3f, cursorX + 1f, y + h - 3f, accent.rgb)
        }
    }

    private fun drawDeleteConfirm(conv: Conversation, mouseX: Int, mouseY: Int) {
        // 遮罩
        RenderUtils.drawRect(x, y, x + w, y + h, Color(0, 0, 0, 120).rgb)

        val dlgW = 240f
        val dlgH = 110f
        val dlgX = x + (w - dlgW) / 2f
        val dlgY = y + (h - dlgH) / 2f
        val dlgBg = if (style.lightTheme) Color(248, 242, 226, 252) else Color(20, 24, 36, 250)
        RoundedUtil.drawRound(dlgX, dlgY, dlgW, dlgH, style.cornerRadius, dlgBg)
        RoundedUtil.drawRound(dlgX, dlgY, dlgW, dlgH, style.cornerRadius, Color(accent.red, accent.green, accent.blue, 60))

        style.titleFont.drawString("Delete Conversation?", dlgX + 12f, dlgY + 12f, text.rgb)
        val title = trimToWidth(conv.title, (dlgW - 24f).toInt())
        style.bodyFont.drawString(" \"$title\" will be permanently deleted.", dlgX + 12f, dlgY + 36f, muted.rgb)

        // 按钮
        val btnW = 80f
        val btnH = 22f
        val btnY = dlgY + dlgH - btnH - 12f
        val cancelX = dlgX + dlgW / 2f - btnW - 4f
        val deleteX = dlgX + dlgW / 2f + 4f

        val cancelHover = isHovered(cancelX, btnY, btnW, btnH, mouseX, mouseY)
        val deleteHover = isHovered(deleteX, btnY, btnW, btnH, mouseX, mouseY)
        RoundedUtil.drawRound(cancelX, btnY, btnW, btnH, style.controlRadius, if (cancelHover) Color(80, 85, 100, 240) else Color(50, 55, 70, 220))
        RoundedUtil.drawRound(deleteX, btnY, btnW, btnH, style.controlRadius, if (deleteHover) Color(220, 60, 60, 240) else Color(180, 50, 50, 220))
        style.bodyFont.drawString("Cancel", cancelX + (btnW - style.bodyFont.getStringWidth("Cancel")) / 2f, btnY + 7f, text.rgb)
        style.bodyFont.drawString("Delete", deleteX + (btnW - style.bodyFont.getStringWidth("Delete")) / 2f, btnY + 7f, Color.WHITE.rgb)
    }

    // ===== Click handlers =====

    private fun clickSidebar(mouseX: Int, mouseY: Int, mouseButton: Int) {
        if (mouseButton != 0) return

        // 对话列表项点击
        val listTop = y + headerH + 6f
        val listBottom = y + h - 36f - 6f
        val convs = ConversationManager.all()
        val itemH = 24f

        var iy = listTop + animListScroll
        convs.forEach { conv ->
            // 对话项本体
            if (isHovered(x + 4f, iy, sidebarW - 22f, itemH, mouseX, mouseY)) {
                ConversationManager.switchTo(conv.id)
                pendingStickToBottom = true
                showSettings = false
                return
            }
            // 删除图标
            val closeIconX = x + sidebarW - 18f
            val closeIconY = iy + 6f
            if (isHovered(closeIconX, closeIconY, 12f, 12f, mouseX, mouseY)) {
                pendingDeleteConv = conv
                return
            }
            iy += itemH
        }

        // 底部按钮
        val bottomY = y + h - 36f
        if (isHovered(x + 6f, bottomY, sidebarW - 12f, 16f, mouseX, mouseY)) {
            // New Chat
            ConversationManager.createConversation()
            pendingStickToBottom = true
            inputBuffer.clear()
            inputFocused = true
            showSettings = false
            return
        }
        val settingsY = bottomY + 18f
        if (isHovered(x + 6f, settingsY, sidebarW - 12f, 16f, mouseX, mouseY)) {
            showSettings = !showSettings
            inputFocused = false
            return
        }
    }

    private fun clickHeader(mouseX: Int, mouseY: Int, mouseButton: Int) {
        // Header 区域无交互(标题展示)
    }

    private fun clickChatArea(mouseX: Int, mouseY: Int, mouseButton: Int) {
        // 点击聊天区取消输入焦点(可选)
    }

    private fun clickInputBox(mouseX: Int, mouseY: Int, mouseButton: Int) {
        if (mouseButton != 0) return
        val ix = contentX + 12f
        val iy = inputTop + 6f
        val iw = contentW - 24f
        val ih = inputH - 12f

        // 发送按钮(右侧) - 与 drawInputBox 保持一致的坐标
        val sendBtnW = 44f
        val sendBtnH = 18f
        val sendBtnX = ix + iw - sendBtnW - 6f
        val sendBtnY = iy + (ih - sendBtnH) / 2f
        if (isHovered(sendBtnX, sendBtnY, sendBtnW, sendBtnH, mouseX, mouseY)) {
            sendCurrentInput()
            return
        }
        // 输入框本体 (排除发送按钮区域)
        if (isHovered(ix, iy, iw - sendBtnW - 12f, ih, mouseX, mouseY)) {
            inputFocused = true
            return
        }
        // 点击其他区域取消焦点
        inputFocused = false
    }

    private fun clickControlBar(mouseX: Int, mouseY: Int, mouseButton: Int) {
        if (mouseButton != 0) return
        val barY = controlTop + 5f
        val barH = controlBarH - 10f
        val layout = controlBarLayout()

        for (btn in layout) {
            if (!isHovered(btn.x, barY, btn.w, barH, mouseX, mouseY)) continue
            if (btn.isToggle) {
                when (btn.id) {
                    "interact" -> {
                        AiriSettings.interactionAllowed = !AiriSettings.interactionAllowed
                        saveConfig(airiConfig)
                    }
                    "think" -> {
                        AiriSettings.thinkEnabled = !AiriSettings.thinkEnabled
                        saveConfig(airiConfig)
                    }
                }
            } else {
                toggleDropdown(btn.id)
            }
            return
        }
    }

    private fun toggleDropdown(id: String) {
        openDropdown = if (openDropdown == id) null else id
    }

    private fun handleDropdownClick(mouseX: Int, mouseY: Int, mouseButton: Int) {
        if (mouseButton != 0) {
            openDropdown = null
            return
        }
        val id = openDropdown ?: return
        val barY = controlTop + 5f
        val barH = controlBarH - 10f

        val btn = controlBarLayout().firstOrNull { it.id == id } ?: run { openDropdown = null; return }
        val dropX = btn.x
        val dropW = btn.w

        val items = when (id) {
            "mode" -> listOf("chat", "script", "roleplay")
            "model" -> AiriSettings.models.toList()
            "thinkstr" -> listOf("0.00", "0.25", "0.50", "0.75", "1.00")
            "role" -> listOf("prankster", "helper", "observer", "custom")
            else -> { openDropdown = null; return }
        }

        val dropY = barY + barH + 2f
        val itemH = 16f
        val dropH = items.size * itemH + 4f
        val actualDropX = dropX.coerceIn(x + 4f, x + w - dropW - 4f)
        val actualDropY = dropY.coerceAtMost(y + h - dropH - 4f)

        // 点击下拉项
        var iy = actualDropY + 2f
        items.forEach { item ->
            if (isHovered(actualDropX, iy, dropW, itemH, mouseX, mouseY)) {
                when (id) {
                    "mode" -> AiriSettings.mode = item
                    "model" -> AiriSettings.model = item
                    "thinkstr" -> AiriSettings.thinkStrength = item.toFloatOrNull() ?: 0.5f
                    "role" -> AiriSettings.role = item
                }
                saveConfig(airiConfig)
                openDropdown = null
                return
            }
            iy += itemH
        }

        // 点击其他区域关闭下拉
        openDropdown = null
    }

    private fun handleDeleteConfirmClick(mouseX: Int, mouseY: Int, mouseButton: Int) {
        if (mouseButton != 0) return
        val conv = pendingDeleteConv ?: return

        val dlgW = 240f
        val dlgH = 110f
        val dlgX = x + (w - dlgW) / 2f
        val dlgY = y + (h - dlgH) / 2f
        val btnW = 80f
        val btnH = 22f
        val btnY = dlgY + dlgH - btnH - 12f
        val cancelX = dlgX + dlgW / 2f - btnW - 4f
        val deleteX = dlgX + dlgW / 2f + 4f

        if (isHovered(cancelX, btnY, btnW, btnH, mouseX, mouseY)) {
            pendingDeleteConv = null
            return
        }
        if (isHovered(deleteX, btnY, btnW, btnH, mouseX, mouseY)) {
            ConversationManager.deleteConversation(conv.id)
            pendingDeleteConv = null
            pendingStickToBottom = true
            saveConfig(airiConfig)
            return
        }
    }

    private fun handleSidebarSliderDrag(mouseX: Int) {
        val sx = contentX + 13f
        val sliderW = contentW - 26f
        val progress = ((mouseX - sx) / sliderW).coerceIn(0f, 1f)

        when (sidebarDraggingSlider) {
            "glow" -> {
                ClickGUI.nlGlowIntensityValue.set(progress, false)
                valuesDirty = true
            }
            "opacity" -> {
                val value = 0.1f + 0.9f * progress
                ClickGUI.nlBgOpacityValue.set(value, false)
                valuesDirty = true
            }
            "blur" -> {
                val value = 1f + 49f * progress
                ClickGUI.nlBlurStrengthValue.set(value, false)
                valuesDirty = true
            }
        }
    }

    private fun clickSettings(contentX: Float, contentW: Float, mouseX: Int, mouseY: Int, mouseButton: Int) {
        if (mouseButton != 0) return
        val sx = contentX + 13f
        val sliderW = contentW - 26f
        // 与 drawSettingsContent 保持一致的 sy 累进 + 滚动偏移
        val scrollOff = animSettingsScroll
        var sy = y + 10f + scrollOff

        // 标题 (无交互)
        sy += 22f

        // ===== Style 按钮点击 (3列1行) =====
        val supportedStyles = arrayOf("Minimal", "Card", "Glass")
        val cols = 3
        val styleBtnW = (sliderW - (cols - 1) * 3f) / cols
        val styleBtnH = 18f
        supportedStyles.forEachIndexed { i, name ->
            val row = i / cols
            val col = i % cols
            val bx = sx + col * (styleBtnW + 3f)
            val by = sy + 14f + row * (styleBtnH + 4f)
            if (isHovered(bx, by, styleBtnW, styleBtnH, mouseX, mouseY)) {
                if (AiriSettings.uiStyle != name) {
                    AiriSettings.uiStyle = name
                    saveConfig(airiConfig)
                    editingField = null
                    endpointBuffer.clear()
                    apiKeyBuffer.clear()
                    newModelBuffer.clear()
                }
                return
            }
        }
        sy += 14f + 1 * (styleBtnH + 4f)

        // ===== Theme 颜色点 =====
        sy += 18f // label
        val dotSize = 14f
        val dotSpacing = 24f
        var dotX = sx
        ClickGUI.nlThemeAccents.forEach { (name, _) ->
            if (isHovered(dotX - 2f, sy + 2f, dotSize + 4f, dotSize + 4f, mouseX, mouseY)) {
                ClickGUI.nlThemeValue.set(name, false)
                valuesDirty = true
                return
            }
            dotX += dotSpacing
        }
        sy += 26f

        // ===== Glow slider =====
        sy += 14f // label + slider offset
        if (isHovered(sx, sy - 5f, sliderW, 14f, mouseX, mouseY)) {
            sidebarDraggingSlider = "glow"
            handleSidebarSliderDrag(mouseX)
            return
        }
        sy += 18f - 14f + 14f // = 18f

        // ===== Opacity slider (仅深色) =====
        if (!style.lightTheme) {
            sy += 14f
            if (isHovered(sx, sy - 5f, sliderW, 14f, mouseX, mouseY)) {
                sidebarDraggingSlider = "opacity"
                handleSidebarSliderDrag(mouseX)
                return
            }
            sy += 18f
        }

        // ===== Blur toggle =====
        val blurToggleX = sx + sliderW - 12f
        val blurToggleY = sy - 2f
        if (isHovered(blurToggleX - 2f, blurToggleY - 2f, 22f, 14f, mouseX, mouseY)) {
            ClickGUI.nlBlurValue.set(!ClickGUI.nlBlur, false)
            valuesDirty = true
            return
        }
        sy += 18f

        // ===== Blur Strength slider (仅 blurEnabled) =====
        if (ClickGUI.nlBlur) {
            sy += 14f
            if (isHovered(sx, sy - 5f, sliderW, 14f, mouseX, mouseY)) {
                sidebarDraggingSlider = "blur"
                handleSidebarSliderDrag(mouseX)
                return
            }
            sy += 18f
        }

        // ===== API Configuration =====
        sy += 18f // API 标题

        val endpointH = 18f

        // Endpoint field
        if (isHovered(sx, sy, sliderW, endpointH, mouseX, mouseY)) {
            editingField = "endpoint"
            endpointBuffer.clear()
            endpointBuffer.append(AiriSettings.endpoint)
            return
        }
        sy += endpointH + 6f

        // API Key field
        if (isHovered(sx, sy, sliderW, endpointH, mouseX, mouseY)) {
            editingField = "apiKey"
            apiKeyBuffer.clear()
            apiKeyBuffer.append(AiriSettings.apiKey)
            return
        }
        sy += endpointH + 6f

        // Model field + "+" 按钮
        val addBtnW = 22f
        val modelFieldW = sliderW - addBtnW - 4f
        val addBtnX = sx + modelFieldW + 4f
        if (isHovered(addBtnX, sy, addBtnW, endpointH, mouseX, mouseY)) {
            if (editingField == "newModel") {
                commitNewModel()
            } else {
                editingField = "newModel"
                newModelBuffer.clear()
            }
            return
        }
        // 点击 model field 打开 model 下拉
        if (isHovered(sx, sy, modelFieldW, endpointH, mouseX, mouseY)) {
            toggleDropdown("model")
            return
        }
        sy += endpointH + 6f

        // 添加模型输入框 (仅 newModel 模式)
        if (editingField == "newModel") {
            if (isHovered(sx, sy, sliderW, endpointH, mouseX, mouseY)) return
            sy += endpointH + 4f + 14f
        }

        // 模型列表项点击 (切换) / 删除按钮
        val mItemH = 14f
        AiriSettings.models.forEach { m ->
            if (isHovered(sx, sy, sliderW - 16f, mItemH, mouseX, mouseY)) {
                AiriSettings.model = m
                saveConfig(airiConfig)
                return
            }
            val delX = sx + sliderW - 14f
            if (isHovered(delX, sy, 12f, mItemH, mouseX, mouseY)) {
                if (AiriSettings.models.size > 1) {
                    AiriSettings.models.remove(m)
                    if (AiriSettings.model == m) {
                        AiriSettings.model = AiriSettings.models.first()
                    }
                    saveConfig(airiConfig)
                }
                return
            }
            sy += mItemH
        }

        // 点击空白区域,若有正在编辑的字段则提交
        if (editingField != null) {
            commitEditingField()
        }
    }

    /** 提交新模型添加 */
    private fun commitNewModel() {
        val name = newModelBuffer.toString().trim()
        if (name.isNotEmpty() && name !in AiriSettings.models) {
            AiriSettings.models.add(name)
            AiriSettings.model = name
            saveConfig(airiConfig)
        }
        editingField = null
        newModelBuffer.clear()
    }

    /** 提交当前编辑的字段 */
    private fun commitEditingField() {
        when (editingField) {
            "endpoint" -> {
                val v = endpointBuffer.toString().trim()
                if (v.isNotEmpty()) {
                    AiriSettings.endpoint = v
                    saveConfig(airiConfig)
                }
                endpointBuffer.clear()
            }
            "apiKey" -> {
                val v = apiKeyBuffer.toString().trim()
                if (v.isNotEmpty()) {
                    AiriSettings.apiKey = v
                    saveConfig(airiConfig)
                }
                apiKeyBuffer.clear()
            }
            "newModel" -> {
                commitNewModel()
                return
            }
        }
        editingField = null
    }

    private fun handleWheel(mouseX: Int, mouseY: Int) {
        val wheel = Mouse.getDWheel()
        if (wheel == 0) return

        // Settings 面板滚动 (优先于聊天区)
        if (showSettings && isHovered(contentX, y + 10f, contentW, h - 16f, mouseX, mouseY)) {
            val contentH = estimateSettingsHeight()
            val settingsH = h - 16f
            val maxScrollDown = (contentH - settingsH).coerceAtLeast(0f)
            settingsScroll = (settingsScroll + if (wheel > 0) 24f else -24f).coerceIn(-maxScrollDown, 0f)
            return
        }

        // 对话列表滚动
        val listTop = y + headerH + 6f
        val listBottom = y + h - 36f - 6f
        val listHovered = isHovered(x, listTop, sidebarW, listBottom - listTop, mouseX, mouseY)
        if (listHovered) {
            val convs = ConversationManager.all()
            val totalH = convs.size * 24f
            val visibleH = listBottom - listTop
            val maxDown = (totalH - visibleH).coerceAtLeast(0f)
            listScroll = (listScroll + if (wheel > 0) 18f else -18f).coerceIn(-maxDown, 0f)
            return
        }

        // 聊天区滚动
        if (!showSettings && isHovered(contentX, chatTop, contentW, chatH, mouseX, mouseY)) {
            // 用户主动滚轮,取消贴底
            pendingStickToBottom = false
            chatScroll = (chatScroll + if (wheel > 0) 36f else -36f)
            // 限制上界(下界由 drawScreen 计算)
            if (chatScroll > 0f) chatScroll = 0f
            return
        }
    }

    private fun sendCurrentInput() {
        val text = inputBuffer.toString().trim()
        if (text.isBlank() || sending) return
        if (AiriSettings.apiKey.isBlank()) {
            // 直接写入错误消息
            val conv = ConversationManager.current
            conv.messages.add(Message(role = "assistant", content = "", error = "API key not set. Use .airi key <sk-...> to set it."))
            pendingStickToBottom = true
            inputBuffer.clear()
            return
        }

        inputBuffer.clear()
        pendingStickToBottom = true
        sending = true

        // 速率限制
        val now = System.currentTimeMillis()
        if (now - AiriSettings.lastRequestTimestamp < 60_000L) {
            if (AiriSettings.requestCountInWindow >= AiriSettings.rateLimitPerMinute) {
                val conv = ConversationManager.current
                conv.messages.add(Message(role = "assistant", content = "", error = "Rate limit exceeded. Try again later."))
                pendingStickToBottom = true
                sending = false
                return
            }
            AiriSettings.requestCountInWindow++
        } else {
            AiriSettings.lastRequestTimestamp = now
            AiriSettings.requestCountInWindow = 1
        }

        ConversationManager.appendUserMessage(text)

        SharedScopes.IO.launch {
            // 流式调用,UI 通过 conv.messages 中 draft 的可变 content 字段渲染增量
            // pendingDraftUpdated 由 drawScreen 自动重绘触发即可(每帧重绘,无需额外通知)
            ConversationManager.sendCurrentStream()
            sending = false
            pendingStickToBottom = true
            saveConfig(airiConfig)
        }
    }

    private fun flushConfigs() {
        if (valuesDirty) {
            saveConfig(net.ccbluex.liquidbounce.file.FileManager.valuesConfig)
            valuesDirty = false
        }
    }

    // ===== Utility =====

    private fun drawCircle(cx: Float, cy: Float, radius: Float, color: Color) {
        if (radius <= 0f) return
        RenderUtils.drawFilledCircle(cx + radius / 2f, cy + radius / 2f, radius / 2f, color)
    }

    private fun animate(current: Float, target: Float, speed: Float): Float {
        if (abs(target - current) < 0.01f) return target
        return current + (target - current) * speed
    }

    private fun easeOut(value: Float): Float {
        val clamped = value.coerceIn(0f, 1f)
        return 1f - (1f - clamped) * (1f - clamped)
    }

    private fun startScissor(sx: Float, sy: Float, sw: Float, sh: Float) {
        val sr = ScaledResolution(mc)
        val factor = sr.scaleFactor
        GL11.glEnable(GL11.GL_SCISSOR_TEST)
        GL11.glScissor(
            (sx * factor).toInt(),
            ((sr.scaledHeight - sy - sh) * factor).toInt(),
            (sw * factor).toInt(),
            (sh * factor).toInt()
        )
    }

    private fun endScissor() = GL11.glDisable(GL11.GL_SCISSOR_TEST)

    private fun trimToWidth(text: String, maxWidth: Int): String {
        if (text.isEmpty() || style.bodyFont.getStringWidth(text) <= maxWidth) return text
        var trimmed = text
        while (trimmed.isNotEmpty() && style.bodyFont.getStringWidth("...$trimmed") > maxWidth) {
            trimmed = trimmed.dropLast(1)
        }
        return if (trimmed.isEmpty()) "..." else "...$trimmed"
    }

    private fun isHovered(hx: Float, hy: Float, hw: Float, hh: Float, mouseX: Int, mouseY: Int) =
        mouseX >= hx && mouseX <= hx + hw && mouseY >= hy && mouseY <= hy + hh

    private companion object {
        var lastX = -1f
        var lastY = -1f
        var lastDisplayedAccent: Color? = null
        var lastDisplayedBg: Color? = null

        val closeIcon: ResourceLocation = ResourceLocation("airclient/clickgui/close.png")
        val menuIcon: ResourceLocation = ResourceLocation("airclient/clickgui/back.png")
        val settingIcon: ResourceLocation = ResourceLocation("airclient/clickgui/setting.png")
    }
}
