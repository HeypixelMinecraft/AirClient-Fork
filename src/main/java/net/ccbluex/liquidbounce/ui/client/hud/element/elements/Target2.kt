package net.ccbluex.liquidbounce.ui.client.hud.element.elements

import net.ccbluex.liquidbounce.LiquidBounce
import net.ccbluex.liquidbounce.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.KillAura
import net.ccbluex.liquidbounce.features.module.modules.render.getMixedColor
import net.ccbluex.liquidbounce.ui.client.hud.designer.GuiHudDesigner
import net.ccbluex.liquidbounce.ui.client.hud.element.Border
import net.ccbluex.liquidbounce.ui.client.hud.element.Element
import net.ccbluex.liquidbounce.ui.client.hud.element.ElementInfo
import net.ccbluex.liquidbounce.ui.client.hud.element.elements.targets2.TargetStyle
import net.ccbluex.liquidbounce.ui.client.hud.element.elements.targets2.impl.*
import net.ccbluex.liquidbounce.utils.render.*
import net.minecraft.client.gui.GuiChat
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.entity.player.EntityPlayer
import org.lwjgl.opengl.GL11
import java.awt.Color

@ElementInfo(name = "Target2")
class Target2 : Element("Target2") {

    private val styleList = mutableListOf<TargetStyle>()

    private val styleValue = ListValue("Style", arrayOf("Chill"), "Chill")

    val blurValue by boolean("Blur", false)
    val blurStrength by float("Blur-Strength", 1F, 0.01F..40F) { blurValue }

    val shadowValue by boolean("Shadow", false)
    val shadowStrength by float("Shadow-Strength", 1F, 0.01F..40F) { shadowValue }
    val shadowColorMode by choices("Shadow-Color", arrayOf("Background", "Custom", "Bar"), "Background") { shadowValue }

    val shadowColorRedValue by int("Shadow-Red", 0, 0..255) { shadowValue && shadowColorMode.equals("custom", ignoreCase = true) }
    val shadowColorGreenValue by int("Shadow-Green", 111, 0..255) { shadowValue && shadowColorMode.equals("custom", ignoreCase = true) }
    val shadowColorBlueValue by int("Shadow-Blue", 255, 0..255) { shadowValue && shadowColorMode.equals("custom", ignoreCase = true) }

    val fadeValue by boolean("FadeAnim", false)
    val fadeSpeed by float("Fade-Speed", 1F, 0F..5F) { fadeValue }
    val noAnimValue by boolean("No-Animation", false)
    val globalAnimSpeed by float("Global-AnimSpeed", 3F, 1F..6.30F) { !noAnimValue }

    val showWithChatOpen by boolean("Show-ChatOpen", true)

    val colorModeValue by choices("Color", arrayOf("Custom", "Rainbow", "Sky", "Slowly", "Fade", "Mixer", "Health"), "Custom")
    val redValue by int("Red", 252, 0..255)
    val greenValue by int("Green", 96, 0..255)
    val blueValue by int("Blue", 66, 0..255)
    val saturationValue by float("Saturation", 1F, 0F..1F)
    val brightnessValue by float("Brightness", 1F, 0F..1F)
    val waveSecondValue by int("Seconds", 2, 1..10)
    val bgRedValue by int("Background-Red", 0, 0..255)
    val bgGreenValue by int("Background-Green", 0, 0..255)
    val bgBlueValue by int("Background-Blue", 0, 0..255)
    val bgAlphaValue by int("Background-Alpha", 160, 0..255)

    val bordercolor: Color
        get() = Color(redValue, greenValue, blueValue)

    val styleValueName: String
        get() = styleValue.get()

    var barColor = Color(-1)
    var bgColor = Color(-1)
    var animProgress = 0F

    val counter1 = intArrayOf(50)
    val counter2 = intArrayOf(80)

    private var mainTarget: EntityPlayer? = null

    init {
        val styles = arrayOf(
            Astolfo(this),
            Astolfo2(this),
            AsuidBounce(this),
            Chill(this),
            Exhibition(this),
            Flux(this),
            Hanabi(this),
            LiquidBounce(this),
            Lnk(this),
            Moon(this),
            Moon4(this),
            Novoline(this),
            Novoline2(this),
            Novoline3(this),
            Raven(this),
            RavenB4(this),
            Remix(this),
            Rice(this),
            Slowly(this),
            Tifality(this)
        )
        styles.forEach { styleList.add(it) }
        styleValue.values = styleList.map { it.name }.toTypedArray()

        addValue(styleValue)
        styleList.forEach { addValues(it.values) }
    }

    fun getFadeProgress() = animProgress

    override fun drawElement(): Border? {
        val isEditing = mc.currentScreen is GuiHudDesigner
        
        val kaTarget = KillAura.target

        if (isEditing) {
            mainTarget = mc.thePlayer
        } else if (kaTarget != null && kaTarget is EntityPlayer) {
            mainTarget = kaTarget as EntityPlayer
        } else if (mc.currentScreen is GuiChat && showWithChatOpen) {
            mainTarget = mc.thePlayer
        } else {
            if (fadeValue) {
                animProgress += (0.0075F * fadeSpeed * RenderUtils.deltaTime)
                animProgress = animProgress.coerceAtMost(1F)
                if (animProgress >= 1F) {
                    mainTarget = null
                }
            } else {
                mainTarget = null
            }
        }

        if (mainTarget != null && fadeValue) {
            animProgress -= (0.0075F * fadeSpeed * RenderUtils.deltaTime)
            animProgress = animProgress.coerceAtLeast(0F)
        }

        val currentStyleName = styleValue.get()
        val mainStyle = styleList.find { it.name.equals(currentStyleName, ignoreCase = true) } ?: return Border(0F, 0F, 120F, 48F)

        if (mainTarget == null) {
            return Border(0F, 0F, 120F, 48F)
        }

        val convertTarget = mainTarget!!

        val preBarColor = when (colorModeValue) {
            "Rainbow" -> Color(ColorUtils.getRainbowOpaque(waveSecondValue, saturationValue, brightnessValue, 0))
            "Custom" -> Color(redValue, greenValue, blueValue)
            "Sky" -> ColorUtils.skyRainbow(0, saturationValue, brightnessValue, 1f)
            "Fade" -> ColorUtils.fade(Color(redValue, greenValue, blueValue), 0, 100)
            "Health" -> BlendUtils.getHealthColor(convertTarget.health, convertTarget.maxHealth)
            "Mixer" -> getMixedColor(0, waveSecondValue)
            else -> ColorUtils.LiquidSlowly(System.nanoTime(), 0, saturationValue, brightnessValue)
        }

        barColor = ColorUtils.reAlpha(preBarColor, preBarColor.alpha / 255F * (1F - animProgress))
        bgColor = Color(bgRedValue, bgGreenValue, bgBlueValue, (bgAlphaValue * (1F - animProgress)).toInt())

        val returnBorder = mainStyle.getBorder(convertTarget) ?: return Border(0F, 0F, 120F, 48F)

        GL11.glPushMatrix()
        
        try {
            if (shadowValue && mainStyle.shaderSupport) {
                GL11.glTranslated(-renderX, -renderY, 0.0)
                GL11.glPushMatrix()
                ShadowUtils.shadow(shadowStrength, {
                    GL11.glPushMatrix()
                    GL11.glTranslated(renderX, renderY, 0.0)
                    mainStyle.handleShadow(convertTarget)
                    GL11.glPopMatrix()
                }, {
                    GL11.glPushMatrix()
                    GL11.glTranslated(renderX, renderY, 0.0)
                    mainStyle.handleShadowCut(convertTarget)
                    GL11.glPopMatrix()
                })
                GL11.glPopMatrix()
                GL11.glTranslated(renderX, renderY, 0.0)
            }

            if (blurValue && mainStyle.shaderSupport) {
                val floatX = renderX.toFloat()
                val floatY = renderY.toFloat()
                GL11.glTranslated(-renderX, -renderY, 0.0)
                GL11.glPushMatrix()
                BlurUtils.blur(floatX + returnBorder.x, floatY + returnBorder.y, floatX + returnBorder.x2, floatY + returnBorder.y2, blurStrength * (1F - animProgress), false) {
                    GL11.glPushMatrix()
                    GL11.glTranslated(renderX, renderY, 0.0)
                    mainStyle.handleBlur(convertTarget)
                    GL11.glPopMatrix()
                }
                GL11.glPopMatrix()
                GL11.glTranslated(renderX, renderY, 0.0)
            }

            if (mainStyle is Chill) {
                val borderWidth = returnBorder.x2 - returnBorder.x
                val borderHeight = returnBorder.y2 - returnBorder.y
                val calcScaleX = animProgress * (4F / (borderWidth / 2F))
                val calcScaleY = animProgress * (4F / (borderHeight / 2F))
                val calcTranslateX = borderWidth / 2F * calcScaleX
                val calcTranslateY = borderHeight / 2F * calcScaleY
                mainStyle.updateData(renderX.toFloat() + calcTranslateX, renderY.toFloat() + calcTranslateY, calcScaleX, calcScaleY)
            }

            mainStyle.drawTarget(convertTarget)
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            GlStateManager.resetColor()
            GL11.glPopMatrix()
        }

        return returnBorder
    }
}
