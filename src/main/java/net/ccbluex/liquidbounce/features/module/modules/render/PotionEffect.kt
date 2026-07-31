package net.ccbluex.liquidbounce.features.module.modules.render

import net.ccbluex.liquidbounce.event.Render2DEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.ui.font.Fonts
import net.ccbluex.liquidbounce.utils.GlowUtils.drawGlow
import net.ccbluex.liquidbounce.utils.render.BlurUtils
import net.ccbluex.liquidbounce.utils.render.LBPPAnimationUtils
import net.ccbluex.liquidbounce.utils.render.RenderUtils
import net.ccbluex.liquidbounce.utils.render.RenderUtils.drawCircle
import net.ccbluex.liquidbounce.utils.render.RenderUtils.drawGradientRect
import net.ccbluex.liquidbounce.utils.render.RenderUtils.drawRoundedBorder
import net.ccbluex.liquidbounce.utils.render.RenderUtils.drawRoundedRect
import net.ccbluex.liquidbounce.utils.render.RenderUtils.drawTexturedModalRect
import net.ccbluex.liquidbounce.utils.render.RenderUtils.makeScissorBox
import net.minecraft.client.gui.ScaledResolution
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.resources.I18n
import net.minecraft.potion.Potion
import net.minecraft.potion.PotionEffect
import net.minecraft.util.ResourceLocation
import org.lwjgl.opengl.GL11.*
import java.awt.Color
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

object PotionEffect : Module("PotionEffect", Category.RENDER) {

    private val style by choices(
        "Style",
        arrayOf("Default", "Modern", "Compact", "Outline", "Card", "Neon", "Bar"),
        "Default"
    )
    private val backgroundAlpha by int("BackgroundAlpha", 120, 0..255)
    private val leftRectRadius by float("LeftRectValue", 0f, 0f..10f)
    private val rightRectRadius by float("RightRectValue", 0f, 0f..10f)
    private val animationSpeed by float("AnimationSpeed", 0.15f, 0.01f..0.5f)
    private val barWidth by float("BarWidth", 5f, 1f..10f)
    private val xOffset by float("X-Offset", 5f, 0f..50f)
    private val yOffset by float("Y-Offset", 0f, -50f..50f)
    private val spacing by float("Spacing", 5f, 0f..10f)
    private val fontMode by choices("Font", arrayOf("Minecraft", "HarmonyOS"), "Minecraft")
    private val blur by boolean("Blur", false)

    // onRender2D 均在主线程触发，无需 CopyOnWriteArrayList
    private val activePotions = ArrayList<AnimatedPotion>()
    private val inventoryTexture = ResourceLocation("textures/gui/container/inventory.png")

    // 默认药水最大时长（秒），用于归一化进度条/进度环
    private const val DEFAULT_MAX_DURATION = 180

    val onRender2D = handler<Render2DEvent> {
        if (mc.thePlayer == null) return@handler

        val sr = ScaledResolution(mc)

        updatePotionList()

        if (activePotions.isEmpty()) return@handler

        activePotions.forEach { it.update(animationSpeed) }
        activePotions.removeIf { it.isReadyToRemove() }

        activePotions.sortBy { it.effect.duration }

        val totalHeight = activePotions.sumByDouble {
            (it.heightForStyle() + spacing) * it.animationY.toDouble()
        }.toFloat() - spacing
        var currentY = (sr.scaledHeight / 2f) - (totalHeight / 2f) + yOffset

        for (potion in activePotions) {
            potion.draw(currentY, xOffset, style, fontMode, blur)
            currentY += (potion.heightForStyle() + spacing) * potion.animationY
        }
    }

    private fun updatePotionList() {
        val playerEffects = mc.thePlayer.activePotionEffects.filter { Potion.potionTypes[it.potionID] != null }

        activePotions.forEach { animatedPotion ->
            if (playerEffects.none { it.potionID == animatedPotion.effect.potionID }) {
                animatedPotion.isMarkedForRemoval = true
            }
        }

        playerEffects.forEach { playerEffect ->
            if (activePotions.none { it.effect.potionID == playerEffect.potionID }) {
                activePotions.add(AnimatedPotion(playerEffect))
            } else {
                activePotions.find { it.effect.potionID == playerEffect.potionID }?.effect = playerEffect
            }
        }
    }

    private class AnimatedPotion(var effect: PotionEffect) {
        val boxWidth = 120f
        val boxHeight = 32f

        // 紧凑样式专用尺寸
        val compactWidth = 95f
        val compactHeight = 22f

        // 卡片样式专用尺寸
        val cardWidth = 130f
        val cardHeight = 40f

        var isMarkedForRemoval = false
        private var animationX = -boxWidth - 10f
        var animationY = 0f

        private val potion: Potion = Potion.potionTypes[effect.potionID]
        private val dataColor = potionColorMap[effect.potionID] ?: Color.GRAY

        fun heightForStyle(): Float = when (style) {
            "Compact" -> compactHeight
            "Card" -> cardHeight
            else -> boxHeight
        }

        private fun widthForStyle(): Float = when (style) {
            "Compact" -> compactWidth
            "Card" -> cardWidth
            else -> boxWidth
        }

        fun update(speed: Float) {
            val targetX = if (isMarkedForRemoval) -boxWidth - 10f else 0f
            val targetY = if (isMarkedForRemoval) 0f else 1f
            animationX = LBPPAnimationUtils.animate(targetX, animationX, speed)
            animationY = LBPPAnimationUtils.animate(targetY, animationY, speed)
        }

        fun isReadyToRemove(): Boolean {
            return isMarkedForRemoval && animationX <= -boxWidth
        }

        fun draw(y: Float, xOffset: Float, style: String, fontMode: String, blur: Boolean) {
            val w = widthForStyle()
            val h = heightForStyle()

            val startX = xOffset + animationX
            val startY = y
            val endX = startX + w
            val endY = startY + h

            if (startX > w) return

            val animatedHeight = h * animationY
            if (animatedHeight < 1) return

            when (style) {
                "Default" -> drawDefault(startX, startY, endX, animatedHeight, h, fontMode, blur)
                "Modern" -> drawModern(startX, startY, endX, animatedHeight, h, fontMode, blur)
                "Compact" -> drawCompact(startX, startY, endX, animatedHeight, h, fontMode, blur)
                "Outline" -> drawOutline(startX, startY, endX, animatedHeight, h, fontMode, blur)
                "Card" -> drawCard(startX, startY, endX, animatedHeight, h, fontMode, blur)
                "Neon" -> drawNeon(startX, startY, endX, animatedHeight, h, fontMode, blur)
                "Bar" -> drawBar(startX, startY, endX, animatedHeight, h, fontMode, blur)
            }
        }

        // ==================== 样式 1: Default（原版） ====================
        private fun drawDefault(
            startX: Float, startY: Float, endX: Float,
            animatedHeight: Float, fullHeight: Float, fontMode: String, blur: Boolean
        ) {
            val bgColor = Color(40, 40, 40, (backgroundAlpha * animationY).toInt())

            if (blur) {
                BlurUtils.blurAreaRounded(startX, startY, endX, startY + animatedHeight, leftRectRadius, 8f)
            }
            drawRoundedRect(startX, startY, endX, startY + animatedHeight, bgColor.rgb, leftRectRadius)
            drawGlow(startX, startY, endX - startX, fullHeight, 8, bgColor)
            drawRoundedRect(
                startX, startY, startX + barWidth, startY + animatedHeight,
                dataColor.rgb, rightRectRadius
            )

            renderIconAndText(startX, startY, animatedHeight, 8f, 30f, fontMode)
        }

        // ==================== 样式 2: Modern（渐变背景 + 底部进度条） ====================
        private fun drawModern(
            startX: Float, startY: Float, endX: Float,
            animatedHeight: Float, fullHeight: Float, fontMode: String, blur: Boolean
        ) {
            val bgStart = Color(30, 30, 35, (backgroundAlpha * animationY).toInt())
            val bgEnd = Color(
                (dataColor.red * 0.3f).toInt().coerceAtMost(255),
                (dataColor.green * 0.3f).toInt().coerceAtMost(255),
                (dataColor.blue * 0.3f).toInt().coerceAtMost(255),
                (backgroundAlpha * animationY).toInt()
            )

            if (blur) {
                BlurUtils.blurAreaRounded(startX, startY, endX, startY + animatedHeight, 6f, 8f)
            }
            drawRoundedRect(startX, startY, endX, startY + animatedHeight, bgStart.rgb, 6f)
            drawGradientRect(
                startX, startY, endX, startY + animatedHeight,
                bgEnd.rgb, bgStart.rgb, 0f
            )
            drawGlow(startX, startY, endX - startX, fullHeight, 6, bgEnd)

            // 底部进度条（剩余时长比例）
            val progress = computeDurationProgress()
            val barY = startY + animatedHeight - 3f
            drawRoundedRect(
                startX, barY, startX + (endX - startX) * progress, barY + 2f,
                dataColor.rgb, 1f
            )

            // 右侧色块装饰
            drawRoundedRect(
                endX - 4f, startY, endX, startY + animatedHeight,
                dataColor.rgb, 2f
            )

            renderIconAndText(startX, startY, animatedHeight, 8f, 30f, fontMode)
        }

        // ==================== 样式 3: Compact（紧凑单行） ====================
        private fun drawCompact(
            startX: Float, startY: Float, endX: Float,
            animatedHeight: Float, fullHeight: Float, fontMode: String, blur: Boolean
        ) {
            val bgColor = Color(35, 35, 40, (backgroundAlpha * animationY).toInt())
            val accent = Color(
                dataColor.red, dataColor.green, dataColor.blue,
                (255 * animationY).toInt()
            )

            if (blur) {
                BlurUtils.blurAreaRounded(startX, startY, endX, startY + animatedHeight, 4f, 8f)
            }
            drawRoundedRect(startX, startY, endX, startY + animatedHeight, bgColor.rgb, 4f)
            drawGlow(startX, startY, endX - startX, fullHeight, 4, bgColor)

            // 左侧小圆点（替代色条）
            val dotRadius = 3f
            val dotX = startX + 8f
            val dotY = startY + animatedHeight / 2f
            drawFilledCircle(dotX, dotY, dotRadius, accent)

            // 图标
            if (potion.hasStatusIcon()) {
                mc.textureManager.bindTexture(inventoryTexture)
                GlStateManager.color(1f, 1f, 1f, animationY)
                val iconX = potion.statusIconIndex % 8 * 18
                val iconY = 198 + potion.statusIconIndex / 8 * 18
                drawTexturedModalRect(
                    (startX + 16f).toInt(), (startY + (animatedHeight - 16) / 2).toInt(),
                    iconX, iconY, 18, 18, 0.0F
                )
            }

            // 单行文字：名称 + 时长
            val potionName = I18n.format(potion.name)
            val displayName = potionName + if (effect.amplifier > 0) " ${effect.amplifier + 1}" else ""
            val duration = effect.duration / 20
            val durationText = formatDuration(duration)
            val nameColor = accent.rgb
            val durationColor = if (duration <= 10) {
                Color(255, 80, 80, (255 * animationY).toInt()).rgb
            } else {
                Color(220, 220, 220, (200 * animationY).toInt()).rgb
            }

            glPushMatrix()
            makeScissorBox(startX, startY, endX, startY + animatedHeight)
            glEnable(GL_SCISSOR_TEST)

            val textX = startX + 36f
            val textY = startY + (animatedHeight / 2f) - 4f
            when (fontMode) {
                "Minecraft" -> {
                    mc.fontRendererObj.drawString(displayName, textX.toInt(), textY.toInt(), nameColor)
                    val durX = textX + mc.fontRendererObj.getStringWidth(displayName) + 6f
                    mc.fontRendererObj.drawString(durationText, durX.toInt(), textY.toInt(), durationColor)
                }
                "HarmonyOS" -> {
                    Fonts.fontSemibold35.drawString(displayName, textX, textY, nameColor)
                    val durX = textX + Fonts.fontSemibold35.getStringWidth(displayName) + 6f
                    Fonts.fontSemibold35.drawString(durationText, durX, textY, durationColor)
                }
            }

            glDisable(GL_SCISSOR_TEST)
            glPopMatrix()
        }

        // ==================== 样式 4: Outline（仅描边） ====================
        private fun drawOutline(
            startX: Float, startY: Float, endX: Float,
            animatedHeight: Float, fullHeight: Float, fontMode: String, blur: Boolean
        ) {
            val alpha = (255 * animationY).toInt()
            val border = Color(dataColor.red, dataColor.green, dataColor.blue, alpha)
            val faintFill = Color(
                dataColor.red, dataColor.green, dataColor.blue,
                (30 * animationY).toInt()
            )

            if (blur) {
                BlurUtils.blurAreaRounded(startX, startY, endX, startY + animatedHeight, 5f, 8f)
            }
            // 极淡的填充
            drawRoundedRect(startX, startY, endX, startY + animatedHeight, faintFill.rgb, 5f)
            // 彩色描边
            drawRoundedBorder(startX, startY, endX, startY + animatedHeight, 1.5f, border.rgb, 5f)

            // 顶部细横线装饰
            drawRoundedRect(
                startX + 4f, startY + 2f, startX + 20f, startY + 3.5f,
                border.rgb, 0.5f
            )

            renderIconAndText(startX, startY, animatedHeight, 8f, 30f, fontMode)
        }

        // ==================== 样式 5: Card（卡片 + 圆形进度环） ====================
        private fun drawCard(
            startX: Float, startY: Float, endX: Float,
            animatedHeight: Float, fullHeight: Float, fontMode: String, blur: Boolean
        ) {
            val bgColor = Color(28, 28, 33, (backgroundAlpha * animationY).toInt())
            val accent = Color(
                dataColor.red, dataColor.green, dataColor.blue,
                (255 * animationY).toInt()
            )

            if (blur) {
                BlurUtils.blurAreaRounded(startX, startY, endX, startY + animatedHeight, 8f, 8f)
            }
            drawRoundedRect(startX, startY, endX, startY + animatedHeight, bgColor.rgb, 8f)
            drawGlow(startX, startY, endX - startX, fullHeight, 8, bgColor)

            // 左侧大色块
            drawRoundedRect(
                startX, startY, startX + 6f, startY + animatedHeight,
                accent.rgb, 3f
            )

            // 右侧圆形进度环
            val ringCx = endX - 16f
            val ringCy = startY + animatedHeight / 2f
            val ringR = 11f
            // 背景圆环
            drawCircle(
                ringCx, ringCy, ringR, 1.2f, 0, 360,
                Color(80, 80, 80, (180 * animationY).toInt())
            )
            // 进度弧（按时长比例）
            val progress = computeDurationProgress().coerceIn(0f, 1f)
            val endAngle = (360 * progress).toInt()
            drawCircle(ringCx, ringCy, ringR, 1.8f, 0, endAngle, accent)

            // 图标
            if (potion.hasStatusIcon()) {
                mc.textureManager.bindTexture(inventoryTexture)
                GlStateManager.color(1f, 1f, 1f, animationY)
                val iconX = potion.statusIconIndex % 8 * 18
                val iconY = 198 + potion.statusIconIndex / 8 * 18
                drawTexturedModalRect(
                    (startX + 14f).toInt(), (startY + (animatedHeight - 18) / 2).toInt(),
                    iconX, iconY, 18, 18, 0.0F
                )
            }

            // 名称在上，时长在下
            val potionName = I18n.format(potion.name)
            val displayName = potionName + if (effect.amplifier > 0) " ${effect.amplifier + 1}" else ""
            val duration = effect.duration / 20
            val durationText = formatDuration(duration)
            val nameColor = accent.rgb
            val durationColor = if (duration <= 10) {
                Color(255, 80, 80, (255 * animationY).toInt()).rgb
            } else {
                Color(230, 230, 230, (200 * animationY).toInt()).rgb
            }

            glPushMatrix()
            makeScissorBox(startX, startY, endX, startY + animatedHeight)
            glEnable(GL_SCISSOR_TEST)

            val textX = startX + 36f
            val textY1 = startY + (animatedHeight / 2f) - 11f
            val textY2 = startY + (animatedHeight / 2f) + 1f
            when (fontMode) {
                "Minecraft" -> {
                    mc.fontRendererObj.drawString(displayName, textX.toInt(), textY1.toInt(), nameColor)
                    mc.fontRendererObj.drawString(durationText, textX.toInt(), textY2.toInt(), durationColor)
                }
                "HarmonyOS" -> {
                    Fonts.fontSemibold35.drawString(displayName, textX, textY1, nameColor)
                    Fonts.fontSemibold35.drawString(durationText, textX, textY2, durationColor)
                }
            }

            glDisable(GL_SCISSOR_TEST)
            glPopMatrix()
        }

        // ==================== 样式 6: Neon（霓虹强光晕） ====================
        private fun drawNeon(
            startX: Float, startY: Float, endX: Float,
            animatedHeight: Float, fullHeight: Float, fontMode: String, blur: Boolean
        ) {
            val alpha = (255 * animationY).toInt()
            val neon = Color(dataColor.red, dataColor.green, dataColor.blue, alpha)
            val darkBg = Color(15, 15, 20, (backgroundAlpha * animationY).toInt())

            // 双层光晕
            drawGlow(startX, startY, endX - startX, fullHeight, 16, neon)
            drawGlow(startX, startY, endX - startX, fullHeight, 8, neon)

            if (blur) {
                BlurUtils.blurAreaRounded(startX, startY, endX, startY + animatedHeight, 7f, 8f)
            }
            drawRoundedRect(startX, startY, endX, startY + animatedHeight, darkBg.rgb, 7f)

            // 顶部和底部彩色发光条
            drawRoundedRect(
                startX, startY, endX, startY + 2f,
                neon.rgb, 1f
            )
            drawRoundedRect(
                startX, startY + animatedHeight - 2f, endX, startY + animatedHeight,
                neon.rgb, 1f
            )
            // 左侧粗色条
            drawRoundedRect(
                startX, startY, startX + barWidth, startY + animatedHeight,
                neon.rgb, rightRectRadius
            )

            renderIconAndText(startX, startY, animatedHeight, 8f, 30f, fontMode)
        }

        // ==================== 样式 7: Bar（背景整体进度条） ====================
        private fun drawBar(
            startX: Float, startY: Float, endX: Float,
            animatedHeight: Float, fullHeight: Float, fontMode: String, blur: Boolean
        ) {
            val progress = computeDurationProgress()
            val alpha = (255 * animationY).toInt()

            // 背景轨
            val trackColor = Color(40, 40, 45, (backgroundAlpha * animationY).toInt())
            // 进度填充（带颜色渐变）
            val fillColor = Color(dataColor.red, dataColor.green, dataColor.blue, alpha)
            val fillEnd = Color(
                min(255, dataColor.red + 40),
                min(255, dataColor.green + 40),
                min(255, dataColor.blue + 40),
                alpha
            )

            if (blur) {
                BlurUtils.blurAreaRounded(startX, startY, endX, startY + animatedHeight, 5f, 8f)
            }
            drawRoundedRect(startX, startY, endX, startY + animatedHeight, trackColor.rgb, 5f)

            // 进度填充区域（带裁剪，按比例）
            glPushMatrix()
            makeScissorBox(startX, startY, startX + (endX - startX) * progress, startY + animatedHeight)
            glEnable(GL_SCISSOR_TEST)
            drawGradientRect(
                startX, startY, endX, startY + animatedHeight,
                fillColor.rgb, fillEnd.rgb, 0f
            )
            drawRoundedRect(startX, startY, endX, startY + animatedHeight, fillColor.rgb, 5f)
            glDisable(GL_SCISSOR_TEST)
            glPopMatrix()

            drawGlow(startX, startY, endX - startX, fullHeight, 6, fillColor)

            // 右侧边缘指示器
            drawRoundedRect(
                endX - 3f, startY, endX, startY + animatedHeight,
                Color(255, 255, 255, (180 * animationY).toInt()).rgb, 1f
            )

            renderIconAndText(startX, startY, animatedHeight, 8f, 30f, fontMode)
        }

        // ==================== 共用渲染逻辑 ====================
        private fun renderIconAndText(
            startX: Float, startY: Float, animatedHeight: Float,
            iconOffsetX: Float, textOffsetX: Float, fontMode: String
        ) {
            if (potion.hasStatusIcon()) {
                mc.textureManager.bindTexture(inventoryTexture)
                GlStateManager.color(1f, 1f, 1f, animationY)
                val iconX = potion.statusIconIndex % 8 * 18
                val iconY = 198 + potion.statusIconIndex / 8 * 18
                drawTexturedModalRect(
                    (startX + iconOffsetX).toInt(),
                    (startY + (animatedHeight - 18) / 2).toInt(),
                    iconX, iconY, 18, 18, 0.0F
                )
            }

            val textX = startX + textOffsetX
            val textY = startY + (animatedHeight / 2f) - 8f
            val nameColor = Color(
                dataColor.red, dataColor.green, dataColor.blue,
                (255 * animationY).toInt()
            ).rgb
            val potionName = I18n.format(potion.name)
            val displayName = potionName + if (effect.amplifier > 0) " ${effect.amplifier + 1}" else ""

            val duration = effect.duration / 20
            val durationText = formatDuration(duration)
            val durationColor = if (duration <= 10) {
                Color(255, 80, 80, (255 * animationY).toInt()).rgb
            } else {
                Color(255, 255, 255, (200 * animationY).toInt()).rgb
            }

            glPushMatrix()
            makeScissorBox(startX, startY, startX + boxWidth, startY + animatedHeight)
            glEnable(GL_SCISSOR_TEST)

            when (fontMode) {
                "Minecraft" -> {
                    mc.fontRendererObj.drawString(displayName, textX.toInt(), textY.toInt(), nameColor)
                    mc.fontRendererObj.drawString(durationText, textX.toInt(), (textY + 11).toInt(), durationColor)
                }
                "HarmonyOS" -> {
                    Fonts.fontSemibold35.drawString(displayName, textX, textY, nameColor)
                    Fonts.fontSemibold35.drawString(durationText, textX, textY + 11, durationColor)
                }
            }

            glDisable(GL_SCISSOR_TEST)
            glPopMatrix()
        }

        /**
         * 计算剩余时长进度（0..1），1 = 满时长，0 = 即将结束。
         * 以 DEFAULT_MAX_DURATION 为参考上界，超出时钳制为 1。
         */
        private fun computeDurationProgress(): Float {
            val durSec = effect.duration / 20f
            return (durSec / DEFAULT_MAX_DURATION).coerceIn(0f, 1f)
        }

        /**
         * 格式化时长为 "MM:SS"，避免每帧调用 String.format 创建 Formatter 对象。
         */
        private fun formatDuration(duration: Int): String {
            val minutes = duration / 60
            val seconds = duration % 60
            val sb = StringBuilder(5)
            if (minutes < 10) sb.append('0')
            sb.append(minutes)
            sb.append(':')
            if (seconds < 10) sb.append('0')
            sb.append(seconds)
            return sb.toString()
        }

        private fun drawFilledCircle(x: Float, y: Float, radius: Float, color: Color) {
            glEnable(GL_BLEND)
            glDisable(GL_TEXTURE_2D)
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
            GlStateManager.color(
                color.red / 255f, color.green / 255f, color.blue / 255f, color.alpha / 255f
            )
            glBegin(GL_TRIANGLE_FAN)
            glVertex2f(x, y)
            var i = 0
            while (i <= 360) {
                val rad = (i * PI / 180.0)
                glVertex2f(
                    (x + cos(rad) * radius).toFloat(),
                    (y + sin(rad) * radius).toFloat()
                )
                i += 10
            }
            glEnd()
            glEnable(GL_TEXTURE_2D)
            glDisable(GL_BLEND)
            GlStateManager.color(1f, 1f, 1f, 1f)
        }
    }

    private val potionColorMap = mapOf(
        Potion.moveSpeed.id to Color(124, 175, 198),
        Potion.digSpeed.id to Color(217, 192, 67),
        Potion.damageBoost.id to Color(204, 91, 89),
        Potion.jump.id to Color(34, 255, 76),
        Potion.regeneration.id to Color(221, 122, 146),
        Potion.resistance.id to Color(153, 69, 59),
        Potion.fireResistance.id to Color(228, 154, 58),
        Potion.waterBreathing.id to Color(46, 82, 153),
        Potion.invisibility.id to Color(127, 131, 146),
        Potion.nightVision.id to Color(31, 31, 165),
        Potion.healthBoost.id to Color(248, 125, 35),
        Potion.absorption.id to Color(36, 147, 147),
        Potion.saturation.id to Color(248, 36, 35),
        Potion.moveSlowdown.id to Color(90, 108, 127),
        Potion.digSlowdown.id to Color(74, 66, 23),
        Potion.weakness.id to Color(72, 77, 77),
        Potion.poison.id to Color(78, 157, 48),
        Potion.wither.id to Color(53, 42, 39),
        Potion.hunger.id to Color(88, 83, 22),
        Potion.confusion.id to Color(85, 29, 74),
        Potion.blindness.id to Color(31, 31, 36)
    )
}
