package net.ccbluex.liquidbounce.features.module.modules.render

import javazoom.jl.player.JavaSoundAudioDevice
import javazoom.jl.player.Player
import net.ccbluex.liquidbounce.config.ListValue
import net.ccbluex.liquidbounce.event.EventState
import net.ccbluex.liquidbounce.event.PacketEvent
import net.ccbluex.liquidbounce.event.Render2DEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.file.FileManager
import net.ccbluex.liquidbounce.ui.font.Fonts
import net.ccbluex.liquidbounce.utils.client.ClientThemesUtils
import net.ccbluex.liquidbounce.utils.render.BlurUtils
import net.ccbluex.liquidbounce.utils.render.RenderUtils
import net.minecraft.client.gui.ScaledResolution
import net.minecraft.entity.item.EntityFireworkRocket
import net.minecraft.network.play.server.S02PacketChat
import net.minecraft.network.play.server.S45PacketTitle
import net.minecraft.util.ResourceLocation
import org.lwjgl.opengl.GL11
import java.awt.Color
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

object MVPDisplay : Module("MVPDisplay", Category.RENDER, gameDetecting = false) {

    private val victoryKeywords = listOf(
        "win", "victory", "胜", "赢", "winner", "champion", "冠军",
        "first place", "第一名", "you won", "胜利", "best",
         "获胜", "triumph", "恭喜", "well played"
    )

    private val checkFirework by boolean("检测烟花", true)
    private val fireworkRadius by int("烟花检测半径", 10, 5..50) { checkFirework }

    private val checkTitle by boolean("检测Title", true)
    private val checkSubtitle by boolean("检测Subtitle", true)
    private val checkChat by boolean("检测聊天栏", true)

    private val checkTabCount by boolean("检测Tab人数", false)
    private val targetTabCount by int("目标Tab人数", 1, 1..100) { checkTabCount }

    private val checkKillStreak by boolean("检测连杀", false)
    private val killStreakCount by int("连杀数量", 5, 2..50) { checkKillStreak }

    private val displayStyle by choices("界面样式", arrayOf("简约", "优雅", "高级", "科技", "高雅"), "高级")

    private val displayX by int("显示位置X", 0, -500..500)
    private val displayY by int("显示位置Y", 130, -500..500)
    private val displayWidth by int("显示宽度", 200, 100..400)
    private val displayHeight by int("显示高度", 80, 50..200)

    private val avatarSize by int("头像大小", 48, 24..96)

    private val backgroundAlpha by int("背景透明度", 230, 0..255)
    private val blurBackground by boolean("背景模糊", true)
    private val blurStrength by float("模糊强度", 10F, 1F..30F) { blurBackground }

    private val bounceAnimation by boolean("弹跳动画", true)
    private val bounceTension by float("弹跳张力", 0.01f, 0.01f..0.5f) { bounceAnimation }
    private val bounceFriction by float("弹跳摩擦", 0.1f, 0.01f..0.5f) { bounceAnimation }

    private val firstLineText by text("第一行文字", "MVP")
    private val secondLineText by text("第二行文字", "为本场MVP!")
    private val thirdLineText by text("第三行文字", "♪正在高奏您的MVP凯歌:  ")

    private val displayDuration by int("显示时间(秒)", 5, 1..30)
    private val fadeAnimation by boolean("淡入淡出动画", true)
    private val scaleAnimation by boolean("缩放动画", true)
    private val animationDuration by int("动画时间(毫秒)", 300, 100..1000) { fadeAnimation || scaleAnimation }

    private val cooldownTime by int("冷却时间(秒)", 30, 0..120)

    private val mvpVolume by int("音乐音量", 100, 0..100)

    private val mvpMusicDir: File by lazy {
        val dir = File(FileManager.dir, "MVPMusic")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        dir
    }

    private val mvpMusicList = mutableListOf<File>()
    private val mvpMusicCache = ConcurrentHashMap<String, Long>()
    private var selectedMvpMusicName = "无"
    private var mvpPlayer: Player? = null
    private var mvpAudioDevice: VolumeControlledAudioDevice? = null
    private var mvpPlayThread: Thread? = null
    private var isMvpPlaying = false

    private lateinit var mvpMusicChoicesValue: ListValue

    private fun initMvpMusicChoices() {
        mvpMusicChoicesValue = choices("MVP音乐", arrayOf("无"), "无").onChanged {
            selectedMvpMusicName = it
        } as ListValue
    }

    init {
        initMvpMusicChoices()
    }

    private var isDisplaying = false
    private var displayStartTime = 0L
    private var lastTriggerTime = 0L
    private var currentAnimationProgress = 0F
    private var animationState = AnimationState.HIDDEN
    private var animationTick = 0L

    private var animX = 0F
    private var animY = 0F
    private var animWidth = 0F
    private var animHeight = 0F
    private var animScale = 0F
    private var animAlpha = 0F

    private var velX = 0F
    private var velY = 0F
    private var velWidth = 0F
    private var velHeight = 0F
    private var velScale = 0F
    private var velAlpha = 0F

    private var killCount = 0
    private var lastKillTime = 0L
    private val killStreakTimeout = 5000L

    private var lastTitleText = ""
    private var lastSubtitleText = ""
    private var lastChatText = ""

    private enum class AnimationState {
        HIDDEN, FADE_IN, SHOWING, FADE_OUT
    }

    override fun onEnable() {
        super.onEnable()
        scanMvpMusicFiles()
        resetState()
    }

    override fun onDisable() {
        super.onDisable()
        stopMvpMusic()
        resetState()
    }

    private fun resetState() {
        isDisplaying = false
        displayStartTime = 0L
        currentAnimationProgress = 0F
        animationState = AnimationState.HIDDEN
        animationTick = 0L
        killCount = 0
        lastKillTime = 0L
        lastTitleText = ""
        lastSubtitleText = ""
        lastChatText = ""
        
        animX = 0F
        animY = 0F
        animWidth = 0F
        animHeight = 0F
        animScale = 0F
        animAlpha = 0F
        velX = 0F
        velY = 0F
        velWidth = 0F
        velHeight = 0F
        velScale = 0F
        velAlpha = 0F
    }

    private fun spring(current: Float, target: Float, velocity: Float): Pair<Float, Float> {
        val displacement = target - current
        val force = displacement * bounceTension
        val drag = velocity * bounceFriction
        val acceleration = force - drag
        val newVelocity = velocity + acceleration
        val newPosition = current + newVelocity
        return newPosition to newVelocity
    }

    private fun scanMvpMusicFiles() {
        mvpMusicList.clear()
        mvpMusicCache.clear()

        if (!mvpMusicDir.exists() || !mvpMusicDir.isDirectory) {
            updateMvpMusicChoices()
            return
        }

        mvpMusicDir.walk()
            .filter { file ->
                file.isFile && (
                    file.extension.equals("mp3", true) ||
                    file.extension.equals("wav", true) ||
                    file.extension.equals("flac", true)
                )
            }
            .sortedBy { it.nameWithoutExtension.lowercase() }
            .forEach { file ->
                mvpMusicList.add(file)
                mvpMusicCache[file.name] = file.lastModified()
            }

        updateMvpMusicChoices()
    }

    private fun updateMvpMusicChoices() {
        val names = mutableListOf("无")
        names.addAll(mvpMusicList.map { it.nameWithoutExtension })
        mvpMusicChoicesValue.updateValues(names.toTypedArray())
        if (selectedMvpMusicName !in names) {
            selectedMvpMusicName = "无"
        }
    }

    private fun updateAnimation() {
        if (!isDisplaying) {
            animationState = AnimationState.HIDDEN
            currentAnimationProgress = 0F
            return
        }

        animationTick++

        val elapsed = System.currentTimeMillis() - displayStartTime
        val animDuration = if (fadeAnimation || scaleAnimation) animationDuration.toLong() else 0L
        val showDuration = displayDuration * 1000L

        when {
            elapsed < animDuration -> {
                animationState = AnimationState.FADE_IN
                currentAnimationProgress = elapsed.toFloat() / animDuration
            }
            elapsed < animDuration + showDuration -> {
                animationState = AnimationState.SHOWING
                currentAnimationProgress = 1F
            }
            elapsed < animDuration + showDuration + animDuration -> {
                animationState = AnimationState.FADE_OUT
                currentAnimationProgress = 1F - (elapsed - animDuration - showDuration).toFloat() / animDuration
            }
            else -> {
                isDisplaying = false
                animationState = AnimationState.HIDDEN
                currentAnimationProgress = 0F
            }
        }
    }

    private fun checkVictoryConditions() {
        if (isDisplaying) return

        val currentTime = System.currentTimeMillis()
        if (currentTime - lastTriggerTime < cooldownTime * 1000L) return

        var victoryDetected = false

        if (checkFirework && checkFireworkVictory()) {
            victoryDetected = true
        }

        if (!victoryDetected && checkTabCount && checkTabCountVictory()) {
            victoryDetected = true
        }

        if (!victoryDetected && checkKillStreak && checkKillStreakVictory()) {
            victoryDetected = true
        }

        if (victoryDetected) {
            triggerMVPDisplay()
        }
    }

    private fun checkFireworkVictory(): Boolean {
        val player = mc.thePlayer ?: return false
        val world = mc.theWorld ?: return false

        val fireworks = world.loadedEntityList.filterIsInstance<EntityFireworkRocket>()
        for (firework in fireworks) {
            val distance = player.getDistanceToEntity(firework)
            if (distance <= fireworkRadius) {
                return true
            }
        }
        return false
    }

    private fun checkTabCountVictory(): Boolean {
        val playerInfoMap = mc.netHandler?.playerInfoMap ?: return false
        return playerInfoMap.size <= targetTabCount
    }

    private fun checkKillStreakVictory(): Boolean {
        return killCount >= killStreakCount
    }

    private fun updateKillStreak() {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastKillTime > killStreakTimeout && killCount > 0) {
            killCount = 0
        }
    }

    fun onKill() {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastKillTime <= killStreakTimeout) {
            killCount++
        } else {
            killCount = 1
        }
        lastKillTime = currentTime
    }

    val onPacket = handler<PacketEvent> { event ->
        if (event.eventType != EventState.RECEIVE) return@handler

        if (isDisplaying) return@handler

        val currentTime = System.currentTimeMillis()
        if (currentTime - lastTriggerTime < cooldownTime * 1000L) return@handler

        var victoryDetected = false

        when (val packet = event.packet) {
            is S45PacketTitle -> {
                val text = packet.message?.unformattedText?.lowercase() ?: ""
                
                if (packet.type == S45PacketTitle.Type.TITLE && checkTitle) {
                    if (text != lastTitleText && checkVictoryKeywords(text)) {
                        victoryDetected = true
                        lastTitleText = text
                    }
                }
                
                if (packet.type == S45PacketTitle.Type.SUBTITLE && checkSubtitle) {
                    if (text != lastSubtitleText && checkVictoryKeywords(text)) {
                        victoryDetected = true
                        lastSubtitleText = text
                    }
                }
            }
            is S02PacketChat -> {
                if (checkChat) {
                    val text = packet.chatComponent.unformattedText.lowercase()
                    if (text != lastChatText && checkVictoryKeywords(text)) {
                        victoryDetected = true
                        lastChatText = text
                    }
                }
            }
        }

        if (victoryDetected) {
            triggerMVPDisplay()
        }
    }

    private fun checkVictoryKeywords(text: String): Boolean {
        val lowerText = text.lowercase()
        return victoryKeywords.any { keyword -> lowerText.contains(keyword.lowercase()) }
    }

    private fun triggerMVPDisplay() {
        if (isDisplaying) return

        val currentTime = System.currentTimeMillis()
        if (currentTime - lastTriggerTime < cooldownTime * 1000L) return

        isDisplaying = true
        displayStartTime = currentTime
        lastTriggerTime = currentTime
        animationState = AnimationState.FADE_IN
        currentAnimationProgress = 0F
        animationTick = 0L

        killCount = 0

        playMvpMusic()
    }

    private fun playMvpMusic() {
        if (selectedMvpMusicName == "无") return

        val musicFile = mvpMusicList.find { it.nameWithoutExtension == selectedMvpMusicName } ?: return

        stopMvpMusic()

        mvpPlayThread = thread(start = true, name = "MVPMusic-Thread") {
            try {
                isMvpPlaying = true
                val inputStream = BufferedInputStream(FileInputStream(musicFile))
                mvpAudioDevice = VolumeControlledAudioDevice()
                mvpAudioDevice?.setVolume(mvpVolume / 100F)
                mvpPlayer = Player(inputStream, mvpAudioDevice)
                mvpPlayer?.play()
            } catch (e: Exception) {
                isMvpPlaying = false
            }
        }
    }

    private fun stopMvpMusic() {
        isMvpPlaying = false
        try {
            mvpPlayer?.close()
        } catch (e: Exception) {
        }
        mvpPlayer = null
        mvpAudioDevice = null
        try {
            mvpPlayThread?.interrupt()
        } catch (e: Exception) {
        }
        mvpPlayThread = null
    }

    val onRender2D = handler<Render2DEvent> {
        updateAnimation()
        checkVictoryConditions()
        updateKillStreak()

        if (!isDisplaying || animationState == AnimationState.HIDDEN) return@handler

        val scaledResolution = ScaledResolution(mc)
        val screenWidth = scaledResolution.scaledWidth.toFloat()
        val screenHeight = scaledResolution.scaledHeight.toFloat()

        val centerX = screenWidth / 2 + displayX
        val centerY = screenHeight / 2 + displayY

        val targetX = centerX - displayWidth / 2
        val targetY = centerY - displayHeight / 2
        val targetWidth = displayWidth.toFloat()
        val targetHeight = displayHeight.toFloat()
        val targetScale = if (scaleAnimation) 0.5F + currentAnimationProgress * 0.5F else 1F
        val targetAlpha = if (fadeAnimation) currentAnimationProgress else 1F

        if (bounceAnimation) {
            val (newX, vX) = spring(animX, targetX, velX)
            animX = newX
            velX = vX

            val (newY, vY) = spring(animY, targetY, velY)
            animY = newY
            velY = vY

            val (newW, vW) = spring(animWidth, targetWidth, velWidth)
            animWidth = newW.coerceAtLeast(0F)
            velWidth = vW

            val (newH, vH) = spring(animHeight, targetHeight, velHeight)
            animHeight = newH.coerceAtLeast(0F)
            velHeight = vH

            val (newS, vS) = spring(animScale, targetScale, velScale)
            animScale = newS.coerceIn(0F, 2F)
            velScale = vS

            val (newA, vA) = spring(animAlpha, targetAlpha, velAlpha)
            animAlpha = newA.coerceIn(0F, 1F)
            velAlpha = vA
        } else {
            animX = targetX
            animY = targetY
            animWidth = targetWidth
            animHeight = targetHeight
            animScale = targetScale
            animAlpha = targetAlpha
        }

        val x = animX
        val y = animY
        val w = animWidth
        val h = animHeight
        val scale = animScale
        val alpha = (animAlpha * 255).toInt()

        GL11.glPushMatrix()
        GL11.glTranslatef(centerX, centerY, 0F)
        GL11.glScalef(scale, scale, 1F)
        GL11.glTranslatef(-centerX, -centerY, 0F)

        val themeColor = ClientThemesUtils.getColor()

        if (blurBackground) {
            BlurUtils.blurAreaRounded(x, y, x + w, y + h, 8F, blurStrength)
        }

        when (displayStyle) {
            "简约" -> renderMinimalStyle(x, y, w, h, themeColor, alpha)
            "优雅" -> renderElegantStyle(x, y, w, h, themeColor, alpha)
            "高级" -> renderPremiumStyle(x, y, w, h, themeColor, alpha)
            "科技" -> renderTechStyle(x, y, w, h, themeColor, alpha)
            "高雅" -> renderRefinedStyle(x, y, w, h, themeColor, alpha)
            else -> renderPremiumStyle(x, y, w, h, themeColor, alpha)
        }

        GL11.glPopMatrix()
    }

    // 简约 - 横向单行条
    private fun renderMinimalStyle(x: Float, y: Float, w: Float, h: Float, themeColor: Color, alpha: Int) {
        val bgAlpha = (alpha * backgroundAlpha / 255F).toInt()
        val bgColor = Color(247, 247, 248, bgAlpha)
        val accent = Color(themeColor.red, themeColor.green, themeColor.blue, alpha)
        val darkText = Color(42, 42, 46, alpha)
        val dimText = Color(110, 110, 116, alpha)

        RenderUtils.drawRoundedRect(x, y, x + w, y + h, bgColor.rgb, 4F)

        val avSize = 32
        val avX = x + 12F
        val avY = y + (h - avSize) / 2F
        val skinLocation: ResourceLocation? = mc.thePlayer?.locationSkin
        if (skinLocation != null) {
            RenderUtils.drawHead(skinLocation, avX.toInt(), avY.toInt(), avSize, avSize, Color.WHITE)
        }

        val playerName = mc.thePlayer?.name ?: "Player"
        val font = Fonts.fontSemibold35
        val textY = y + h / 2F - font.fontHeight / 2F + 2F
        var textX = avX + avSize + 12F

        font.drawString(firstLineText, textX, textY, accent.rgb)
        textX += font.getStringWidth(firstLineText) + 8F
        font.drawString("·", textX, textY, dimText.rgb)
        textX += font.getStringWidth("·") + 8F

        val secondLine = "$playerName$secondLineText"
        font.drawString(secondLine, textX, textY, darkText.rgb)

        if (selectedMvpMusicName != "无") {
            textX += font.getStringWidth(secondLine) + 10F
            val musicFont = Fonts.fontRegular30
            musicFont.drawString("♪ $selectedMvpMusicName", textX, textY + 2F, dimText.rgb)
        }

        RenderUtils.drawRect(x + 8, y + h - 2, x + w - 8, y + h - 1, accent.rgb)
    }

    // 优雅 - 居中竖向卡片 + 四角金饰
    private fun renderElegantStyle(x: Float, y: Float, w: Float, h: Float, themeColor: Color, alpha: Int) {
        val bgAlpha = (alpha * backgroundAlpha / 255F).toInt()
        val bgColor = Color(239, 232, 216, bgAlpha)
        val gold = Color(201, 169, 97, alpha)
        val goldDim = Color(201, 169, 97, (alpha * 0.5F).toInt())
        val darkText = Color(92, 74, 46, alpha)
        val dimText = Color(130, 110, 80, alpha)

        RenderUtils.drawRoundedRect(x, y, x + w, y + h, bgColor.rgb, 12F)

        // 四角L形金饰
        val cornerLen = 8F
        val cornerThick = 2F
        val inset = 4F
        // 左上
        RenderUtils.drawRect(x + inset, y + inset, x + inset + cornerLen, y + inset + cornerThick, gold.rgb)
        RenderUtils.drawRect(x + inset, y + inset, x + inset + cornerThick, y + inset + cornerLen, gold.rgb)
        // 右上
        RenderUtils.drawRect(x + w - inset - cornerLen, y + inset, x + w - inset, y + inset + cornerThick, gold.rgb)
        RenderUtils.drawRect(x + w - inset - cornerThick, y + inset, x + w - inset, y + inset + cornerLen, gold.rgb)
        // 左下
        RenderUtils.drawRect(x + inset, y + h - inset - cornerThick, x + inset + cornerLen, y + h - inset, gold.rgb)
        RenderUtils.drawRect(x + inset, y + h - inset - cornerLen, x + inset + cornerThick, y + h - inset, gold.rgb)
        // 右下
        RenderUtils.drawRect(x + w - inset - cornerLen, y + h - inset - cornerThick, x + w - inset, y + h - inset, gold.rgb)
        RenderUtils.drawRect(x + w - inset - cornerThick, y + h - inset - cornerLen, x + w - inset, y + h - inset, gold.rgb)

        val avSize = 28
        val avX = x + w / 2F - avSize / 2F
        val avY = y + 10F
        val skinLocation: ResourceLocation? = mc.thePlayer?.locationSkin
        if (skinLocation != null) {
            RenderUtils.drawHead(skinLocation, avX.toInt(), avY.toInt(), avSize, avSize, Color.WHITE)
        }

        val playerName = mc.thePlayer?.name ?: "Player"
        val titleFont = Fonts.fontSemibold35
        val bodyFont = Fonts.fontRegular30
        val titleW = titleFont.getStringWidth(firstLineText)
        titleFont.drawString(firstLineText, x + w / 2F - titleW / 2F, avY + avSize + 3F, gold.rgb)

        val secondLine = "$playerName$secondLineText"
        val bodyW = bodyFont.getStringWidth(secondLine)
        bodyFont.drawString(secondLine, x + w / 2F - bodyW / 2F, avY + avSize + 20F, darkText.rgb)

        if (selectedMvpMusicName != "无") {
            val musicStr = "♪ $selectedMvpMusicName"
            val mw = bodyFont.getStringWidth(musicStr)
            bodyFont.drawString(musicStr, x + w / 2F - mw / 2F, y + h - 12F, dimText.rgb)
        }
    }

    // 高级 - 左侧竖向金带
    private fun renderPremiumStyle(x: Float, y: Float, w: Float, h: Float, themeColor: Color, alpha: Int) {
        val bgAlpha = (alpha * backgroundAlpha / 255F).toInt()
        val bgColor = Color(13, 13, 15, bgAlpha)
        val gold = Color(212, 175, 55, alpha)
        val goldDim = Color(212, 175, 55, (alpha * 0.55F).toInt())
        val white = Color(245, 245, 245, alpha)
        val dim = Color(160, 160, 165, alpha)

        RenderUtils.drawRoundedRect(x, y, x + w, y + h, bgColor.rgb, 8F)
        // 左侧金带
        RenderUtils.drawRect(x, y, x + 16, y + h, gold.rgb)

        // "MVP" 竖排
        val ribbonFont = Fonts.fontSemibold35
        val chars = firstLineText.toCharArray()
        val charY = y + (h - chars.size * (ribbonFont.fontHeight + 2)) / 2F
        for ((idx, c) in chars.withIndex()) {
            val cw = ribbonFont.getStringWidth(c.toString())
            ribbonFont.drawString(c.toString(), x + 8F - cw / 2F, charY + idx * (ribbonFont.fontHeight + 2), Color(20, 20, 20, alpha).rgb)
        }

        val avSize = 36
        val avX = x + 24F
        val avY = y + (h - avSize) / 2F
        val skinLocation: ResourceLocation? = mc.thePlayer?.locationSkin
        if (skinLocation != null) {
            RenderUtils.drawHead(skinLocation, avX.toInt(), avY.toInt(), avSize, avSize, Color.WHITE)
        }

        val playerName = mc.thePlayer?.name ?: "Player"
        val nameFont = Fonts.fontSemibold35
        val subFont = Fonts.fontRegular30
        val textX = avX + avSize + 12F
        nameFont.drawString(playerName, textX, avY + 2F, white.rgb)
        subFont.drawString(secondLineText, textX, avY + 22F, goldDim.rgb)

        if (selectedMvpMusicName != "无") {
            subFont.drawString("♪ $selectedMvpMusicName", textX, avY + 40F, dim.rgb)
        }
    }

    // 科技 - HUD 边角支架
    private fun renderTechStyle(x: Float, y: Float, w: Float, h: Float, themeColor: Color, alpha: Int) {
        val bgAlpha = (alpha * backgroundAlpha / 255F).toInt()
        val bgColor = Color(22, 25, 32, bgAlpha)
        val accent = Color(themeColor.red, themeColor.green, themeColor.blue, alpha)
        val accentDim = Color(themeColor.red, themeColor.green, themeColor.blue, (alpha * 0.45F).toInt())
        val white = Color(235, 240, 248, alpha)
        val dim = Color(150, 160, 178, alpha)

        RenderUtils.drawRoundedRect(x, y, x + w, y + h, bgColor.rgb, 6F)

        // 四角支架
        val cl = 10F
        val ct = 2F
        val off = 3F
        // 左上
        RenderUtils.drawRect(x + off, y + off, x + off + cl, y + off + ct, accent.rgb)
        RenderUtils.drawRect(x + off, y + off, x + off + ct, y + off + cl, accent.rgb)
        // 右上
        RenderUtils.drawRect(x + w - off - cl, y + off, x + w - off, y + off + ct, accent.rgb)
        RenderUtils.drawRect(x + w - off - ct, y + off, x + w - off, y + off + cl, accent.rgb)
        // 左下
        RenderUtils.drawRect(x + off, y + h - off - ct, x + off + cl, y + h - off, accent.rgb)
        RenderUtils.drawRect(x + off, y + h - off - cl, x + off + ct, y + h - off, accent.rgb)
        // 右下
        RenderUtils.drawRect(x + w - off - cl, y + h - off - ct, x + w - off, y + h - off, accent.rgb)
        RenderUtils.drawRect(x + w - off - ct, y + h - off - cl, x + w - off, y + h - off, accent.rgb)

        // 顶部标签
        val labelFont = Fonts.fontSemibold35
        labelFont.drawString("[ $firstLineText ]", x + off + cl + 4F, y + off + 1F, accent.rgb)

        val avSize = 32
        val avX = x + 14F
        val avY = y + h / 2F - avSize / 2F + 4F
        val skinLocation: ResourceLocation? = mc.thePlayer?.locationSkin
        if (skinLocation != null) {
            RenderUtils.drawHead(skinLocation, avX.toInt(), avY.toInt(), avSize, avSize, Color.WHITE)
        }

        val playerName = mc.thePlayer?.name ?: "Player"
        val nameFont = Fonts.fontSemibold35
        val subFont = Fonts.fontRegular30
        val textX = avX + avSize + 12F
        nameFont.drawString(playerName, textX, avY, white.rgb)
        subFont.drawString(secondLineText, textX, avY + 18F, dim.rgb)

        if (selectedMvpMusicName != "无") {
            subFont.drawString("> $selectedMvpMusicName", textX, avY + 34F, accentDim.rgb)
        }
    }

    // 高雅 - 上下双线 + 居中徽章
    private fun renderRefinedStyle(x: Float, y: Float, w: Float, h: Float, themeColor: Color, alpha: Int) {
        val bgAlpha = (alpha * backgroundAlpha / 255F).toInt()
        val bgColor = Color(27, 42, 78, bgAlpha)
        val silver = Color(214, 220, 232, alpha)
        val silverDim = Color(214, 220, 232, (alpha * 0.5F).toInt())
        val pearl = Color(240, 240, 245, alpha)
        val dim = Color(170, 178, 200, alpha)

        RenderUtils.drawRoundedRect(x, y, x + w, y + h, bgColor.rgb, 6F)

        // 上下双横线
        RenderUtils.drawRect(x + 10, y + 5, x + w - 10, y + 6, silverDim.rgb)
        RenderUtils.drawRect(x + 10, y + h - 6, x + w - 10, y + h - 5, silverDim.rgb)
        // 线条中央小菱形
        val cx = x + w / 2F
        RenderUtils.drawRect(cx - 2, y + 3, cx + 2, y + 8, silver.rgb)
        RenderUtils.drawRect(cx - 2, y + h - 8, cx + 2, y + h - 3, silver.rgb)

        val avSize = 28
        val avX = cx - avSize / 2F
        val avY = y + 11F
        val skinLocation: ResourceLocation? = mc.thePlayer?.locationSkin
        if (skinLocation != null) {
            RenderUtils.drawHead(skinLocation, avX.toInt(), avY.toInt(), avSize, avSize, Color.WHITE)
        }

        val playerName = mc.thePlayer?.name ?: "Player"
        val titleFont = Fonts.fontSemibold35
        val bodyFont = Fonts.fontRegular30
        val titleW = titleFont.getStringWidth(firstLineText)
        titleFont.drawString(firstLineText, cx - titleW / 2F, avY + avSize + 2F, silver.rgb)

        val secondLine = "$playerName$secondLineText"
        val bodyW = bodyFont.getStringWidth(secondLine)
        bodyFont.drawString(secondLine, cx - bodyW / 2F, avY + avSize + 19F, pearl.rgb)

        if (selectedMvpMusicName != "无") {
            val musicStr = "♪ $selectedMvpMusicName"
            val mw = bodyFont.getStringWidth(musicStr)
            bodyFont.drawString(musicStr, cx - mw / 2F, y + h - 14F, dim.rgb)
        }
    }

    private class VolumeControlledAudioDevice : JavaSoundAudioDevice() {
        private var volumeControl: javax.sound.sampled.FloatControl? = null

        fun setVolume(volume: Float) {
            try {
                if (volumeControl == null) {
                    findVolumeControl()
                }
                volumeControl?.let { ctrl ->
                    val min = ctrl.minimum
                    val max = ctrl.maximum
                    val range = max - min
                    val gain = range * volume.coerceIn(0F, 1F) + min
                    ctrl.value = gain
                }
            } catch (e: Exception) {
            }
        }

        private fun findVolumeControl() {
            try {
                val field = JavaSoundAudioDevice::class.java.getDeclaredField("source")
                field.isAccessible = true
                val source = field.get(this) as? javax.sound.sampled.SourceDataLine
                if (source != null && source.isControlSupported(javax.sound.sampled.FloatControl.Type.MASTER_GAIN)) {
                    volumeControl = source.getControl(javax.sound.sampled.FloatControl.Type.MASTER_GAIN) as javax.sound.sampled.FloatControl
                }
            } catch (e: Exception) {
            }
        }
    }
}
