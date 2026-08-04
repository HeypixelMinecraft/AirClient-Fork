// skid LiquidBounce
package net.ccbluex.liquidbounce.features.module.modules.combat

import net.ccbluex.liquidbounce.event.AttackEvent
import net.ccbluex.liquidbounce.event.GameTickEvent
import net.ccbluex.liquidbounce.event.PacketEvent
import net.ccbluex.liquidbounce.event.PlayerTickEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.features.module.modules.player.Gapple
import net.ccbluex.liquidbounce.utils.client.BlinkUtils
import net.ccbluex.liquidbounce.utils.client.chat
import net.ccbluex.liquidbounce.utils.client.realMotionX
import net.ccbluex.liquidbounce.utils.client.realMotionZ
import net.ccbluex.liquidbounce.utils.extensions.getDistanceToEntityBox
import net.ccbluex.liquidbounce.utils.extensions.isMoving
import net.ccbluex.liquidbounce.utils.extensions.tryJump
import net.ccbluex.liquidbounce.utils.extras.StuckUtils
import net.ccbluex.liquidbounce.utils.rotation.RaycastUtils
import net.ccbluex.liquidbounce.utils.rotation.RotationUtils
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.item.EntityArmorStand
import net.minecraft.network.play.client.C02PacketUseEntity
import net.minecraft.network.play.client.C03PacketPlayer
import net.minecraft.network.play.client.C07PacketPlayerDigging
import net.minecraft.network.play.client.C0APacketAnimation
import net.minecraft.network.play.client.C0BPacketEntityAction
import net.minecraft.network.play.client.C0FPacketConfirmTransaction
import net.minecraft.network.play.server.S08PacketPlayerPosLook
import net.minecraft.network.play.server.S12PacketEntityVelocity
import net.minecraft.util.BlockPos
import net.minecraft.util.EnumFacing
import kotlin.math.pow

object AntiVelocity : Module("AntiVelocity", Category.COMBAT) {

    private val mode by choices("Mode", arrayOf("GrimNoXZ", "GrimReduce", "Grim1.17C06", "CancelC0F", "MatrixReduce"), "GrimNoXZ")
    private val c02s by int("GrimNoXZC02Counts", 5, 1..16) { mode == "GrimNoXZ" }
    private val rayCast by boolean("GrimNoXZRayCast", true) { mode == "GrimNoXZ" }
    private val reach by float("GrimNoXZReach", 3.2f, 3.0f..6.0f) { mode == "GrimNoXZ" }
    private val legitSprint by boolean("GrimNoXZLegitSprint", false) { mode == "GrimNoXZ" }
    private val stopSprint by boolean("GrimNoXZStopSprint", true) { mode == "GrimNoXZ" }
    private val setSprint by boolean("GrimNoXZSetSprint", false) { mode == "GrimNoXZ" }
    private val reduceMotion by int("GrimNoXZMotion", 5, 1..16) { mode == "GrimNoXZ" }
    private val lagDebug by boolean("GrimNoXZLagDebug", true) { mode == "GrimNoXZ" }
    private val cancelC0FCounts by int("CancelC0FCounts", 6, 1..16) { mode == "CancelC0F" }

    private var needVelocity = false
    private var skipTicks = 0
    private var lastHurtTime = 0
    private var entity: Entity? = null
    private var lastAttackReach = 0.0
    private var cancelC0FTicks = 0

    override val tag: String
        get() = mode

    override fun onDisable() {
        needVelocity = false
    }

    val onPacket = handler<PacketEvent> { event ->
        val packet = event.packet

        if (packet is S12PacketEntityVelocity && mc.thePlayer.entityId == packet.entityID) {
            when (mode) {
                "GrimNoXZ" -> {
                    entity = RaycastUtils.raycastEntity(6.0, RotationUtils.serverRotation.yaw, RotationUtils.serverRotation.pitch) {
                        it is EntityLivingBase && it !is EntityArmorStand
                    }
                    if (entity == null && !rayCast) {
                        entity = KillAura.target as? Entity
                    }
                    entity?.let {
                        if (mc.thePlayer.getDistanceToEntityBox(it) > reach.toDouble()) {
                            entity = null
                        }
                    }
                    entity?.let {
                        lastAttackReach = mc.thePlayer.getDistanceToEntityBox(it)
                    }
                    needVelocity = true
                }

                "Grim1.17C06" -> {
                    mc.netHandler.addToSendQueue(C03PacketPlayer.C06PacketPlayerPosLook(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ, mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch, mc.thePlayer.onGround))
                    mc.netHandler.addToSendQueue(C07PacketPlayerDigging(C07PacketPlayerDigging.Action.ABORT_DESTROY_BLOCK, BlockPos(mc.thePlayer).up(), EnumFacing.DOWN))
                    mc.netHandler.addToSendQueue(C07PacketPlayerDigging(C07PacketPlayerDigging.Action.STOP_DESTROY_BLOCK, BlockPos(mc.thePlayer).up(), EnumFacing.DOWN))
                    needVelocity = true
                    event.cancelEvent()
                }

                "CancelC0F" -> {
                    cancelC0FTicks = cancelC0FCounts
                    event.cancelEvent()
                }

                "GrimReduce" -> needVelocity = true

                "MatrixReduce" -> {
                    packet.motionX = (packet.realMotionX * 0.33).toInt()
                    packet.motionZ = (packet.realMotionZ * 0.33).toInt()
                    if (mc.thePlayer.onGround) {
                        packet.motionX = (packet.realMotionX * 0.86).toInt()
                        packet.motionZ = (packet.realMotionZ * 0.86).toInt()
                    }
                }
            }
        }

        if (cancelC0FTicks > 0 && packet is C0FPacketConfirmTransaction) {
            event.cancelEvent()
            cancelC0FTicks -= 1
        }

        if (packet is S08PacketPlayerPosLook && lagDebug) {
            chat("Detect Lag AttackReach: $lastAttackReach")
        }
    }

    val onGameTick = handler<GameTickEvent> {
        when (mode) {
            "GrimNoXZ" -> {
                val sprintState = mc.thePlayer.serverSprintState
                if (needVelocity) {
                    if (mc.thePlayer.hurtTime != 0 && entity != null) {
                        if (!sprintState) {
                            if (legitSprint) {
                                StuckUtils.skipTicks = 1
                                mc.netHandler.addToSendQueue(C03PacketPlayer(mc.thePlayer.onGround))
                            }
                            mc.netHandler.addToSendQueue(C0BPacketEntityAction(mc.thePlayer, C0BPacketEntityAction.Action.START_SPRINTING))
                            if (setSprint) {
                                mc.thePlayer.isSprinting = true
                                mc.thePlayer.serverSprintState = true
                            }
                        }

                        for (i in 0..c02s) {
                            if (BlinkUtils.isBlinking) {
                                BlinkUtils.packets.add(C0APacketAnimation())
                                BlinkUtils.packets.add(C02PacketUseEntity(entity, C02PacketUseEntity.Action.ATTACK))
                            } else if (Gapple.isEating) {
                                Gapple.packets.add(C0APacketAnimation())
                                Gapple.packets.add(C02PacketUseEntity(entity, C02PacketUseEntity.Action.ATTACK))
                            } else {
                                mc.netHandler.addToSendQueue(C0APacketAnimation())
                                mc.netHandler.addToSendQueue(C02PacketUseEntity(entity, C02PacketUseEntity.Action.ATTACK))
                            }
                        }

                        if (!sprintState && stopSprint) {
                            mc.netHandler.addToSendQueue(C0BPacketEntityAction(mc.thePlayer, C0BPacketEntityAction.Action.STOP_SPRINTING))
                        }

                        mc.thePlayer.motionX *= 0.6.pow(reduceMotion.toDouble())
                        mc.thePlayer.motionZ *= 0.6.pow(reduceMotion.toDouble())
                    }
                    needVelocity = false
                }
            }

            "Grim1.17C06" -> {
                if (needVelocity) {
                    mc.netHandler.addToSendQueue(C03PacketPlayer.C06PacketPlayerPosLook(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ, mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch, mc.thePlayer.onGround))
                    mc.netHandler.addToSendQueue(C07PacketPlayerDigging(C07PacketPlayerDigging.Action.ABORT_DESTROY_BLOCK, BlockPos(mc.thePlayer).up(), EnumFacing.DOWN))
                    mc.netHandler.addToSendQueue(C07PacketPlayerDigging(C07PacketPlayerDigging.Action.STOP_DESTROY_BLOCK, BlockPos(mc.thePlayer).up(), EnumFacing.DOWN))
                    needVelocity = false
                }
            }
        }
    }

    val onAttack = handler<AttackEvent> {
        if (mode == "GrimReduce") {
            if (mc.thePlayer.hurtTime == 0) {
                needVelocity = false
            }
            if (needVelocity) {
                if (mc.thePlayer.hurtTime > 5 && mc.thePlayer.hurtTime != lastHurtTime && mc.thePlayer.onGround) {
                    mc.thePlayer.tryJump()
                    chat("Player Jump")
                }
                if (!mc.thePlayer.isMoving || !mc.thePlayer.isSprinting) {
                    return@handler
                }
                if (mc.thePlayer.hurtTime != lastHurtTime) {
                    when (mc.thePlayer.hurtTime) {
                        9 -> {
                            mc.thePlayer.motionX *= 0.8
                            mc.thePlayer.motionX *= 0.8
                        }
                        8 -> {
                            mc.thePlayer.motionX *= 0.11
                            mc.thePlayer.motionX *= 0.11
                        }
                        7 -> {
                            mc.thePlayer.motionX *= 0.4
                            mc.thePlayer.motionX *= 0.4
                        }
                        4 -> {
                            mc.thePlayer.motionX *= 0.37
                            mc.thePlayer.motionX *= 0.37
                        }
                    }
                    lastHurtTime = mc.thePlayer.hurtTime
                }
            }
        }
    }

    val onPlayerTicks = handler<PlayerTickEvent> { event ->
        if (skipTicks > 0) {
            event.cancelEvent()
            skipTicks -= 1
        }
    }
}
