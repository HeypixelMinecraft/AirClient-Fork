/*
 * Air Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 */
package net.ccbluex.liquidbounce.features.module.modules.render

import net.ccbluex.liquidbounce.LiquidBounce.CLIENT_NAME
import net.ccbluex.liquidbounce.LiquidBounce.hud
import net.ccbluex.liquidbounce.event.*
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.ui.client.hud.designer.GuiHudDesigner
import net.ccbluex.liquidbounce.ui.client.hud.element.Element.Companion.MAX_GRADIENT_COLORS
import net.ccbluex.liquidbounce.utils.render.ColorSettingsFloat
import net.ccbluex.liquidbounce.utils.render.ColorSettingsInteger
import net.ccbluex.liquidbounce.utils.render.RenderUtils
import net.minecraft.client.gui.Gui
import net.minecraft.client.gui.GuiChat
import net.minecraft.client.gui.ScaledResolution
import net.minecraft.util.ResourceLocation
import net.ccbluex.liquidbounce.ui.font.Fonts
import net.ccbluex.liquidbounce.utils.render.AnimationUtils
import net.minecraftforge.client.event.RenderGameOverlayEvent
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.fml.common.eventhandler.EventPriority
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import org.lwjgl.opengl.GL11
import java.awt.Color
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

object HUD : Module("HUD", Category.RENDER, gameDetecting = false, defaultState = true, defaultHidden = true) {
    init {
        MinecraftForge.EVENT_BUS.register(this)
    }

    private fun lerp(start: Float, end: Float, percent: Float): Float {
        return start + (end - start) * percent
    }
    
    val customHotbar by boolean("CustomHotbar", true)
    private var hotBarX = 0F

    val smoothHotbarSlot by boolean("SmoothHotbarSlot", true) { customHotbar }

    val roundedHotbarRadius by float("RoundedHotbar-Radius", 3F, 0F..5F) { customHotbar }

    val inventoryParticle by boolean("InventoryParticle", false)
    private val blur by boolean("Blur", false)
    private val fontChat by choices("FontChat", arrayOf("Off", "Minecraft", "Regular35", "Regular40", "Semibold35", "Semibold40"), "Minecraft")
    val chatCombine by boolean("ChatCombine", true)
    val chatAnimation by boolean("ChatAnimation", true)
    val chatAnimationSpeed by float("Chat-AnimationSpeed", 0.1f, 0.01f..1.0f) { chatAnimation }
    val chatRect by boolean("ChatRect", true) { chatAnimation }

    val customHealthBar by boolean("自定义血条", true)
    private val healthStyle by choices("血条样式", arrayOf("圆角", "渐变圆角", "闪烁", "极简", "主题", "iOS", "霓虹", "原版"), "圆角") { customHealthBar }
    private val healthBarWidth by int("血条宽度", 90, 50..200) { customHealthBar }
    private val healthBarHeight by int("血条高度", 10, 5..20) { customHealthBar }
    private val healthBarOffsetX by int("血条X偏移", 0, -500..500) { customHealthBar }
    private val healthBarOffsetY by int("血条Y偏移", 0, -500..500) { customHealthBar }
    private val healthColor by color("血条颜色", Color(255, 50, 50)) { customHealthBar && healthStyle in listOf("圆角", "渐变圆角", "闪烁", "极简", "主题", "iOS", "霓虹") }
    private val healthGradientColor2 by color("血条渐变色2", Color(255, 150, 50)) { customHealthBar && healthStyle == "渐变圆角" }
    private val healthBgColor by color("血条背景色", Color(50, 50, 50, 150)) { customHealthBar }
    private val healthText by boolean("显示血量文字", true) { customHealthBar && healthStyle != "原版" }
    private val healthTextShadow by boolean("血量文字阴影", true) { customHealthBar && healthText }
    private val healthRoundedRadius by float("血条圆角", 3f, 0f..10f) { customHealthBar && healthStyle in listOf("圆角", "渐变圆角", "闪烁", "主题", "iOS", "霓虹") }
    private val healthAnimSpeed by float("血条动画速度", 0.3f, 0.05f..1f) { customHealthBar }

    val customFoodBar by boolean("自定义饥饿值", true)
    private val foodStyle by choices("饥饿值样式", arrayOf("圆角", "渐变圆角", "闪烁", "极简", "主题", "iOS", "霓虹", "原版"), "圆角") { customFoodBar }
    private val foodBarWidth by int("饥饿值宽度", 90, 50..200) { customFoodBar }
    private val foodBarHeight by int("饥饿值高度", 10, 5..20) { customFoodBar }
    private val foodBarOffsetX by int("饥饿值X偏移", 0, -500..500) { customFoodBar }
    private val foodBarOffsetY by int("饥饿值Y偏移", 0, -500..500) { customFoodBar }
    private val foodColor by color("饥饿值颜色", Color(139, 90, 43)) { customFoodBar && foodStyle in listOf("圆角", "渐变圆角", "闪烁", "极简", "主题", "iOS", "霓虹") }
    private val foodGradientColor2 by color("饥饿值渐变色2", Color(194, 124, 57)) { customFoodBar && foodStyle == "渐变圆角" }
    private val foodBgColor by color("饥饿值背景色", Color(50, 50, 50, 150)) { customFoodBar }
    private val foodText by boolean("显示饥饿值文字", true) { customFoodBar && foodStyle != "原版" }
    private val foodTextShadow by boolean("饥饿值文字阴影", true) { customFoodBar && foodText }
    private val foodRoundedRadius by float("饥饿值圆角", 3f, 0f..10f) { customFoodBar && foodStyle in listOf("圆角", "渐变圆角", "闪烁", "主题", "iOS", "霓虹") }
    private val foodAnimSpeed by float("饥饿值动画速度", 0.3f, 0.05f..1f) { customFoodBar }

    private var displayHealth = 0f
    private var lastHealth = 0f
    private var displayFood = 0f
    private var lastFood = 0
    private var easingHealth = 0f
    private var easingFood = 0f

    private val ICONS = ResourceLocation("textures/gui/icons.png")

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    fun onRenderGameOverlay(event: RenderGameOverlayEvent.Pre) {
        if (!handleEvents()) return

        val player = mc.thePlayer ?: return
        val resolution = ScaledResolution(mc)
        val width = resolution.scaledWidth
        val height = resolution.scaledHeight

        when (event.type) {
            RenderGameOverlayEvent.ElementType.HEALTH -> {
                if (customHealthBar) {
                    event.isCanceled = true
                    renderCustomHealthBar(player, width, height)
                }
            }
            RenderGameOverlayEvent.ElementType.FOOD -> {
                if (customFoodBar) {
                    event.isCanceled = true
                    renderCustomFoodBar(player, width, height)
                }
            }
            RenderGameOverlayEvent.ElementType.AIR -> {
                if (customHealthBar) {
                    event.isCanceled = true
                }
            }
            else -> {}
        }
    }

    /**
     * 根据样式和类型获取主题颜色
     */
    private fun getBarColor(style: String, baseColor: Color, gradientColor2: Color, index: Int = 0): Color {
        return when (style) {
            "主题" -> net.ccbluex.liquidbounce.utils.client.ClientThemesUtils.getColor(index)
            "霓虹" -> {
                val neonBase = net.ccbluex.liquidbounce.utils.client.ClientThemesUtils.getColor(index)
                Color(neonBase.red, neonBase.green, neonBase.blue, 220)
            }
            else -> baseColor
        }
    }

    /**
     * 渲染一个进度条 - 通用方法支持所有样式
     */
    private fun renderBar(
        barX: Float, barY: Float, barWidth: Float, barHeight: Float,
        percent: Float, style: String, baseColor: Color, gradientColor2: Color,
        bgColor: Color, radius: Float, showText: Boolean, textShadow: Boolean,
        textContent: String, isRightAligned: Boolean
    ) {
        GL11.glPushMatrix()
        GL11.glEnable(GL11.GL_BLEND)
        GL11.glDisable(GL11.GL_TEXTURE_2D)
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA)
        GL11.glShadeModel(GL11.GL_SMOOTH)

        val fillWidth = percent * barWidth

        when (style) {
            "极简" -> {
                RenderUtils.drawRoundedRect(barX, barY, barX + barWidth, barY + 2, bgColor.rgb, 1f, RenderUtils.RoundedCorners.ALL)
                if (fillWidth > 0) {
                    RenderUtils.drawRoundedRect(barX, barY, barX + fillWidth, barY + 2, baseColor.rgb, 1f, RenderUtils.RoundedCorners.ALL)
                }
            }
            "圆角" -> {
                RenderUtils.drawRoundedRect(barX, barY, barX + barWidth, barY + barHeight, bgColor.rgb, radius, RenderUtils.RoundedCorners.ALL)
                if (fillWidth > 0) {
                    RenderUtils.drawRoundedRect(barX, barY, barX + fillWidth, barY + barHeight, baseColor.rgb, radius, RenderUtils.RoundedCorners.ALL)
                }
            }
            "渐变圆角" -> {
                RenderUtils.drawRoundedRect(barX, barY, barX + barWidth, barY + barHeight, bgColor.rgb, radius, RenderUtils.RoundedCorners.ALL)
                if (fillWidth > 0) {
                    RenderUtils.withClipping(
                        main = { RenderUtils.drawRoundedRect(barX, barY, barX + fillWidth, barY + barHeight, 0, radius) },
                        toClip = { RenderUtils.drawGradientRect(barX, barY, barX + fillWidth, barY + barHeight, baseColor.rgb, gradientColor2.rgb, 0f) }
                    )
                }
            }
            "闪烁" -> {
                val time = System.currentTimeMillis() % 2000 / 2000f
                RenderUtils.drawRoundedRect(barX, barY, barX + barWidth, barY + barHeight, bgColor.rgb, radius, RenderUtils.RoundedCorners.ALL)
                if (fillWidth > 0) {
                    RenderUtils.drawRoundedRect(barX, barY, barX + fillWidth, barY + barHeight, baseColor.rgb, radius, RenderUtils.RoundedCorners.ALL)
                    val shimmerW = 30f
                    val shimmerX = (time * (barWidth + shimmerW)) - shimmerW
                    GL11.glColor4f(1f, 1f, 1f, 0.25f)
                    RenderUtils.drawRoundedRect(
                        barX + max(0f, min(shimmerX, fillWidth)),
                        barY,
                        min(barX + fillWidth, barX + shimmerX + shimmerW),
                        barY + barHeight,
                        Color.WHITE.rgb, radius, RenderUtils.RoundedCorners.ALL
                    )
                    GL11.glColor4f(1f, 1f, 1f, 1f)
                }
            }
            "主题" -> {
                val themeColor = getBarColor(style, baseColor, gradientColor2)
                RenderUtils.drawRoundedRect(barX, barY, barX + barWidth, barY + barHeight, bgColor.rgb, radius, RenderUtils.RoundedCorners.ALL)
                if (fillWidth > 0) {
                    RenderUtils.drawRoundedRect(barX, barY, barX + fillWidth, barY + barHeight, themeColor.rgb, radius, RenderUtils.RoundedCorners.ALL)
                }
            }
            "iOS" -> {
                // iOS风格的胶囊进度条，带微妙阴影
                val pillRadius = barHeight / 2f
                // 阴影层
                net.ccbluex.liquidbounce.utils.GlowUtils.drawGlow(barX, barY - 1f, barX + barWidth, barY + barHeight + 1f, 6, Color(0, 0, 0, 40))
                RenderUtils.drawRoundedRect(barX, barY, barX + barWidth, barY + barHeight, bgColor.rgb, pillRadius, RenderUtils.RoundedCorners.ALL)
                if (fillWidth > 0) {
                    val iosColor = baseColor
                    RenderUtils.drawRoundedRect(barX, barY, barX + fillWidth, barY + barHeight, iosColor.rgb, pillRadius, RenderUtils.RoundedCorners.ALL)
                    // 白色高光条
                    GL11.glColor4f(1f, 1f, 1f, 0.15f)
                    RenderUtils.drawRoundedRect(barX, barY, barX + fillWidth, barY + barHeight * 0.45f, Color.WHITE.rgb, pillRadius, RenderUtils.RoundedCorners.ALL)
                    GL11.glColor4f(1f, 1f, 1f, 1f)
                }
            }
            "霓虹" -> {
                val neonColor = getBarColor(style, baseColor, gradientColor2)
                // 外发光
                net.ccbluex.liquidbounce.utils.GlowUtils.drawGlow(barX - 2, barY - 2, barX + barWidth + 2, barY + barHeight + 2, 10, neonColor)
                RenderUtils.drawRoundedRect(barX, barY, barX + barWidth, barY + barHeight, Color(0, 0, 0, 180).rgb, radius, RenderUtils.RoundedCorners.ALL)
                if (fillWidth > 0) {
                    // 霓虹边框
                    RenderUtils.drawRoundedBorder(barX, barY, barX + fillWidth, barY + barHeight, 1.5f, neonColor.rgb, radius)
                    // 霓虹填充 (半透明)
                    val innerRadius = max(0f, radius - 1)
                    RenderUtils.drawRoundedRect(barX + 1, barY + 1, barX + fillWidth - 1, barY + barHeight - 1,
                        Color(neonColor.red, neonColor.green, neonColor.blue, 60).rgb, innerRadius, RenderUtils.RoundedCorners.ALL)
                }
            }
            "原版" -> {
                // 原版由单独方法处理
            }
        }

        if (showText && style != "原版") {
            GL11.glEnable(GL11.GL_TEXTURE_2D)
            val textColor = when (style) {
                "霓虹" -> getBarColor(style, baseColor, gradientColor2)
                "iOS" -> Color.WHITE
                else -> Color.WHITE
            }
            val textX = if (isRightAligned) {
                barX + barWidth / 2 - Fonts.fontRegular35.getStringWidth(textContent) / 2
            } else {
                barX + barWidth / 2 - Fonts.fontRegular35.getStringWidth(textContent) / 2
            }
            val textY = barY + barHeight / 2 - Fonts.fontRegular35.FONT_HEIGHT / 2
            Fonts.fontRegular35.drawString(textContent, textX, textY, textColor.rgb, textShadow)
        }

        GL11.glEnable(GL11.GL_TEXTURE_2D)
        GL11.glShadeModel(GL11.GL_FLAT)
        GL11.glPopMatrix()
    }

    private fun renderVanillaHealthBar(barX: Float, barY: Float, health: Float, maxHealth: Float) {
        GL11.glPushMatrix()
        GL11.glEnable(GL11.GL_BLEND)
        GL11.glEnable(GL11.GL_TEXTURE_2D)
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA)
        mc.textureManager.bindTexture(ICONS)
        GL11.glColor4f(1f, 1f, 1f, 1f)
        val maxHearts = ceil(maxHealth / 2f).toInt()
        val heartSize = 9
        val spacing = 2

        for (i in 0 until maxHearts) {
            val heartX = barX + i * (heartSize + spacing)
            val healthForHeart = health - i * 2
            Gui.drawModalRectWithCustomSizedTexture(heartX.toInt(), barY.toInt(), 16f, 0f, 9, 9, 256f, 256f)
            if (healthForHeart >= 2) {
                Gui.drawModalRectWithCustomSizedTexture(heartX.toInt(), barY.toInt(), 52f, 0f, 9, 9, 256f, 256f)
            } else if (healthForHeart >= 1) {
                Gui.drawModalRectWithCustomSizedTexture(heartX.toInt(), barY.toInt(), 61f, 0f, 9, 9, 256f, 256f)
            }
        }
        GL11.glDisable(GL11.GL_TEXTURE_2D)
        GL11.glPopMatrix()
    }

    private fun renderVanillaFoodBar(barX: Float, barY: Float, foodLevel: Float) {
        GL11.glPushMatrix()
        GL11.glEnable(GL11.GL_BLEND)
        GL11.glEnable(GL11.GL_TEXTURE_2D)
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA)
        mc.textureManager.bindTexture(ICONS)
        GL11.glColor4f(1f, 1f, 1f, 1f)
        val iconCount = 10
        val iconSize = 9
        val spacing = 2

        for (i in 0 until iconCount) {
            val iconX = barX + i * (iconSize + spacing)
            val foodForIcon = foodLevel - i * 2
            Gui.drawModalRectWithCustomSizedTexture(iconX.toInt(), barY.toInt(), 16f, 27f, 9, 9, 256f, 256f)
            if (foodForIcon >= 2) {
                Gui.drawModalRectWithCustomSizedTexture(iconX.toInt(), barY.toInt(), 52f, 27f, 9, 9, 256f, 256f)
            } else if (foodForIcon >= 1) {
                Gui.drawModalRectWithCustomSizedTexture(iconX.toInt(), barY.toInt(), 61f, 27f, 9, 9, 256f, 256f)
            }
        }
        GL11.glDisable(GL11.GL_TEXTURE_2D)
        GL11.glPopMatrix()
    }

    private fun renderCustomHealthBar(player: net.minecraft.entity.player.EntityPlayer, width: Int, height: Int) {
        val health = player.health
        val maxHealth = player.maxHealth

        if (easingHealth < 0 || easingHealth > maxHealth || abs(easingHealth - health) > maxHealth * 0.5f) {
            easingHealth = health
        }
        easingHealth = lerp(easingHealth, health, healthAnimSpeed)
        displayHealth = easingHealth
        lastHealth = health

        val healthPercent = max(0f, min(1f, displayHealth / maxHealth))
        val barX = width / 2f - 91f + healthBarOffsetX.toFloat()
        val barY = height - 39f + healthBarOffsetY.toFloat()
        val barWidth = healthBarWidth.toFloat()
        val barHeight = healthBarHeight.toFloat()

        if (healthStyle == "原版") {
            renderVanillaHealthBar(barX, barY, health, maxHealth)
            return
        }

        val barColor = getBarColor(healthStyle, healthColor, healthGradientColor2)
        val gradientColor = if (healthStyle == "渐变圆角") healthGradientColor2 else healthColor
        renderBar(
            barX, barY, barWidth, barHeight, healthPercent,
            healthStyle, barColor, gradientColor, healthBgColor,
            healthRoundedRadius, healthText, healthTextShadow,
            "${health.toInt()}/${maxHealth.toInt()}", false
        )
    }

    private fun renderCustomFoodBar(player: net.minecraft.entity.player.EntityPlayer, width: Int, height: Int) {
        val foodLevel = player.foodStats.foodLevel
        val maxFood = 20f

        if (easingFood < 0 || easingFood > maxFood || abs(easingFood - foodLevel) > maxFood * 0.5f) {
            easingFood = foodLevel.toFloat()
        }
        easingFood = lerp(easingFood, foodLevel.toFloat(), foodAnimSpeed)
        displayFood = easingFood
        lastFood = foodLevel

        val foodPercent = max(0f, min(1f, displayFood / maxFood))
        val barX = width / 2f + 91f - foodBarWidth + foodBarOffsetX.toFloat()
        val barY = height - 39f + foodBarOffsetY.toFloat()
        val barWidth = foodBarWidth.toFloat()
        val barHeight = foodBarHeight.toFloat()

        if (foodStyle == "原版") {
            renderVanillaFoodBar(barX, barY, displayFood)
            return
        }

        val barColor = getBarColor(foodStyle, foodColor, foodGradientColor2)
        val gradientColor = if (foodStyle == "渐变圆角") foodGradientColor2 else foodColor
        renderBar(
            barX, barY, barWidth, barHeight, foodPercent,
            foodStyle, barColor, gradientColor, foodBgColor,
            foodRoundedRadius, foodText, foodTextShadow,
            "${displayFood.toInt()}/${maxFood.toInt()}", true
        )
    }

    val onRender2D = handler<Render2DEvent> {
        if (mc.currentScreen is GuiHudDesigner)
            return@handler

        hud.render(false)
    }

    val onUpdate = handler<UpdateEvent> {
        hud.update()
    }

    val onKey = handler<KeyEvent> { event ->
        hud.handleKey('a', event.key)
    }

    val onScreen = handler<ScreenEvent>(always = true) { event ->
        if (mc.theWorld == null || mc.thePlayer == null) return@handler
        if (state && blur && !mc.entityRenderer.isShaderActive && event.guiScreen != null &&
            !(event.guiScreen is GuiChat || event.guiScreen is GuiHudDesigner)
        ) mc.entityRenderer.loadShader(
            ResourceLocation(CLIENT_NAME.lowercase() + "/blur.json")
        ) else if (mc.entityRenderer.shaderGroup != null &&
            "airclient/blur.json" in mc.entityRenderer.shaderGroup.shaderGroupName
        ) mc.entityRenderer.stopUseShader()
    }

    fun shouldModifyChatFont() = handleEvents() && fontChat != "Off"

    fun getChatFont() = when (fontChat) {
        "Minecraft" -> Fonts.minecraftFont
        "Regular35" -> Fonts.fontRegular35
        "Regular40" -> Fonts.fontRegular40
        "Semibold35" -> Fonts.fontSemibold35
        "Semibold40" -> Fonts.fontSemibold40
        else -> Fonts.fontSemibold40
    }

    fun getAnimPos(pos: Float): Float {
        hotBarX = AnimationUtils.animate(pos, hotBarX, 0.02F * RenderUtils.deltaTime.toFloat())

        return hotBarX
    }
}
