/*
 * Air Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 */
package net.ccbluex.liquidbounce.ui.client.music

import kotlinx.coroutines.launch
import net.ccbluex.liquidbounce.features.module.modules.music.MusicPlayer
import net.ccbluex.liquidbounce.features.module.modules.music.core.LocalMusicSource
import net.ccbluex.liquidbounce.features.module.modules.music.core.Track
import net.ccbluex.liquidbounce.features.module.modules.music.core.TrackSource
import net.ccbluex.liquidbounce.ui.font.AWTFontRenderer.Companion.assumeNonVolatile
import net.ccbluex.liquidbounce.ui.font.Fonts
import net.ccbluex.liquidbounce.utils.kotlin.SharedScopes
import net.ccbluex.liquidbounce.utils.render.RenderUtils
import net.ccbluex.liquidbounce.utils.render.RoundedUtil
import net.ccbluex.liquidbounce.utils.ui.AbstractScreen
import net.minecraft.client.gui.GuiScreen
import net.minecraft.client.gui.GuiTextField
import net.minecraft.client.gui.ScaledResolution
import net.minecraft.util.ResourceLocation
import org.lwjgl.input.Keyboard
import org.lwjgl.input.Mouse
import org.lwjgl.opengl.GL11
import java.awt.Color
import java.awt.Desktop
import kotlin.math.abs

/**
 * 音乐播放器界面 - 自定义渲染版本
 *
 * 布局:
 *   - 顶部 Header(52px): music.png 图标 + "Music Player" 标题 + 状态 + 搜索框 + 返回按钮
 *   - Tab 栏(32px): 本地音乐 / 网易云 / 播放队列 + 右侧搜索按钮
 *   - 主区列表: 自定义渲染(双行: 歌名+来源), 滚动+悬停高亮
 *   - 右侧控制栏(96px): 圆角文字按钮 + 音量条 + +/-
 *   - 底部 Now Playing(96px): 歌名 + 信息 + 歌词 + 进度条
 */
class GuiMusicPlayer(private val prevGui: GuiScreen?) : AbstractScreen() {

    private enum class Tab(val label: String) {
        LOCAL("本地音乐"),
        NETEASE("网易云"),
        QUEUE("播放队列")
    }

    // ===== 配色 =====
    private val bgColor = Color(18, 22, 32, 235)
    private val headerBgColor = Color(14, 16, 22, 240)
    private val bottomBgColor = Color(14, 16, 22, 245)
    private val accent = Color(0, 160, 255)
    private val textColor = Color(232, 234, 240)
    private val mutedColor = Color(170, 175, 188)
    private val dimColor = Color(120, 125, 138)
    private val hoverColor = Color(255, 255, 255, 18)
    private val activeColor = Color(0, 160, 255, 80)
    private val selectedColor = Color(0, 160, 255, 40)
    private val trackBgColor = Color(0, 0, 0, 60)
    private val separatorColor = Color(255, 255, 255, 18)

    // ===== 布局尺寸 =====
    private val headerH = 52f
    private val tabH = 32f
    private val bottomH = 96f
    private val sidebarW = 96f
    private val trackH = 32f

    // ===== 状态 =====
    private lateinit var searchField: GuiTextField
    private var statusText: String = "就绪"
    private var currentTab = Tab.LOCAL
    private var searchResults = emptyList<Track>()
    private var selectedIndex = -1
    private var listScroll = 0f
    private var animListScroll = 0f
    private var lastClickTime = 0L
    private var lastClickedIndex = -1

    // ===== 图标 =====
    private val backIcon = ResourceLocation("airclient/clickgui/back.png")
    private val searchIcon = ResourceLocation("airclient/clickgui/search.png")
    private val musicIcon = ResourceLocation("airclient/tabgui/music.png")

    // ===== 右侧控制按钮 =====
    private val sidebarBtns = listOf(
        "play" to "▶ 播放",
        "queue" to "+ 队列",
        "prev" to "◄ 上一首",
        "next" to "► 下一首",
        "stop" to "■ 停止",
        "refresh" to "↻ 刷新",
        "folder" to "▣ 目录"
    )

    override fun initGui() {
        Keyboard.enableRepeatEvents(true)
        statusText = "就绪"
        searchField = textField(100, Fonts.fontSemibold35, 0, 0, 200, 16) {
            maxStringLength = 128
        }
        refreshListSelection()
    }

    override fun onGuiClosed() {
        Keyboard.enableRepeatEvents(false)
        super.onGuiClosed()
    }

    override fun doesGuiPauseGame() = false

    override fun drawScreen(mouseX: Int, mouseY: Int, partialTicks: Float) {
        assumeNonVolatile {
            drawDefaultBackground()
            // 整体背景
            RenderUtils.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgColor.rgb)

            drawHeader(mouseX, mouseY)
            drawTabBar(mouseX, mouseY)
            drawTrackList(mouseX, mouseY)
            drawControlSidebar(mouseX, mouseY)
            drawNowPlaying(mouseX, mouseY)
        }
        super.drawScreen(mouseX, mouseY, partialTicks)
    }

    // ===== 顶部 Header =====
    private fun drawHeader(mouseX: Int, mouseY: Int) {
        RenderUtils.drawRect(0f, 0f, width.toFloat(), headerH, headerBgColor.rgb)

        // 音乐图标 (左)
        RenderUtils.drawImage(musicIcon, 16, 12, 28, 28, accent)

        // 标题 (图标右侧, fontRise40 + 1.25x)
        val titleScale = 1.25f
        GL11.glPushMatrix()
        GL11.glTranslatef(54f, (headerH - Fonts.fontRise40.FONT_HEIGHT * titleScale) / 2f - 4f, 0f)
        GL11.glScalef(titleScale, titleScale, 1f)
        Fonts.fontRise40.drawString("Music Player", 0f, 0f, textColor.rgb)
        GL11.glPopMatrix()

        // 状态文字 (标题下方)
        Fonts.fontSemibold35.drawString(statusText, 54f, headerH - 16f, mutedColor.rgb)

        // 搜索框 (中间偏右)
        val searchX = (width - 280f).coerceAtLeast(360f)
        val searchY = (headerH - 24f) / 2f
        val searchW = 220f
        val searchH = 24f
        val searchHovered = isHovered(searchX, searchY, searchW, searchH, mouseX, mouseY)
        val searchBg = if (searchHovered || searchField.isFocused) Color(0, 0, 0, 130) else Color(0, 0, 0, 100)
        RoundedUtil.drawRound(searchX, searchY, searchW, searchH, 6f, searchBg)
        RenderUtils.drawImage(searchIcon, (searchX + 6).toInt(), (searchY + 4).toInt(), 16, 16, mutedColor)
        searchField.xPosition = (searchX + 28).toInt()
        searchField.yPosition = (searchY + 4).toInt()
        searchField.width = (searchW - 36).toInt()
        searchField.height = 16
        searchField.drawTextBox()
        if (searchField.text.isEmpty() && !searchField.isFocused) {
            Fonts.fontSemibold35.drawString("搜索关键词或歌曲 ID", searchField.xPosition + 4f, searchField.yPosition + 4f, dimColor.rgb)
        }

        // 返回按钮 (右上角)
        val backBtnSize = 18f
        val backBtnX = width - 36f
        val backBtnY = (headerH - backBtnSize) / 2f
        val backHovered = isHovered(backBtnX, backBtnY, backBtnSize, backBtnSize, mouseX, mouseY)
        if (backHovered) {
            RoundedUtil.drawRound(backBtnX - 4f, backBtnY - 4f, backBtnSize + 8f, backBtnSize + 8f, 4f, hoverColor)
        }
        RenderUtils.drawImage(backIcon, backBtnX.toInt(), backBtnY.toInt(), backBtnSize.toInt(), backBtnSize.toInt(), if (backHovered) accent else textColor)

        // 分隔线
        RenderUtils.drawRect(0f, headerH - 1f, width.toFloat(), headerH, separatorColor.rgb)
    }

    // ===== Tab 栏 =====
    private fun drawTabBar(mouseX: Int, mouseY: Int) {
        val tabY = headerH
        RenderUtils.drawRect(0f, tabY, width.toFloat(), tabY + tabH, Color(0, 0, 0, 40).rgb)

        val tabW = 110f
        val tabGap = 4f
        var tx = 16f
        for (tab in Tab.values()) {
            val isActive = currentTab == tab
            val hovered = isHovered(tx, tabY + 4f, tabW, tabH - 8f, mouseX, mouseY)
            val bg = when {
                isActive -> activeColor
                hovered -> hoverColor
                else -> null
            }
            bg?.let { RoundedUtil.drawRound(tx, tabY + 4f, tabW, tabH - 8f, 4f, it) }
            val tc = if (isActive) accent.rgb else mutedColor.rgb
            val labelW = Fonts.fontSemibold35.getStringWidth(tab.label)
            Fonts.fontSemibold35.drawString(tab.label, tx + (tabW - labelW) / 2f, tabY + (tabH - Fonts.fontSemibold35.FONT_HEIGHT) / 2f + 1f, tc)
            tx += tabW + tabGap
        }

        // 搜索按钮 (Tab 栏右侧, 紧邻搜索框)
        val searchTabX = (width - 280f).coerceAtLeast(360f) - 50f
        val searchTabW = 40f
        val searchTabHovered = isHovered(searchTabX, tabY + 4f, searchTabW, tabH - 8f, mouseX, mouseY)
        if (searchTabHovered) {
            RoundedUtil.drawRound(searchTabX, tabY + 4f, searchTabW, tabH - 8f, 4f, hoverColor)
        }
        RenderUtils.drawImage(searchIcon, (searchTabX + 12).toInt(), (tabY + 9).toInt(), 14, 14, if (searchTabHovered) accent else mutedColor)

        // 分隔线
        RenderUtils.drawRect(0f, tabY + tabH - 1f, width.toFloat(), tabY + tabH, separatorColor.rgb)
    }

    // ===== 主区列表 =====
    private fun drawTrackList(mouseX: Int, mouseY: Int) {
        val listTop = headerH + tabH + 4f
        val listBottom = height - bottomH - 4f
        val listH = listBottom - listTop
        val listLeft = 16f
        val listRight = width - sidebarW - 16f
        val listW = listRight - listLeft

        // 列表背景
        RoundedUtil.drawRound(listLeft, listTop, listW, listH, 6f, trackBgColor)

        // 滚动逻辑
        val tracks = currentTracks()
        val totalH = tracks.size * trackH
        val maxScrollDown = (totalH - listH + 12f).coerceAtLeast(0f)
        listScroll = listScroll.coerceIn(-maxScrollDown, 0f)
        animListScroll = animate(animListScroll, listScroll, 0.18f)

        // 裁剪
        startScissor(listLeft, listTop + 4f, listW, listH - 8f)

        var iy = listTop + 6f + animListScroll
        tracks.forEachIndexed { i, track ->
            if (iy + trackH < listTop) {
                iy += trackH
                return@forEachIndexed
            }
            if (iy > listBottom) return@forEachIndexed

            val isCurrent = track == MusicPlayer.playingTrack
            val isSelected = i == selectedIndex
            val hovered = isHovered(listLeft + 4f, iy, listW - 8f, trackH - 2f, mouseX, mouseY)

            // 行背景
            val rowBg = when {
                isCurrent -> activeColor
                isSelected -> selectedColor
                hovered -> hoverColor
                else -> null
            }
            rowBg?.let { RoundedUtil.drawRound(listLeft + 4f, iy, listW - 8f, trackH - 2f, 4f, it) }

            // 左侧指示器
            val indicatorX = listLeft + 12f
            val indicatorY = iy + (trackH - Fonts.fontSemibold35.FONT_HEIGHT) / 2f + 1f
            if (isCurrent) {
                Fonts.fontSemibold35.drawString("▶", indicatorX, indicatorY, accent.rgb)
            } else if (isSelected) {
                RenderUtils.drawRect(listLeft + 6f, iy + 4f, listLeft + 8f, iy + trackH - 6f, accent)
            }

            // 歌名 + 来源/ID
            val titleX = listLeft + 32f
            val titleY = iy + 6f
            val titleMaxW = (listW - 60f).toInt()
            val trimmedTitle = trimToWidth(track.displayName, titleMaxW)
            Fonts.fontSemibold35.drawString(trimmedTitle, titleX, titleY, textColor.rgb)

            // 来源/ID (第二行小字)
            val sourceStr = buildString {
                when (track.source) {
                    TrackSource.LOCAL -> append("本地")
                    TrackSource.NETEASE -> append("网易云")
                }
                track.neteaseId?.let { append(" • ID: $it") }
            }
            Fonts.fontSemibold35.drawString(sourceStr, titleX, titleY + 14f, mutedColor.rgb)

            iy += trackH
        }

        endScissor()

        // 滚动条
        if (totalH > listH - 8f) {
            val trackAreaH = listH - 8f
            val barH = (trackAreaH * (trackAreaH / totalH)).coerceAtLeast(20f)
            val progress = (-animListScroll / (totalH - trackAreaH + 4f)).coerceIn(0f, 1f)
            RoundedUtil.drawRound(listRight - 4f, listTop + 4f, 2f, trackAreaH, 1f, Color(255, 255, 255, 20))
            RoundedUtil.drawRound(listRight - 4f, listTop + 4f + (trackAreaH - barH) * progress, 2f, barH, 1f, accent)
        }

        // 空列表提示
        if (tracks.isEmpty()) {
            val emptyText = when (currentTab) {
                Tab.LOCAL -> "本地音乐为空, 点击右侧 '↻ 刷新' 加载"
                Tab.NETEASE -> "在上方搜索框输入关键词或歌曲 ID 后点击搜索"
                Tab.QUEUE -> "播放队列为空, 选中歌曲后点击 '+ 队列' 加入"
            }
            val emptyW = Fonts.fontSemibold35.getStringWidth(emptyText)
            Fonts.fontSemibold35.drawString(emptyText, (width - sidebarW) / 2f - emptyW / 2f, listTop + listH / 2f, mutedColor.rgb)
        }
    }

    // ===== 右侧控制栏 =====
    private fun drawControlSidebar(mouseX: Int, mouseY: Int) {
        val sidebarX = width - sidebarW + 8f
        val sidebarTop = headerH + tabH + 4f
        val sidebarBottom = height - bottomH - 4f

        // 分隔线
        RenderUtils.drawRect(width - sidebarW + 6f, sidebarTop, width - sidebarW + 7f, sidebarBottom, separatorColor.rgb)

        // 标题
        Fonts.fontSemibold35.drawString("操作", sidebarX, sidebarTop + 4f, mutedColor.rgb)

        val btnH = 28f
        val btnW = sidebarW - 16f
        var by = sidebarTop + 22f
        for ((id, label) in sidebarBtns) {
            val hovered = isHovered(sidebarX, by, btnW, btnH, mouseX, mouseY)
            val bg = if (hovered) hoverColor else Color(255, 255, 255, 8)
            RoundedUtil.drawRound(sidebarX, by, btnW, btnH, 4f, bg)
            val tc = if (hovered) accent.rgb else textColor.rgb
            val labelW = Fonts.fontSemibold35.getStringWidth(label)
            Fonts.fontSemibold35.drawString(label, sidebarX + (btnW - labelW) / 2f, by + (btnH - Fonts.fontSemibold35.FONT_HEIGHT) / 2f + 1f, tc)
            by += btnH + 4f
        }

        // 音量控制 (底部)
        val volY = sidebarBottom - 70f
        Fonts.fontSemibold35.drawString("音量", sidebarX, volY, mutedColor.rgb)
        val volText = "${MusicPlayer.getVolume()}%"
        val volW = Fonts.fontSemibold35.getStringWidth(volText)
        Fonts.fontSemibold35.drawString(volText, sidebarX + btnW - volW, volY, accent.rgb)

        // 音量条
        val volBarY = volY + 18f
        val volBarH = 4f
        RoundedUtil.drawRound(sidebarX, volBarY, btnW, volBarH, 2f, Color(40, 48, 64))
        val volProgress = MusicPlayer.getVolume() / 100f
        RoundedUtil.drawRound(sidebarX, volBarY, btnW * volProgress, volBarH, 2f, accent)

        // +/- 按钮
        val btnSize = 24f
        val minusX = sidebarX
        val plusX = sidebarX + btnW - btnSize
        val btnY = volBarY + 10f
        val minusHovered = isHovered(minusX, btnY, btnSize, btnSize, mouseX, mouseY)
        val plusHovered = isHovered(plusX, btnY, btnSize, btnSize, mouseX, mouseY)
        if (minusHovered) RoundedUtil.drawRound(minusX, btnY, btnSize, btnSize, 4f, hoverColor)
        if (plusHovered) RoundedUtil.drawRound(plusX, btnY, btnSize, btnSize, 4f, hoverColor)
        Fonts.fontSemibold35.drawString("-", minusX + 8f, btnY + 6f, if (minusHovered) accent.rgb else textColor.rgb)
        Fonts.fontSemibold35.drawString("+", plusX + 8f, btnY + 6f, if (plusHovered) accent.rgb else textColor.rgb)
    }

    // ===== 底部 Now Playing =====
    private fun drawNowPlaying(mouseX: Int, mouseY: Int) {
        val bottomY = height - bottomH
        RenderUtils.drawRect(0f, bottomY, width.toFloat(), height.toFloat(), bottomBgColor.rgb)
        RenderUtils.drawRect(0f, bottomY, width.toFloat(), bottomY + 1f, separatorColor.rgb)

        val track = MusicPlayer.playingTrack
        val title = track?.displayName ?: "未播放"
        val source = when (track?.source) {
            TrackSource.LOCAL -> "本地"
            TrackSource.NETEASE -> "网易云"
            null -> "-"
        }

        // 左侧: 歌名 + 信息
        val infoX = 16f
        val infoY = bottomY + 10f
        val titleMaxW = (width - 200).toInt()
        val trimmedTitle = trimToWidth(title, titleMaxW)
        Fonts.fontSemibold40.drawString(trimmedTitle, infoX, infoY, textColor.rgb)

        val infoStr = "来源: $source    ${MusicPlayer.timeDisplayString}    音量: ${MusicPlayer.getVolume()}%"
        Fonts.fontSemibold35.drawString(infoStr, infoX, infoY + 22f, mutedColor.rgb)

        // 歌词
        val lyric = MusicPlayer.currentLyricDisplay
        if (lyric.isNotBlank()) {
            val lyricTrimmed = trimToWidth(lyric, titleMaxW)
            Fonts.fontSemibold35.drawString(lyricTrimmed, infoX, infoY + 44f, Color(120, 200, 255).rgb)
        }

        // 进度条 (底部)
        val barX = 16f
        val barY = bottomY + bottomH - 16f
        val barW = width - 32f
        val barH = 4f
        RoundedUtil.drawRound(barX, barY, barW, barH, 2f, Color(40, 48, 64))
        val progress = MusicPlayer.progress
        if (progress > 0f) {
            RoundedUtil.drawRound(barX, barY, barW * progress, barH, 2f, accent)
        }
        // 进度条 hover 高亮
        if (isHovered(barX, barY - 4f, barW, barH + 8f, mouseX, mouseY)) {
            val hoverX = barX + barW * ((mouseX - barX) / barW).coerceIn(0f, 1f)
            RoundedUtil.drawRound(hoverX - 2f, barY - 2f, 4f, barH + 4f, 2f, Color(255, 255, 255, 200))
        }

        // 时间 + 状态 (进度条上方)
        Fonts.fontSemibold35.drawString(MusicPlayer.timeDisplayString, barX, barY - 12f, mutedColor.rgb)
        val state = if (MusicPlayer.isCurrentlyPlaying) "▶ 播放中" else "■ 已停止"
        val stateW = Fonts.fontSemibold35.getStringWidth(state)
        Fonts.fontSemibold35.drawString(state, barX + barW - stateW, barY - 12f, if (MusicPlayer.isCurrentlyPlaying) accent.rgb else mutedColor.rgb)
    }

    // ===== 交互 =====

    override fun mouseClicked(mouseX: Int, mouseY: Int, mouseButton: Int) {
        if (mouseButton != 0) {
            super.mouseClicked(mouseX, mouseY, mouseButton)
            return
        }

        // Header 区域
        if (mouseY < headerH) {
            // 返回按钮
            val backBtnSize = 18f
            val backBtnX = width - 36f
            val backBtnY = (headerH - backBtnSize) / 2f
            if (isHovered(backBtnX, backBtnY, backBtnSize, backBtnSize, mouseX, mouseY)) {
                mc.displayGuiScreen(prevGui)
                return
            }
            // 搜索框 (让 AbstractScreen 处理 textFields 点击)
            super.mouseClicked(mouseX, mouseY, mouseButton)
            return
        }

        // Tab 栏
        if (mouseY >= headerH && mouseY <= headerH + tabH) {
            val tabW = 110f
            val tabGap = 4f
            var tx = 16f
            for (tab in Tab.values()) {
                if (isHovered(tx, headerH + 4f, tabW, tabH - 8f, mouseX, mouseY)) {
                    switchTab(tab)
                    return
                }
                tx += tabW + tabGap
            }
            // 搜索按钮
            val searchTabX = (width - 280f).coerceAtLeast(360f) - 50f
            if (isHovered(searchTabX, headerH + 4f, 40f, tabH - 8f, mouseX, mouseY)) {
                searchNetease()
                return
            }
            return
        }

        // 右侧控制栏
        if (mouseX > width - sidebarW + 6f && mouseY < height - bottomH) {
            val sidebarTop = headerH + tabH + 4f
            val sidebarBottom = height - bottomH - 4f
            val sidebarX = width - sidebarW + 8f
            val btnH = 28f
            val btnW = sidebarW - 16f
            var by = sidebarTop + 22f
            for ((id, _) in sidebarBtns) {
                if (isHovered(sidebarX, by, btnW, btnH, mouseX, mouseY)) {
                    handleSidebarAction(id)
                    return
                }
                by += btnH + 4f
            }
            // 音量 -/+
            val volY = sidebarBottom - 70f
            val volBarY = volY + 18f
            val btnSize = 24f
            val btnY = volBarY + 10f
            val minusX = sidebarX
            val plusX = sidebarX + btnW - btnSize
            if (isHovered(minusX, btnY, btnSize, btnSize, mouseX, mouseY)) {
                MusicPlayer.setVolume(MusicPlayer.getVolume() - 5)
                return
            }
            if (isHovered(plusX, btnY, btnSize, btnSize, mouseX, mouseY)) {
                MusicPlayer.setVolume(MusicPlayer.getVolume() + 5)
                return
            }
            return
        }

        // 列表项点击
        val listTop = headerH + tabH + 4f
        val listBottom = height - bottomH - 4f
        if (mouseY >= listTop && mouseY <= listBottom && mouseX < width - sidebarW + 6f) {
            val tracks = currentTracks()
            val listLeft = 16f
            val relativeY = mouseY - listTop - 6f - animListScroll
            val index = (relativeY / trackH).toInt()
            if (index in tracks.indices) {
                selectedIndex = index
                // 双击播放
                val now = System.currentTimeMillis()
                if (lastClickedIndex == index && now - lastClickTime < 400) {
                    playSelected()
                    lastClickTime = 0L
                    lastClickedIndex = -1
                } else {
                    lastClickTime = now
                    lastClickedIndex = index
                }
                return
            }
        }

        super.mouseClicked(mouseX, mouseY, mouseButton)
    }

    override fun keyTyped(typedChar: Char, keyCode: Int) {
        if (searchField.isFocused) {
            if (keyCode == Keyboard.KEY_RETURN) {
                searchNetease()
                return
            }
            if (keyCode == Keyboard.KEY_ESCAPE) {
                searchField.setFocused(false)
                return
            }
            searchField.textboxKeyTyped(typedChar, keyCode)
            return
        }
        if (keyCode == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(prevGui)
            return
        }
        super.keyTyped(typedChar, keyCode)
    }

    override fun handleMouseInput() {
        super.handleMouseInput()
        val dw = Mouse.getEventDWheel()
        if (dw == 0) return
        val listTop = headerH + tabH + 4f
        val listBottom = height - bottomH - 4f
        val mx = Mouse.getEventX() * width / mc.displayWidth
        val my = height - Mouse.getEventY() * height / mc.displayHeight - 1
        if (mx in 0..(width - sidebarW.toInt()) && my in listTop.toInt()..listBottom.toInt()) {
            val delta = if (dw > 0) 30f else -30f
            listScroll += delta
        }
    }

    override fun updateScreen() {
        searchField.updateCursorCounter()
    }

    // ===== 业务逻辑 =====

    private fun switchTab(tab: Tab) {
        currentTab = tab
        selectedIndex = -1
        listScroll = 0f
        animListScroll = 0f
        statusText = when (tab) {
            Tab.LOCAL -> "本地音乐 ${MusicPlayer.localTrackList.size} 首"
            Tab.NETEASE -> "网易云搜索 (${searchResults.size} 条结果)"
            Tab.QUEUE -> "播放队列 ${MusicPlayer.queueTracks.size} 首"
        }
    }

    private fun currentTracks(): List<Track> = when (currentTab) {
        Tab.LOCAL -> MusicPlayer.localTrackList
        Tab.NETEASE -> searchResults
        Tab.QUEUE -> MusicPlayer.queueTracks
    }

    private fun refreshListSelection() {
        if (selectedIndex >= currentTracks().size) {
            selectedIndex = -1
        }
    }

    private fun playSelected() {
        val tracks = currentTracks()
        if (selectedIndex !in tracks.indices) {
            statusText = "请先选择一首歌曲"
            return
        }
        val track = tracks[selectedIndex]
        if (currentTab == Tab.LOCAL) {
            val localIndex = MusicPlayer.localTrackList.indexOf(track)
            if (localIndex >= 0) {
                MusicPlayer.playLocalIndex(localIndex)
            }
        } else {
            MusicPlayer.playTrack(track)
        }
        statusText = "正在播放: ${track.displayName}"
    }

    private fun addSelectedToQueue() {
        val tracks = currentTracks()
        if (selectedIndex !in tracks.indices) {
            statusText = "请先选择一首歌曲"
            return
        }
        val track = tracks[selectedIndex]
        val pos = MusicPlayer.enqueue(track)
        statusText = "已加入队列 (#$pos): ${track.displayName}"
    }

    private fun searchNetease() {
        val keyword = searchField.text.trim()
        if (keyword.isEmpty()) {
            statusText = "请输入搜索关键词或歌曲 ID"
            return
        }

        val id = keyword.toLongOrNull()
        if (id != null && id > 0L) {
            statusText = "正在加载 ID: $id ..."
            SharedScopes.IO.launch {
                val track = MusicPlayer.fetchNeteaseTrack(id)
                mc.addScheduledTask {
                    if (track == null) {
                        statusText = "无法获取歌曲 (ID: $id)"
                        return@addScheduledTask
                    }
                    searchResults = listOf(track)
                    switchTab(Tab.NETEASE)
                    selectedIndex = 0
                    MusicPlayer.playTrack(track)
                    statusText = "正在播放: ${track.displayName}"
                }
            }
            return
        }

        statusText = "正在搜索: $keyword ..."
        SharedScopes.IO.launch {
            val results = MusicPlayer.searchNetease(keyword)
            mc.addScheduledTask {
                searchResults = results
                switchTab(Tab.NETEASE)
                selectedIndex = if (results.isNotEmpty()) 0 else -1
                statusText = if (results.isEmpty()) {
                    "未找到结果"
                } else {
                    "找到 ${results.size} 首: $keyword"
                }
            }
        }
    }

    private fun openMusicFolder() {
        try {
            Desktop.getDesktop().open(LocalMusicSource.musicDir)
            statusText = "已打开: ${LocalMusicSource.musicDir.absolutePath}"
        } catch (e: Exception) {
            statusText = "无法打开音乐目录"
        }
    }

    private fun handleSidebarAction(id: String) {
        when (id) {
            "play" -> playSelected()
            "queue" -> addSelectedToQueue()
            "prev" -> MusicPlayer.playPrevious()
            "next" -> MusicPlayer.playNext()
            "stop" -> MusicPlayer.stopMusic()
            "refresh" -> {
                MusicPlayer.refreshLocalTracks()
                statusText = "已刷新本地列表 (${MusicPlayer.localTrackList.size} 首)"
            }
            "folder" -> openMusicFolder()
        }
    }

    // ===== 辅助方法 =====

    private fun animate(current: Float, target: Float, speed: Float): Float {
        if (abs(target - current) < 0.01f) return target
        return current + (target - current) * speed
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
        if (text.isEmpty() || Fonts.fontSemibold35.getStringWidth(text) <= maxWidth) return text
        var trimmed = text
        while (trimmed.isNotEmpty() && Fonts.fontSemibold35.getStringWidth("...$trimmed") > maxWidth) {
            trimmed = trimmed.dropLast(1)
        }
        return if (trimmed.isEmpty()) "..." else "...$trimmed"
    }

    private fun isHovered(hx: Float, hy: Float, hw: Float, hh: Float, mouseX: Int, mouseY: Int) =
        mouseX >= hx && mouseX <= hx + hw && mouseY >= hy && mouseY <= hy + hh
}
