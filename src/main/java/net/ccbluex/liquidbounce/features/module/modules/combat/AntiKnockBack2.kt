// skid Myau
package net.ccbluex.liquidbounce.features.module.modules.combat

import net.ccbluex.liquidbounce.event.*
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.features.module.ModuleManager
import net.ccbluex.liquidbounce.utils.extensions.center
import net.ccbluex.liquidbounce.utils.extensions.isMoving
import net.ccbluex.liquidbounce.utils.rotation.Rotation
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityLivingBase
import net.minecraft.network.play.client.C02PacketUseEntity
import net.minecraft.network.play.client.C0APacketAnimation
import net.minecraft.network.play.server.S12PacketEntityVelocity
import net.minecraft.network.play.server.S19PacketEntityStatus
import net.minecraft.network.play.server.S27PacketExplosion
import net.minecraft.util.MathHelper
import kotlin.math.atan2
import kotlin.math.sqrt

object AntiKnockBack2 : Module("AntiKnockBack2", Category.COMBAT) {

    // Mode
    private val mode by choices("Mode", arrayOf("Reduce", "Intave"), "Reduce")

    // Delay settings (Reduce mode)
    private val delay by boolean("Delay", false) { mode == "Reduce" }
    private val delayTicks by int("DelayTicks", 3, 1..20) { mode == "Reduce" && delay }
    private val delayChance by int("DelayChance", 100, 0..100) { mode == "Reduce" && delay }

    // JumpReset (Reduce mode)
    private val jumpReset by boolean("JumpReset", true) { mode == "Reduce" }

    // Reduce chance (Reduce mode)
    private val reduceChance by int("ReduceChance", 100, 0..100) { mode == "Reduce" }

    // Tick limit (Reduce mode)
    private val tickLimit by float("TickLimit", 3f, 0f..20f) { mode == "Reduce" }

    // Rotate settings (Reduce mode)
    private val rotate by boolean("Rotate", false) { mode == "Reduce" }
    private val rotateTick by int("RotateTick", 2, 1..12) { mode == "Reduce" && rotate }

    // AutoMove settings (Reduce mode)
    private val autoMove by boolean("AutoMove", false) { mode == "Reduce" }
    private val moveTick by int("MoveTick", 2, 1..12) { mode == "Reduce" && autoMove }

    // Intave JumpReset
    private val intaveJumpReset by boolean("IntaveJumpReset", true) { mode == "Intave" }

    // State variables
    var hasTakenVelocity = false
        private set

    private var slot = false
    private var attack = false
    private var swing = false
    private var block = false
    private var inventory = false
    private var dig = false
    private var jumpFlag = false
    private var hurtTime = 0
    private var ticksSinceVelocity = 0
    private var rotatoTickCounter = 0
    private var targetRotation: Rotation? = null
    private var knockbackX = 0.0
    private var knockbackZ = 0.0
    private var autoMoveSprint = false
    private var autoMoveTickCounter = 0
    private var delayChanceCounter = 0
    private var pendingExplosion = false
    private var allowNext = true
    private var reverseFlag = false
    private var delayActive = false

    // Delayed velocity packet storage
    private var delayedVelocityX = 0.0
    private var delayedVelocityY = 0.0
    private var delayedVelocityZ = 0.0
    private var delayTickCounter = 0
    private var hasDelayedPacket = false

    // KillAura access
    private val killAuraModule: Module?
        get() = ModuleManager.getModule("KillAura")

    // 反射缓存：避免每次调用都重新查找 Method/Field
    private var shouldAutoBlockMethod: java.lang.reflect.Method? = null
    private var targetField: java.lang.reflect.Field? = null
    private var killAuraReflectInited = false

    private fun initKillAuraReflect(ka: Module) {
        if (killAuraReflectInited) return
        killAuraReflectInited = true
        try {
            shouldAutoBlockMethod = ka.javaClass.getDeclaredMethod("shouldAutoBlock")
            shouldAutoBlockMethod?.isAccessible = true
        } catch (_: Exception) {}
        try {
            targetField = ka.javaClass.getDeclaredField("target")
            targetField?.isAccessible = true
        } catch (_: Exception) {}
    }

    private fun getKillAuraTarget(): EntityLivingBase? {
        val ka = killAuraModule ?: return null
        initKillAuraReflect(ka)
        return try {
            targetField?.get(ka) as? EntityLivingBase
        } catch (_: Exception) {
            null
        }
    }

    private fun isKillAuraAutoBlocking(): Boolean {
        val ka = killAuraModule ?: return false
        initKillAuraReflect(ka)
        return try {
            shouldAutoBlockMethod?.invoke(ka) as? Boolean ?: false
        } catch (_: Exception) {
            false
        }
    }

    private val LongJumpModule: Module?
        get() = ModuleManager.getModule("LongJump")

    private fun isLongJumpActive(): Boolean {
        val lj = LongJumpModule ?: return false
        return lj.state
    }

    private fun isInLiquidOrWeb(): Boolean {
        val player = mc.thePlayer ?: return false
        return player.isInWater || player.isInLava || player.isInWeb
    }

    private fun canDelay(): Boolean {
        val player = mc.thePlayer ?: return false
        return player.onGround && (!isKillAuraAutoBlocking())
    }

    private fun calculateRotationTo(deltaX: Double, deltaY: Double, deltaZ: Double, currentYaw: Float, currentPitch: Float): Rotation {
        val dist = sqrt(deltaX * deltaX + deltaZ * deltaZ)
        val yaw = (MathHelper.wrapAngleTo180_float((atan2(deltaZ, deltaX).toFloat() * 180f / Math.PI.toFloat()) - 90f))
        val pitch = MathHelper.wrapAngleTo180_float(-atan2(deltaY, dist).toFloat() * 180f / Math.PI.toFloat())
        return Rotation(yaw, pitch)
    }

    private fun badPackets(
        checkSlot: Boolean = false,
        checkAttack: Boolean = true,
        checkSwing: Boolean = false,
        checkBlock: Boolean = false,
        checkInventory: Boolean = false,
        checkDig: Boolean = false
    ): Boolean {
        return (slot && checkSlot) || (attack && checkAttack) || (swing && checkSwing) ||
                (block && checkBlock) || (inventory && checkInventory) || (dig && checkDig)
    }

    private fun resetBadPackets() {
        slot = false
        swing = false
        attack = false
        block = false
        inventory = false
        dig = false
    }

    private fun isInView(target: Entity): Boolean {
        val world = mc.theWorld ?: return false
        val player = mc.thePlayer ?: return false
        val eyePos = player.getPositionEyes(1f)
        val center = target.entityBoundingBox.center
        return world.rayTraceBlocks(eyePos, center) == null
    }

    val onPacket = handler<PacketEvent> { event ->
        val player = mc.thePlayer ?: return@handler

        if (event.eventType == EventState.RECEIVE && !event.isCancelled) {
            val packet = event.packet

            if (packet is S12PacketEntityVelocity && packet.entityID == player.entityId) {
                // Delay logic
                if (mode == "Reduce" && delay) {
                    if (!reverseFlag && !canDelay() && !isInLiquidOrWeb() && !pendingExplosion && !isLongJumpActive()) {
                        delayChanceCounter = delayChanceCounter % 100 + delayChance
                        if (delayChanceCounter >= 100) {
                            // Store delayed packet data and cancel
                            delayedVelocityX = packet.motionX / 8000.0
                            delayedVelocityY = packet.motionY / 8000.0
                            delayedVelocityZ = packet.motionZ / 8000.0
                            hasDelayedPacket = true
                            delayTickCounter = 0
                            event.cancelEvent()
                            reverseFlag = true
                            delayActive = true
                            return@handler
                        }
                    }
                }

                ticksSinceVelocity = 0
                hasTakenVelocity = true

                if (rotate) {
                    knockbackX = (-packet.motionX).toDouble() / 8000.0
                    knockbackZ = (-packet.motionZ).toDouble() / 8000.0
                    if (kotlin.math.abs(knockbackX) > 0.01 || kotlin.math.abs(knockbackZ) > 0.01) {
                        rotatoTickCounter = 1
                    }
                }
            } else if (packet is S27PacketExplosion) {
                if (packet.field_149152_f != 0f || packet.field_149153_g != 0f || packet.field_149159_h != 0f) {
                    pendingExplosion = true
                }
            } else if (packet is S19PacketEntityStatus) {
                val entity = packet.getEntity(mc.theWorld)
                if (entity != null && entity == player && packet.opCode.toInt() == 2) {
                    allowNext = false
                }
            }
        }

        if (event.eventType == EventState.SEND && !event.isCancelled) {
            val packet = event.packet
            when (packet) {
                is net.minecraft.network.play.client.C09PacketHeldItemChange -> slot = true
                is C0APacketAnimation -> swing = true
                is C02PacketUseEntity -> {
                    if (packet.action == C02PacketUseEntity.Action.ATTACK) {
                        attack = true
                    }
                }
                is net.minecraft.network.play.client.C08PacketPlayerBlockPlacement -> block = true
                is net.minecraft.network.play.client.C07PacketPlayerDigging -> {
                    block = true
                    dig = true
                }
                is net.minecraft.network.play.client.C0DPacketCloseWindow,
                is net.minecraft.network.play.client.C0EPacketClickWindow -> inventory = true
                is net.minecraft.network.play.client.C16PacketClientStatus -> {
                    if (packet.status == net.minecraft.network.play.client.C16PacketClientStatus.EnumState.OPEN_INVENTORY_ACHIEVEMENT) {
                        inventory = true
                    }
                }
                is net.minecraft.network.play.client.C03PacketPlayer -> resetBadPackets()
            }
        }
    }

    val onAttack = handler<AttackEvent> { event ->
        val player = mc.thePlayer ?: return@handler

        // Intave mode: reduce motion on specific hurtTime
        if (mode == "Intave") {
            when (player.hurtTime) {
                7 -> {
                    player.motionX *= 0.6
                    player.motionZ *= 0.6
                }
                8 -> {
                    player.motionX *= 0.36
                    player.motionZ *= 0.36
                }
                9 -> {
                    player.motionX *= 0.6
                    player.motionZ *= 0.6
                }
            }
        }
    }

    val onMotion = handler<MotionEvent> { event ->
        val player = mc.thePlayer ?: return@handler

        // POST phase
        if (event.eventState == EventState.POST) {
            // Delay release logic
            if (reverseFlag && (canDelay() || isInLiquidOrWeb() || delayTickCounter >= delayTicks)) {
                // Release delayed packet by applying the motion
                if (hasDelayedPacket) {
                    player.motionX += delayedVelocityX
                    player.motionY += delayedVelocityY
                    player.motionZ += delayedVelocityZ
                    hasDelayedPacket = false
                }
                reverseFlag = false
                delayActive = false
            }

            // Rotation counter logic
            val maxTick = rotateTick
            if (rotatoTickCounter > 0 && rotatoTickCounter <= maxTick) {
                rotatoTickCounter++
                if (rotatoTickCounter > maxTick) {
                    rotatoTickCounter = 0
                    targetRotation = null
                    knockbackX = 0.0
                    knockbackZ = 0.0
                }
            }
        }

        // PRE phase
        if (event.eventState == EventState.PRE) {
            // Delay tick counter
            if (hasDelayedPacket) {
                delayTickCounter++
            }

            // AutoMove sprint restore
            if (autoMoveSprint && autoMoveTickCounter > 0) {
                autoMoveTickCounter--
                if (autoMoveTickCounter == 0) {
                    if (player.isMoving && !player.isSprinting) {
                        player.isSprinting = true
                    }
                    autoMoveSprint = false
                }
            }

            // Rotation application
            val maxTick = rotateTick
            if (rotatoTickCounter > 0 && rotatoTickCounter <= maxTick) {
                if (rotatoTickCounter == 1) {
                    targetRotation = calculateRotationTo(
                        knockbackX, 0.0, knockbackZ,
                        player.rotationYaw, player.rotationPitch
                    )
                }

                targetRotation?.let { rot ->
                    player.rotationYaw = rot.yaw
                    player.rotationPitch = rot.pitch
                }
            }

            // Reduce mode logic
            ticksSinceVelocity++
            val limit = tickLimit
            val withinLimit = limit == 0f || ticksSinceVelocity < limit

            if (mode == "Reduce") {
                if (hasTakenVelocity && withinLimit) {
                    val target = getKillAuraTarget()
                    if (target != null) {
                        if (!isInView(target)) return@handler

                        if (player.isInWeb) return@handler

                        if (!player.isSwingInProgress) return@handler

                        if (player.isMoving && player.isSprinting) {
                            if (badPackets()) return@handler

                            // Reduce chance check
                            if (reduceChance < 100 && (Math.random() * 100).toInt() >= reduceChance) return@handler

                            // Send extra attack packets to reduce knockback
                            mc.netHandler?.addToSendQueue(C0APacketAnimation())
                            mc.netHandler?.addToSendQueue(C02PacketUseEntity(target, C02PacketUseEntity.Action.ATTACK))

                            player.motionX *= 0.6
                            player.motionZ *= 0.6
                            player.isSprinting = false

                            // AutoMove: restore sprint after ticks
                            if (autoMove && player.isMoving) {
                                autoMoveSprint = true
                                autoMoveTickCounter = moveTick
                            }

                            hasTakenVelocity = false
                            return@handler
                        }
                    }
                } else if (limit > 0f && ticksSinceVelocity >= limit) {
                    hasTakenVelocity = false
                }
            } else if (mode == "Intave") {
                // Intave JumpReset
                if (player.hurtTime == 9 && intaveJumpReset && player.isSprinting &&
                    player.onGround && !mc.gameSettings.keyBindJump.isKeyDown
                ) {
                    player.jump()
                }
            }
        }
    }

    val onLivingUpdate = handler<MovementInputEvent> {
        val player = mc.thePlayer ?: return@handler

        if (jumpFlag) {
            jumpFlag = false
            if (player.onGround && player.isSprinting && !player.isInWater && !player.isInLava) {
                player.movementInput.jump = true
            }
        }
    }

    val onStrafe = handler<StrafeEvent> {
        val player = mc.thePlayer ?: return@handler

        // JumpReset trigger (Reduce mode)
        if (player.hurtTime == 9 && hurtTime != 9 && player.onGround &&
            !mc.gameSettings.keyBindJump.isKeyDown && jumpReset
        ) {
            jumpFlag = true
        }
        hurtTime = player.hurtTime

        // AutoMove: adjust strafe for rotation
        if (rotatoTickCounter > 0 && rotatoTickCounter <= rotateTick) {
            if (autoMove) {
                player.movementInput.moveForward = 1f
            }
        }
    }

    val onWorld = handler<WorldEvent> {
        onDisable()
    }

    override fun onEnable() {
        hasTakenVelocity = false
        hurtTime = 0
        jumpFlag = false
        ticksSinceVelocity = 100
        rotatoTickCounter = 0
        targetRotation = null
        knockbackX = 0.0
        knockbackZ = 0.0
        autoMoveSprint = false
        autoMoveTickCounter = 0
        pendingExplosion = false
        allowNext = true
        reverseFlag = false
        delayActive = false
        hasDelayedPacket = false
        delayTickCounter = 0
        delayChanceCounter = 0
    }

    override fun onDisable() {
        hasTakenVelocity = false
        hurtTime = 0
        jumpFlag = false
        ticksSinceVelocity = 0
        rotatoTickCounter = 0
        targetRotation = null
        knockbackX = 0.0
        knockbackZ = 0.0
        autoMoveSprint = false
        autoMoveTickCounter = 0
        pendingExplosion = false
        allowNext = true
        reverseFlag = false
        delayActive = false
        hasDelayedPacket = false
        delayTickCounter = 0
        slot = false
        swing = false
        attack = false
        block = false
        inventory = false
        dig = false
    }

    override val tag: String?
        get() = mode
}
