// skid LiquidBounce
package net.ccbluex.liquidbounce.features.module.modules.movement

import net.ccbluex.liquidbounce.event.UpdateEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.features.module.modules.combat.KillAura
import net.ccbluex.liquidbounce.utils.extensions.isMoving
import net.ccbluex.liquidbounce.utils.rotation.RotationUtils
import net.minecraft.client.settings.GameSettings
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.item.EntityArmorStand
import net.minecraft.entity.item.EntityBoat
import net.minecraft.entity.item.EntityMinecart
import net.minecraft.entity.projectile.EntityFishHook
import net.minecraft.util.AxisAlignedBB
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

object EntitySpeed : Module("EntitySpeed", Category.MOVEMENT) {

    private val mode by choices("Mode", arrayOf("EntityCollide", "Grim"), "EntityCollide")
    private val speed by int("Speed", 5, 1..8) { mode == "EntityCollide" || mode == "Grim" }

    override val tag: String
        get() = mode

    fun getMoveYaw(yaw: Double): Double {
        var rotationYaw = yaw

        if (mc.thePlayer.moveForward < 0.0f) {
            rotationYaw += 180.0
        }

        var forward = 1.0
        if (mc.thePlayer.moveForward < 0.0f) {
            forward = -0.5
        } else if (mc.thePlayer.moveForward > 0.0f) {
            forward = 0.5
        }

        if (mc.thePlayer.moveStrafing > 0.0f) {
            rotationYaw -= 90.0 * forward
        }

        if (mc.thePlayer.moveStrafing < 0.0f) {
            rotationYaw += 90.0 * forward
        }

        return rotationYaw
    }

    val onUpdate = handler<UpdateEvent> {
        when (mode) {
            "EntityCollide" -> {
                if (mc.thePlayer.moveForward == 0.0f && mc.thePlayer.moveStrafing == 0.0f) {
                    return@handler
                }

                for (entity in mc.theWorld.loadedEntityList) {
                    var collidedCount = 0

                    if (entity is EntityArmorStand) {
                        continue
                    }

                    if (entity != mc.thePlayer &&
                        entity is EntityLivingBase &&
                        mc.thePlayer.entityBoundingBox.expand(1.0, 1.0, 1.0).intersectsWith(entity.entityBoundingBox)
                    ) {
                        ++collidedCount
                    }

                    val yawRadians = getMoveYaw(RotationUtils.serverRotation.yaw.toDouble()) * (Math.PI / 180.0)
                    val boost = speed * 0.01 * collidedCount.toDouble()
                    mc.thePlayer.addVelocity(-sin(yawRadians) * boost, 0.0, cos(yawRadians) * boost)
                }
            }

            "Grim" -> {
                val playerBox: AxisAlignedBB = mc.thePlayer.entityBoundingBox.expand(1.0, 1.0, 1.0)
                var collidedCount = 0

                for (entity: Entity in mc.theWorld.loadedEntityList) {
                    if ((entity !is EntityLivingBase && entity !is EntityBoat && entity !is EntityMinecart && entity !is EntityFishHook) ||
                        entity is EntityArmorStand ||
                        entity.entityId == mc.thePlayer.entityId ||
                        !playerBox.intersectsWith(entity.entityBoundingBox) ||
                        entity.entityId == -8 ||
                        entity.entityId == -1337
                    ) {
                        continue
                    }

                    ++collidedCount
                }

                if (collidedCount > 0 && mc.thePlayer.isMoving) {
                    val strafeOffset = min(collidedCount, 4) * speed * 0.01
                    val yaw = getMoveYaw(RotationUtils.serverRotation.yaw.toDouble())
                    val motionX = -sin(Math.toRadians(yaw))
                    val motionZ = cos(Math.toRadians(yaw))

                    mc.thePlayer.addVelocity(motionX * strafeOffset, 0.0, motionZ * strafeOffset)

                    if (collidedCount < 4 && KillAura.target != null && mc.gameSettings.keyBindJump.isKeyDown) {
                        mc.gameSettings.keyBindSprint.pressed = true
                        return@handler
                    }

                    mc.gameSettings.keyBindSprint.pressed = GameSettings.isKeyDown(mc.gameSettings.keyBindSprint)
                    return@handler
                }

                mc.gameSettings.keyBindSprint.pressed = GameSettings.isKeyDown(mc.gameSettings.keyBindSprint)
            }
        }
    }
}
