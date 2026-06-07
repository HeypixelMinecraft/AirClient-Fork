/*
 * Air Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 */
package net.ccbluex.liquidbounce.features.module.modules.`fun`

import net.ccbluex.liquidbounce.event.EventState
import net.ccbluex.liquidbounce.event.MotionEvent
import net.ccbluex.liquidbounce.event.MoveEvent
import net.ccbluex.liquidbounce.event.UpdateEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.features.module.modules.world.scaffolds.Scaffold
import net.ccbluex.liquidbounce.utils.client.ClientUtils
import net.ccbluex.liquidbounce.utils.rotation.RotationUtils
import net.ccbluex.liquidbounce.utils.timing.MSTimer
import net.minecraft.util.MathHelper
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.atan2
import kotlin.math.sqrt

object AutoFreezeFunny : Module("AutoFreezeFunny", Category.FUN, subjective = true) {

    private val chatSpam by boolean("ChatSpam", true)
    private val autoRotate by boolean("AutoRotate", true)
    private val scaffoldTicks by int("ScaffoldTicks", 24, 1..100)
    private val effectTicks by int("EffectTicks", 60, 1..100)

    private val chatTimer = MSTimer()
    private val colorCodes = charArrayOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f')

    private var funnyActive = false
    private var fadeTicks = 0
    private var forcedYaw: Float? = null
    private var scaffoldAirTicks = 0
    private var freezeLeftTicks = 0
    private var rotateLeftTicks = 0
    private var waitingForNextScaffoldRun = false

    override fun onEnable() {
        resetState()
    }

    override fun onDisable() {
        resetState()
    }

    val onMotion = handler<MotionEvent> { event ->
        if (event.eventState != EventState.PRE) {
            return@handler
        }

        val player = mc.thePlayer ?: return@handler

        if (!player.onGround && Scaffold.handleEvents()) {
            funnyActive = true
            fadeTicks = 6

            if (!waitingForNextScaffoldRun && freezeLeftTicks <= 0 && rotateLeftTicks <= 0) {
                scaffoldAirTicks++

                if (scaffoldAirTicks >= scaffoldTicks) {
                    freezeLeftTicks = effectTicks
                    rotateLeftTicks = effectTicks
                    waitingForNextScaffoldRun = true
                }
            }

            if (chatSpam && chatTimer.hasTimePassed(1250L)) {
                ClientUtils.displayChatMessage("§${randomColorCode()}注意：我没有卡在空中。")
                chatTimer.reset()
            }

            if (autoRotate && rotateLeftTicks > 0) {
                applySmoothBackwardsRotation()
            }
        } else if (autoRotate && funnyActive && fadeTicks > 0) {
            applySmoothBackwardsRotation(fade = true)
            fadeTicks--
        }
    }

    val onUpdate = handler<UpdateEvent> {
        val player = mc.thePlayer ?: return@handler
        val scaffolding = !player.onGround && Scaffold.handleEvents()

        if (freezeLeftTicks > 0) {
            player.motionX = 0.0
            player.motionZ = 0.0
            freezeLeftTicks--
        }

        if (rotateLeftTicks > 0) {
            rotateLeftTicks--
        }

        if (!scaffolding) {
            scaffoldAirTicks = 0
            waitingForNextScaffoldRun = false
        }

        if (player.onGround && funnyActive) {
            fadeTicks = (fadeTicks - 1).coerceAtLeast(0)

            if (fadeTicks == 0) {
                funnyActive = false
                forcedYaw = null
            }
        }
    }

    val onMove = handler<MoveEvent> { event ->
        if (freezeLeftTicks <= 0) {
            return@handler
        }

        event.zeroXZ()
    }

    private fun applySmoothBackwardsRotation(fade: Boolean = false) {
        val player = mc.thePlayer ?: return
        val backwardsYaw = calculateBackwardsYaw() ?: forcedYaw ?: RotationUtils.serverRotation.yaw
        val currentYaw = forcedYaw ?: player.rotationYaw
        val speed = if (fade) 6F else 14F
        val yawDiff = MathHelper.wrapAngleTo180_float(backwardsYaw - currentYaw).coerceIn(-speed, speed)
        val smoothedYaw = currentYaw + yawDiff

        forcedYaw = smoothedYaw
        player.rotationYaw = smoothedYaw
        player.rotationPitch = MathHelper.clamp_float(player.rotationPitch, -90F, 90F)
        RotationUtils.syncRotations()
    }

    private fun calculateBackwardsYaw(): Float? {
        val player = mc.thePlayer ?: return null
        val motionX = player.motionX
        val motionZ = player.motionZ
        val motionLength = sqrt(motionX * motionX + motionZ * motionZ)

        if (motionLength > 0.003) {
            val movementYaw = Math.toDegrees(atan2(motionZ, motionX)).toFloat() - 90F
            return MathHelper.wrapAngleTo180_float(movementYaw + 180F)
        }

        val input = player.movementInput ?: return null
        var forward = input.moveForward
        var strafe = input.moveStrafe

        if (forward == 0F && strafe == 0F) {
            return null
        }

        var yaw = player.rotationYaw

        if (forward < 0F) {
            yaw += 180F
            forward = -forward
        }

        if (strafe > 0F) {
            yaw -= if (forward == 0F) 90F else 45F
        } else if (strafe < 0F) {
            yaw += if (forward == 0F) 90F else 45F
        }

        return MathHelper.wrapAngleTo180_float(yaw + 180F)
    }

    private fun randomColorCode(): Char {
        return colorCodes[ThreadLocalRandom.current().nextInt(colorCodes.size)]
    }

    private fun resetState() {
        chatTimer.zero()
        funnyActive = false
        fadeTicks = 0
        forcedYaw = null
        scaffoldAirTicks = 0
        freezeLeftTicks = 0
        rotateLeftTicks = 0
        waitingForNextScaffoldRun = false
    }

}
