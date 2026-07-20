// skid OpenMyau
package net.ccbluex.liquidbounce.features.module.modules.combat

import net.ccbluex.liquidbounce.event.EventState
import net.ccbluex.liquidbounce.event.MovementInputEvent
import net.ccbluex.liquidbounce.event.PacketEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.utils.timing.MSTimer
import net.minecraft.network.play.client.C02PacketUseEntity
import net.minecraft.potion.Potion

object WTap : Module("WTap", Category.COMBAT) {

    private val timer = MSTimer()
    private var active = false
    private var stopForward = false
    private var delayTicks = 0L
    private var durationTicks = 0L

    private val delay by float("Delay", 5.5f, 0f..10f)
    private val duration by float("Duration", 1.5f, 1f..5f)

    private fun canTrigger(): Boolean {
        val player = mc.thePlayer ?: return false
        return !(player.movementInput.moveForward < 0.8f)
                && !player.isCollidedHorizontally
                && (!(player.foodStats.foodLevel.toFloat() <= 6f) || player.capabilities.allowFlying)
                && (player.isSprinting
                || !player.isUsingItem && !player.isPotionActive(Potion.blindness) && mc.gameSettings.keyBindSprint.isKeyDown)
    }

    val onMoveInput = handler<MovementInputEvent> {
        val player = mc.thePlayer ?: return@handler
        if (active) {
            if (!stopForward && !canTrigger()) {
                active = false
                while (delayTicks > 0L) delayTicks -= 50L
                while (durationTicks > 0L) durationTicks -= 50L
            } else if (delayTicks > 0L) {
                delayTicks -= 50L
            } else {
                if (durationTicks > 0L) {
                    durationTicks -= 50L
                    stopForward = true
                    player.movementInput.moveForward = 0f
                }
                if (durationTicks <= 0L) {
                    active = false
                }
            }
        }
    }

    val onPacket = handler<PacketEvent> {
        if (!handleEvents()) return@handler
        if (it.eventType != EventState.SEND) return@handler
        if (it.isCancelled) return@handler

        if (it.packet is C02PacketUseEntity
            && it.packet.action == C02PacketUseEntity.Action.ATTACK
            && !active
            && timer.hasTimePassed(500L)
            && mc.thePlayer.isSprinting
        ) {
            timer.reset()
            active = true
            stopForward = false
            delayTicks += (50f * delay).toLong()
            durationTicks += (50f * duration).toLong()
        }
    }

    override fun onDisable() {
        active = false
        stopForward = false
        delayTicks = 0
        durationTicks = 0
    }
}
