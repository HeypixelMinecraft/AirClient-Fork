package net.ccbluex.liquidbounce.ui.client.hud.element.elements.targets2.impl

import net.ccbluex.liquidbounce.ui.client.hud.element.Border
import net.ccbluex.liquidbounce.ui.client.hud.element.elements.Target2
import net.ccbluex.liquidbounce.ui.client.hud.element.elements.targets2.TargetStyle
import net.ccbluex.liquidbounce.ui.font.Fonts
import net.ccbluex.liquidbounce.utils.render.RenderUtils
import net.minecraft.entity.player.EntityPlayer
import java.awt.Color
import kotlin.math.pow

class Rice(inst: Target2) : TargetStyle("Rice", inst, true) {
    override fun drawTarget(entity: EntityPlayer) {
        updateAnim(entity.health)

        val font = Fonts.fontSF40
        val name = "Name: ${entity.name}"
        val info = "Distance: ${decimalFormat2.format(mc.thePlayer.getDistanceToEntity(entity))}"

        val length = (font.getStringWidth(name).coerceAtLeast(font.getStringWidth(info)).toFloat() + 40F).coerceAtLeast(125F)
        val maxHealthLength = font.getStringWidth(decimalFormat2.format(entity.maxHealth)).toFloat()

        RenderUtils.drawRoundedRect(0F, 0F, 10F + length, 55F, targetInstance.bgColor.rgb, 8F)

        val scaleHT = (entity.hurtTime.toFloat() / entity.maxHurtTime.coerceAtLeast(1).toFloat()).coerceIn(0F, 1F)
        val skinLocation = mc.netHandler.getPlayerInfo(entity.uniqueID)?.locationSkin
        if (skinLocation != null) {
            drawHead(skinLocation,
                5F + 15F * (scaleHT * 0.2F),
                5F + 15F * (scaleHT * 0.2F),
                1F - scaleHT * 0.2F,
                30, 30,
                1F, 0.4F + (1F - scaleHT) * 0.6F, 0.4F + (1F - scaleHT) * 0.6F,
                1F - targetInstance.getFadeProgress())
        }

        font.drawString(name, 39F, 11F, getColor(-1).rgb)
        font.drawString(info, 39F, 23F, getColor(-1).rgb)

        val barWidth = (length - 5F - maxHealthLength) * (easingHealth / entity.maxHealth).coerceIn(0F, 1F)
        RenderUtils.drawRect(5F, 42F, length - maxHealthLength, 48F, Color(50, 50, 50).rgb)
        RenderUtils.drawRect(5F, 42F, 5F + barWidth, 48F, targetInstance.barColor.rgb)

        val healthName = decimalFormat2.format(easingHealth)
        font.drawString(healthName, 10F + barWidth, 41F, getColor(-1).rgb)
    }

    override fun handleBlur(entity: EntityPlayer) {
        val font = Fonts.fontSF40
        val name = "Name: ${entity.name}"
        val info = "Distance: ${decimalFormat2.format(mc.thePlayer.getDistanceToEntity(entity))}"
        val length = (font.getStringWidth(name).coerceAtLeast(font.getStringWidth(info)).toFloat() + 40F).coerceAtLeast(125F)
        RenderUtils.quickDrawRect(0F, 0F, 10F + length, 55F)
    }

    override fun handleShadowCut(entity: EntityPlayer) = handleBlur(entity)

    override fun handleShadow(entity: EntityPlayer) {
        val font = Fonts.fontSF40
        val name = "Name: ${entity.name}"
        val info = "Distance: ${decimalFormat2.format(mc.thePlayer.getDistanceToEntity(entity))}"
        val length = (font.getStringWidth(name).coerceAtLeast(font.getStringWidth(info)).toFloat() + 40F).coerceAtLeast(125F)
        RenderUtils.quickDrawRect(0F, 0F, 10F + length, 55F, shadowOpaque.rgb)
    }

    override fun getBorder(entity: EntityPlayer?): Border {
        entity ?: return Border(0F, 0F, 135F, 55F)
        val font = Fonts.fontSF40
        val name = "Name: ${entity.name}"
        val info = "Distance: ${decimalFormat2.format(mc.thePlayer.getDistanceToEntity(entity))}"
        val length = (font.getStringWidth(name).coerceAtLeast(font.getStringWidth(info)).toFloat() + 40F).coerceAtLeast(125F)
        return Border(0F, 0F, 10F + length, 55F)
    }
}
