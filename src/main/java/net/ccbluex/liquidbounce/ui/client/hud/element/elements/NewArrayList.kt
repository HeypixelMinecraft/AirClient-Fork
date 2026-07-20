// skid Opai
/*
 * Air Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 */
package net.ccbluex.liquidbounce.ui.client.hud.element.elements

import net.ccbluex.liquidbounce.LiquidBounce.moduleManager
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.ui.client.hud.designer.GuiHudDesigner
import net.ccbluex.liquidbounce.ui.client.hud.element.Border
import net.ccbluex.liquidbounce.ui.client.hud.element.Element
import net.ccbluex.liquidbounce.ui.client.hud.element.ElementInfo
import net.ccbluex.liquidbounce.ui.client.hud.element.Side
import net.ccbluex.liquidbounce.ui.client.hud.element.Side.Horizontal
import net.ccbluex.liquidbounce.ui.client.hud.element.Side.Vertical
import net.ccbluex.liquidbounce.ui.font.AWTFontRenderer.Companion.assumeNonVolatile
import net.ccbluex.liquidbounce.ui.font.Fonts
import net.ccbluex.liquidbounce.utils.render.BlurUtils
import net.ccbluex.liquidbounce.utils.render.RenderUtils.RoundedCorners
import net.ccbluex.liquidbounce.utils.render.RenderUtils.drawRoundedRect
import net.ccbluex.liquidbounce.utils.render.RenderUtils.drawImage
import net.ccbluex.liquidbounce.utils.render.animation.AnimationUtil
import net.ccbluex.liquidbounce.utils.render.ColorUtils
import net.ccbluex.liquidbounce.utils.client.ClientThemesUtils
import net.ccbluex.liquidbounce.features.module.modules.render.getMixedColor
import net.minecraft.util.ResourceLocation
import org.lwjgl.opengl.GL11
import java.awt.Color

@ElementInfo(name = "NewArrayList", single = true)
class NewArrayList(
    x: Double = 0.0, y: Double = 0.0, scale: Float = 1F,
    side: Side = Side(Horizontal.RIGHT, Vertical.UP),
) : Element("NewArrayList", x, y, scale, side) {
    private val displayScale by float("DisplayScale", 1f, 0.5f..3f)
    private val textShadow by boolean("TextShadow", true)
    private val shadowOffset by float("ShadowOffset", 1f, 0f..5f) { textShadow }
    private val textColorMode by choices("TextColorMode", arrayOf("Custom", "Rainbow", "Theme", "Sky", "Mixer"), "Custom")
    private val textCustomColor by color("TextColor", Color.WHITE) { textColorMode == "Custom" }
    private val textRainbowOffset by float("TextRainbowOffset", 0F, -2000F..2000F) { textColorMode == "Rainbow" }
    private val textSkySaturation by float("TextSkySaturation", 0.9f, 0f..1f) { textColorMode == "Sky" }
    private val textSkyBrightness by float("TextSkyBrightness", 1f, 0f..1f) { textColorMode == "Sky" }
    private val textSkySpeed by float("TextSkySpeed", 1f, 0.1f..5f) { textColorMode == "Sky" }
    private val textMixerSeconds by int("TextMixerSeconds", 2, 1..10) { textColorMode == "Mixer" }
    private val backgroundAlpha by int("BackgroundAlpha", 120, 0..255)
    private val bgColorMode by choices("BgColorMode", arrayOf("Custom", "Rainbow", "Theme", "Sky", "Mixer"), "Custom")
    private val bgCustomColor by color("BgColor", Color(0, 0, 0)) { bgColorMode == "Custom" }
    private val bgRainbowOffset by float("BgRainbowOffset", 0F, -2000F..2000F) { bgColorMode == "Rainbow" }
    private val bgSkySaturation by float("BgSkySaturation", 0.9f, 0f..1f) { bgColorMode == "Sky" }
    private val bgSkyBrightness by float("BgSkyBrightness", 1f, 0f..1f) { bgColorMode == "Sky" }
    private val bgSkySpeed by float("BgSkySpeed", 1f, 0.1f..5f) { bgColorMode == "Sky" }
    private val bgMixerSeconds by int("BgMixerSeconds", 2, 1..10) { bgColorMode == "Mixer" }
    private val stripeColorMode by choices("StripeColorMode", arrayOf("Custom", "Rainbow", "Theme", "Sky", "Mixer"), "Custom")
    private val stripeCustomColor by color("StripeColor", Color(65, 130, 225)) { stripeColorMode == "Custom" }
    private val stripeRainbowOffset by float("StripeRainbowOffset", 0F, -2000F..2000F) { stripeColorMode == "Rainbow" }
    private val stripeSkySaturation by float("StripeSkySaturation", 0.9f, 0f..1f) { stripeColorMode == "Sky" }
    private val stripeSkyBrightness by float("StripeSkyBrightness", 1f, 0f..1f) { stripeColorMode == "Sky" }
    private val stripeSkySpeed by float("StripeSkySpeed", 1f, 0.1f..5f) { stripeColorMode == "Sky" }
    private val stripeMixerSeconds by int("StripeMixerSeconds", 2, 1..10) { stripeColorMode == "Mixer" }
    private val leftCornersEnabled by boolean("LeftCornersEnabled", false)
    private val cornerRadius by float("CornerRadius", 5f, 0f..12f)
    private val showToggleIcon by boolean("ShowToggleIcon", true)
    private val toggleIconSize by float("ToggleIconSize", 12f, 6f..28f)
    private val toggleIconSpacing by float("ToggleIconSpacing", 4f, 0f..16f)
    private val toggleIconOffsetX by float("ToggleIconOffsetX", 0f, -10f..10f)
    private val toggleIconOffsetY by float("ToggleIconOffsetY", 0f, -10f..10f)
    private val iconBgAlpha by int("IconBgAlpha", 80, 0..255)
    private val iconBgColorMode by choices("IconBgColor", arrayOf("Theme", "Custom", "SameAsMain"), "Theme")
    private val iconBgCustomColor by color("IconBgCustom", Color(0, 0, 0)) { iconBgColorMode == "Custom" }
    private val iconCornerRadius by float("IconCornerRadius", 4f, 0f..12f)
    private val iconBlur by boolean("IconBlur", true)
    private val iconBlurStrength by float("IconBlurStrength", 12f, 3f..30f) { iconBlur }
    private val iconShadow by boolean("IconShadow", true)
    private val iconShadowRadius by float("IconShadowRadius", 6f, 1f..20f) { iconShadow }
    private val iconShadowColor by color("IconShadowColor", Color(0, 0, 0, 80)) { iconShadow }
    private val iconPadding by float("IconPadding", 2f, 0f..8f)
    private val shadowCheck by boolean("Shadow", false)
    private val shadowRadiusValue by float("ShadowRadius", 15F, 1F..50F) { shadowCheck }
    private val shadowColor by color("ShadowColor", Color(0, 0, 0, 120)) { shadowCheck }
    private val blur by boolean("Blur", true)
    private val blurRadius by float("BlurStrength", 15f, 5f..30f)
    private val stripeW by float("StripeWidth", 3f, 1f..8f)
    private val hideRender by boolean("HideRender", true)
    private val showTags by boolean("ShowTags", true)
    private val tagColorMode by choices("TagColorMode", arrayOf("FollowText", "White", "Gray", "Custom", "Theme"), "FollowText") { showTags }
    private val tagCustomColor by color("TagColor", Color(128, 128, 128)) { showTags && tagColorMode == "Custom" }
    private val font by font("Font", Fonts.fontSF35)
    private val textHeight by float("TextHeight", 11F, 1F..200F)
    private val textY by float("TextY", 0F, -50F..50F)
    private val space by float("Space", 1F, 0F..5F)
    private val itemGap by float("ItemGap", 0F, 0F..10F)
    private val animationSpeed by float("AnimationSpeed", 0.2F, 0.01F..1F)

    private val toggleIconResource = ResourceLocation("airclient/toggleon.png")
    private var modules = emptyList<Module>()

    private fun getBgColor(index: Int): Color {
        val baseColor = when (bgColorMode) {
            "Custom" -> bgCustomColor
            "Rainbow" -> ColorUtils.rainbow(System.currentTimeMillis() % 10000 + index * 100L + (bgRainbowOffset * 1000).toLong())
            "Theme" -> ClientThemesUtils.getColor(index)
            "Sky" -> ColorUtils.skyRainbow(index, bgSkySaturation, bgSkyBrightness, bgSkySpeed)
            "Mixer" -> getMixedColor(index, bgMixerSeconds)
            else -> bgCustomColor
        }
        return Color(baseColor.red, baseColor.green, baseColor.blue, backgroundAlpha)
    }

    private fun getTextColor(index: Int): Color = when (textColorMode) {
        "Custom" -> textCustomColor
        "Rainbow" -> ColorUtils.rainbow(System.currentTimeMillis() % 10000 + index * 100L + (textRainbowOffset * 1000).toLong())
        "Theme" -> ClientThemesUtils.getColor(index)
        "Sky" -> ColorUtils.skyRainbow(index, textSkySaturation, textSkyBrightness, textSkySpeed)
        "Mixer" -> getMixedColor(index, textMixerSeconds)
        else -> textCustomColor
    }

    private fun getStripeColor(index: Int): Color = when (stripeColorMode) {
        "Custom" -> stripeCustomColor
        "Rainbow" -> ColorUtils.rainbow(System.currentTimeMillis() % 10000 + index * 100L + (stripeRainbowOffset * 1000).toLong())
        "Theme" -> ClientThemesUtils.getColor(index)
        "Sky" -> ColorUtils.skyRainbow(index, stripeSkySaturation, stripeSkyBrightness, stripeSkySpeed)
        "Mixer" -> getMixedColor(index, stripeMixerSeconds)
        else -> stripeCustomColor
    }

    private fun getTagColor(itemColor: Color, index: Int): Color = when (tagColorMode) {
        "FollowText" -> Color(itemColor.red, itemColor.green, itemColor.blue, 128)
        "White" -> Color.WHITE
        "Gray" -> Color(128, 128, 128)
        "Custom" -> tagCustomColor
        "Theme" -> ClientThemesUtils.getColor(index).let { Color(it.red, it.green, it.blue, 128) }
        else -> Color(128, 128, 128)
    }

    private fun getShadowColor(textColor: Color): Color {
        return Color(textColor.red / 4, textColor.green / 4, textColor.blue / 4, textColor.alpha)
    }

    private fun getIconBgColor(): Color {
        return when (iconBgColorMode) {
            "Custom" -> Color(iconBgCustomColor.red, iconBgCustomColor.green, iconBgCustomColor.blue, iconBgAlpha)
            "SameAsMain" -> Color(0, 0, 0, iconBgAlpha)
            else -> {
                val theme = ClientThemesUtils.getColor()
                Color(theme.red, theme.green, theme.blue, iconBgAlpha)
            }
        }
    }

    private data class ItemInfo(
        val mod: Module, val index: Int,
        val bgLeft: Float, val bgTop: Float, val bgRight: Float, val bgBottom: Float,
        val stripeX: Float, val textX: Float, val textY: Float,
        val corners: RoundedCorners,
        val isFirst: Boolean, val isLast: Boolean
    )

    override fun drawElement(): Border? {
        assumeNonVolatile {
            val activeModules = moduleManager.filter {
                it.state && !it.isHidden && (it.category != Category.RENDER || !hideRender)
            }

            if (activeModules.isEmpty()) {
                if (mc.currentScreen is GuiHudDesigner) {
                    val s = 20f * displayScale
                    return if (side.horizontal == Horizontal.LEFT) Border(0F, -1F, s, s)
                    else Border(0F, -1F, -s, s)
                }
                return null
            }

            val sorted = activeModules.sortedByDescending { mod ->
                val nameW = font.getStringWidth(mod.getName(false)).toFloat()
                val tagW = if (showTags && mod.tag != null) font.getStringWidth(" ${mod.tag}").toFloat() else 0f
                nameW + tagW
            }

            val bgPadding = 6f
            val elemH = textHeight
            val textSpacer = textHeight + space + itemGap
            val totalItems = sorted.size

            val corners = if (leftCornersEnabled) RoundedCorners.ALL else RoundedCorners.NONE

            val items = sorted.mapIndexed { idx, mod ->
                val nameW = font.getStringWidth(mod.getName(false)).toFloat()
                val tagW = if (showTags && mod.tag != null) font.getStringWidth(" ${mod.tag}").toFloat() else 0f
                val textW = nameW + tagW

                val yPos = (if (side.vertical == Vertical.DOWN) -textSpacer else textSpacer) *
                        if (side.vertical == Vertical.DOWN) idx + 1 else idx
                val animY = AnimationUtil.base(mod.yAnim.toDouble(), yPos.toDouble(), animationSpeed.toDouble()).toFloat()
                mod.yAnim = animY
                val elemY = animY
                val elemX = 0f - stripeW
                val bgLeft = elemX - textW - bgPadding * 2f
                val bgRight = elemX + stripeW
                val isFirst = idx == 0
                val isLast = idx == totalItems - 1
                val textVertCenter = elemY + (elemH - font.FONT_HEIGHT) / 2f + textY
                ItemInfo(mod, idx, bgLeft, elemY, bgRight, elemY + elemH,
                    elemX, bgLeft + bgPadding, textVertCenter, corners, isFirst, isLast)
            }

            val listLeft = items.minOf { it.bgLeft }
            val listTop = items.first().bgTop
            val listRight = items.first().bgRight
            val listBottom = items.last().bgBottom

            GL11.glPushMatrix()
            GL11.glScalef(displayScale, displayScale, 1f)
            GL11.glPushAttrib(GL11.GL_STENCIL_BUFFER_BIT or GL11.GL_COLOR_BUFFER_BIT or GL11.GL_ENABLE_BIT)
            GL11.glEnable(GL11.GL_STENCIL_TEST)
            GL11.glClearStencil(0)
            GL11.glClear(GL11.GL_STENCIL_BUFFER_BIT)
            GL11.glColorMask(false, false, false, false)
            GL11.glStencilFunc(GL11.GL_ALWAYS, 1, 0xFF)
            GL11.glStencilOp(GL11.GL_REPLACE, GL11.GL_REPLACE, GL11.GL_REPLACE)

            for (item in items) {
                drawRoundedRect(
                    item.bgLeft, item.bgTop, item.bgRight, item.bgBottom,
                    Color.WHITE.rgb, cornerRadius, item.corners
                )
                if (showToggleIcon) {
                    val iconSize = toggleIconSize
                    val spacing = toggleIconSpacing
                    val offsetX = toggleIconOffsetX
                    val offsetY = toggleIconOffsetY
                    val cardSize = iconSize + iconPadding * 2
                    var currentX = item.textX + font.getStringWidth(item.mod.getName(false)).toFloat()
                    if (showTags && item.mod.tag != null) {
                        currentX += font.getStringWidth(" ${item.mod.tag}").toFloat()
                    }

                    val cardX = currentX + spacing + offsetX
                    val cardY = item.textY + font.FONT_HEIGHT / 2f - cardSize / 2f + offsetY

                    drawRoundedRect(
                        cardX, cardY, cardX + cardSize, cardY + cardSize,
                        Color.WHITE.rgb, iconCornerRadius, RoundedCorners.ALL
                    )
                }
            }
            GL11.glColorMask(true, true, true, true)
            if (shadowCheck) {
                GL11.glStencilFunc(GL11.GL_NOTEQUAL, 1, 0xFF)
                GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP)
                val maxDist = shadowRadiusValue.toInt()
                for (i in maxDist downTo 1) {
                    val alpha = (shadowColor.alpha * (1f - i.toFloat() / (maxDist + 1)))
                        .toInt().coerceIn(1, shadowColor.alpha)
                    val color = Color(shadowColor.red, shadowColor.green, shadowColor.blue, alpha).rgb
                    for (item in items) {
                        drawRoundedRect(
                            item.bgLeft - i, item.bgTop - i, item.bgRight + i, item.bgBottom + i,
                            color, cornerRadius + i, item.corners
                        )
                    }
                }
            }
            GL11.glStencilFunc(GL11.GL_EQUAL, 1, 0xFF)
            GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP)
            if (blur) {
                GL11.glPushMatrix()
                GL11.glTranslated(-renderX, -renderY, 0.0)
                GL11.glScalef(1F / (scale * displayScale), 1F / (scale * displayScale), 1F)
                for (item in items) {
                    BlurUtils.blurAreaRounded(
                        renderX.toFloat() + item.bgLeft,
                        renderY.toFloat() + item.bgTop,
                        renderX.toFloat() + item.bgRight,
                        renderY.toFloat() + item.bgBottom,
                        cornerRadius, blurRadius
                    )
                }
                GL11.glPopMatrix()
            }
            for (item in items) {
                drawRoundedRect(
                    item.bgLeft, item.bgTop, item.bgRight, item.bgBottom,
                    getBgColor(item.index).rgb, cornerRadius, item.corners
                )
            }
            for (item in items) {
                val stripeCol = getStripeColor(item.index)
                val textCol = getTextColor(item.index)
                drawRoundedRect(
                    item.stripeX, item.bgTop, item.stripeX + stripeW, item.bgBottom,
                    stripeCol.rgb, 0f, RoundedCorners.NONE
                )
                val moduleName = item.mod.getName(false)
                var textX = item.textX
                val textY = item.textY

                if (textShadow) {
                    val shadowCol = getShadowColor(textCol)
                    font.drawString(moduleName, textX + shadowOffset, textY + shadowOffset, shadowCol.rgb, false)
                }
                font.drawString(moduleName, textX, textY, textCol.rgb, false)

                val nameWidth = font.getStringWidth(moduleName).toFloat()
                var currentX = textX + nameWidth
                if (showTags && item.mod.tag != null) {
                    val tagText = " ${item.mod.tag}"
                    val tagCol = getTagColor(textCol, item.index)
                    if (textShadow) {
                        val shadowTagCol = getShadowColor(tagCol)
                        font.drawString(tagText, currentX + shadowOffset, textY + shadowOffset, shadowTagCol.rgb, false)
                    }
                    font.drawString(tagText, currentX, textY, tagCol.rgb, false)
                    currentX += font.getStringWidth(tagText).toFloat()
                }
                if (showToggleIcon) {
                    val iconSize = toggleIconSize
                    val spacing = toggleIconSpacing
                    val offsetX = toggleIconOffsetX
                    val offsetY = toggleIconOffsetY

                    val cardSize = iconSize + iconPadding * 2
                    val cardX = currentX + spacing + offsetX
                    val cardY = item.textY + font.FONT_HEIGHT / 2f - cardSize / 2f + offsetY
                    GL11.glStencilFunc(GL11.GL_ALWAYS, 0, 0xFF)
                    GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP)
                    if (iconShadow) {
                        val shadowR = iconCornerRadius
                        val maxDist = iconShadowRadius.toInt()
                        val shadowBase = iconShadowColor
                        for (i in maxDist downTo 1) {
                            val alpha = (shadowBase.alpha * (1f - i.toFloat() / (maxDist + 1)))
                                .toInt().coerceIn(1, shadowBase.alpha)
                            drawRoundedRect(
                                cardX - i, cardY - i, cardX + cardSize + i, cardY + cardSize + i,
                                Color(shadowBase.red, shadowBase.green, shadowBase.blue, alpha).rgb,
                                shadowR + i, RoundedCorners.ALL
                            )
                        }
                    }

                    if (iconBlur) {
                        GL11.glPushMatrix()
                        GL11.glTranslated(-renderX, -renderY, 0.0)
                        GL11.glScalef(1F / (scale * displayScale), 1F / (scale * displayScale), 1F)
                        BlurUtils.blurAreaRounded(
                            renderX.toFloat() + cardX,
                            renderY.toFloat() + cardY,
                            renderX.toFloat() + cardX + cardSize,
                            renderY.toFloat() + cardY + cardSize,
                            iconCornerRadius, iconBlurStrength
                        )
                        GL11.glPopMatrix()
                    }

                    GL11.glStencilFunc(GL11.GL_EQUAL, 1, 0xFF)
                    GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP)
                    drawRoundedRect(
                        cardX, cardY, cardX + cardSize, cardY + cardSize,
                        getIconBgColor().rgb, iconCornerRadius, RoundedCorners.ALL
                    )
                    drawImage(
                        toggleIconResource,
                        (cardX + iconPadding).toInt(),
                        (cardY + iconPadding).toInt(),
                        iconSize.toInt(), iconSize.toInt(),
                        Color.WHITE
                    )
                }
            }

            GL11.glPopAttrib()
            GL11.glDisable(GL11.GL_STENCIL_TEST)

            GL11.glPopMatrix()

            if (mc.currentScreen is GuiHudDesigner) {
                val x2 = items.minOf { it.bgLeft }.toInt()
                val y2 = (if (side.vertical == Vertical.DOWN) -textSpacer else textSpacer) * items.size
                return Border(0F, 0F, (x2 - 7F) * displayScale, (y2 - if (side.vertical == Vertical.DOWN) 1F else 0F) * displayScale)
            }
        }
        return null
    }

    override fun updateElement() {
        modules = moduleManager.filter {
            it.state && !it.isHidden && (it.category != Category.RENDER || !hideRender)
        }.sortedByDescending { mod ->
            val nameW = font.getStringWidth(mod.getName(false)).toFloat()
            val tagW = if (showTags && mod.tag != null) font.getStringWidth(" ${mod.tag}").toFloat() else 0f
            nameW + tagW
        }
    }
}
