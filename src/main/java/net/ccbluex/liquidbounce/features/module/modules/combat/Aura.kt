// skid LiquidBounce
package net.ccbluex.liquidbounce.features.module.modules.combat

import de.florianmichael.viamcp.ViaMCP
import de.florianmichael.viamcp.fixes.AttackOrder
import de.florianmichael.vialoadingbase.ViaLoadingBase
import net.ccbluex.liquidbounce.event.EventState
import net.ccbluex.liquidbounce.event.MotionEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.utils.attack.EntityUtils
import net.ccbluex.liquidbounce.utils.client.PacketUtils.sendPacket
import net.ccbluex.liquidbounce.utils.extras.sendOffHandUseItem
import net.ccbluex.liquidbounce.utils.extensions.getDistanceToEntityBox
import net.ccbluex.liquidbounce.utils.rotation.Rotation
import net.ccbluex.liquidbounce.utils.rotation.RotationSettings
import net.ccbluex.liquidbounce.utils.rotation.RotationUtils
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemSword
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement

object Aura : Module("Aura", Category.COMBAT) {

    val attackTiming by choices("AttackTiming", arrayOf("PRE", "POST"), "PRE")
    val attackRange by float("AttackRange", 3.0f, 3.0f..6.0f)
    val autoBlock by boolean("AutoBlock", true)
    val autoBlockSendC08 by boolean("AutoBlockSendC08PerTick", true) { autoBlock }

    val rotationSettings = RotationSettings(this)

    var target: Entity? = null
    var isBlocking = false

    override fun onDisable() {
        target = null
        isBlocking = false
        mc.gameSettings.keyBindUseItem.pressed = false
    }

    fun getRotations(entity: Entity): Rotation {
        val playerX = mc.thePlayer.posX
        val playerY = mc.thePlayer.posY + mc.thePlayer.eyeHeight
        val playerZ = mc.thePlayer.posZ
        val entityX = entity.posX
        val entityY = entity.posY + entity.height / 2.0f
        val entityZ = entity.posZ
        val diffX = playerX - entityX
        val diffY = playerY - entityY
        val diffZ = playerZ - entityZ
        val diffHorizontal = Math.sqrt(Math.pow(diffX, 2.0) + Math.pow(diffZ, 2.0))
        val yaw = Math.toDegrees(Math.atan2(diffZ, diffX)) + 90.0
        val pitch = Math.toDegrees(Math.atan2(diffHorizontal, diffY))
        return Rotation(yaw.toFloat(), (90.0 - pitch).toFloat())
    }

    val onMotion = handler<MotionEvent> { event ->
        if (mc.thePlayer == null) {
            return@handler
        }

        if (event.eventState == EventState.PRE && attackTiming != "PRE") {
            return@handler
        }

        if (event.eventState == EventState.POST && attackTiming != "POST") {
            return@handler
        }

        target = null
        isBlocking = false
        mc.gameSettings.keyBindUseItem.pressed = false

        for (entity in mc.theWorld.loadedEntityList) {
            if (entity !is EntityLivingBase || !EntityUtils.isSelected(entity, true)) {
                continue
            }

            if (mc.thePlayer.getDistanceToEntityBox(entity) <= attackRange.toDouble()) {
                target = entity
                break
            }
        }

        val currentTarget = target ?: return@handler

        if (autoBlock && mc.thePlayer.heldItem?.item is ItemSword) {
            mc.gameSettings.keyBindUseItem.pressed = true
            if (autoBlockSendC08) {
                if (ViaLoadingBase.getInstance().targetVersion.version > 47) {
                    sendOffHandUseItem.sendOffHandUseItem()
                } else {
                    sendPacket(C08PacketPlayerBlockPlacement(mc.thePlayer.heldItem), false)
                }
            }
        }

        isBlocking = true
        val rotation = getRotations(currentTarget)
        RotationUtils.setTargetRotation(rotation, rotationSettings)
        AttackOrder.sendFixedAttack(mc.thePlayer as EntityPlayer, currentTarget)
    }
}
