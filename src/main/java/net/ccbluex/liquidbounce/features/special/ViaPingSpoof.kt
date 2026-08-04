/*
 * Air Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 */
package net.ccbluex.liquidbounce.features.special

import net.ccbluex.liquidbounce.event.EventState
import net.ccbluex.liquidbounce.event.Listenable
import net.ccbluex.liquidbounce.event.MotionEvent
import net.ccbluex.liquidbounce.event.PacketEvent
import net.ccbluex.liquidbounce.event.WorldEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.utils.client.MinecraftInstance
import net.ccbluex.liquidbounce.utils.client.PacketUtils
import net.minecraft.client.gui.GuiDownloadTerrain
import net.minecraft.network.Packet
import net.minecraft.network.play.client.*
import net.minecraft.network.play.server.*
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Port of RiseClient's PingSpoofComponent.
 *
 * Always registered as a [Listenable]; activation is controlled by [spoof] which sets [enabled]
 * and the category flags. While active, matching packets are captured (cancelled from the network
 * pipeline) into [packets] and released after [amount] ms via the post-motion tick, or flushed
 * immediately via [dispatch] on world change / timeout / teleport.
 */
object ViaPingSpoof : MinecraftInstance, Listenable {

    data class TimedPacket(val packet: Packet<*>, val time: Long, val state: EventState)

    val packets = ConcurrentLinkedQueue<TimedPacket>()

    @Volatile
    var enabled = false
        private set

    private var enabledTime = 0L

    private var amount = 0L

    private var regularEnabled = false
    private var velocityEnabled = false
    private var teleportsEnabled = false
    private var playersEnabled = false
    private var blinkEnabled = false
    private var movementEnabled = false

    private val regular = arrayOf<Class<*>>(
        C0FPacketConfirmTransaction::class.java, C00PacketKeepAlive::class.java, S1CPacketEntityMetadata::class.java
    )
    private val velocity = arrayOf<Class<*>>(
        S12PacketEntityVelocity::class.java, S27PacketExplosion::class.java
    )
    private val teleports = arrayOf<Class<*>>(
        S08PacketPlayerPosLook::class.java, S39PacketPlayerAbilities::class.java, S09PacketHeldItemChange::class.java
    )
    private val players = arrayOf<Class<*>>(
        S13PacketDestroyEntities::class.java, S14PacketEntity::class.java,
        S14PacketEntity.S15PacketEntityRelMove::class.java,
        S14PacketEntity.S16PacketEntityLook::class.java,
        S14PacketEntity.S17PacketEntityLookMove::class.java,
        S18PacketEntityTeleport::class.java,
        S20PacketEntityProperties::class.java, S19PacketEntityHeadLook::class.java
    )
    private val blink = arrayOf<Class<*>>(
        C02PacketUseEntity::class.java, C0DPacketCloseWindow::class.java, C0EPacketClickWindow::class.java,
        C0CPacketInput::class.java, C0BPacketEntityAction::class.java, C08PacketPlayerBlockPlacement::class.java,
        C07PacketPlayerDigging::class.java, C09PacketHeldItemChange::class.java, C13PacketPlayerAbilities::class.java,
        C15PacketClientSettings::class.java, C16PacketClientStatus::class.java, C17PacketCustomPayload::class.java,
        C18PacketSpectate::class.java, C19PacketResourcePackStatus::class.java, C0APacketAnimation::class.java
    )
    private val movement = arrayOf<Class<*>>(
        C03PacketPlayer::class.java, C03PacketPlayer.C04PacketPlayerPosition::class.java,
        C03PacketPlayer.C05PacketPlayerLook::class.java, C03PacketPlayer.C06PacketPlayerPosLook::class.java
    )

    private fun matches(packet: Packet<*>): Boolean {
        val clazz = packet.javaClass
        return (regularEnabled && regular.any { it == clazz }) ||
            (velocityEnabled && velocity.any { it == clazz }) ||
            (teleportsEnabled && teleports.any { it == clazz }) ||
            (playersEnabled && players.any { it == clazz }) ||
            (blinkEnabled && blink.any { it == clazz }) ||
            (movementEnabled && movement.any { it == clazz })
    }

    /**
     * Flush all queued packets immediately. Packets are released through [PacketUtils] without
     * re-triggering PacketEvents so they are not re-captured.
     */
    fun dispatch() {
        if (packets.isEmpty()) return
        val previous = enabled
        enabled = false
        packets.forEach { timed ->
            when (timed.state) {
                EventState.SEND -> PacketUtils.sendPacket(timed.packet, false)
                EventState.RECEIVE -> PacketUtils.schedulePacketProcess(timed.packet)
                else -> {}
            }
        }
        packets.clear()
        enabled = previous
    }

    fun spoof(
        amount: Int,
        regular: Boolean,
        velocity: Boolean,
        teleports: Boolean,
        players: Boolean,
        blink: Boolean = false,
        movement: Boolean = false
    ) {
        enabledTime = System.currentTimeMillis()
        regularEnabled = regular
        velocityEnabled = velocity
        teleportsEnabled = teleports
        playersEnabled = players
        blinkEnabled = blink
        movementEnabled = movement
        this.amount = amount.toLong()
    }

    val onPacket = handler<PacketEvent>(priority = -2) { event ->
        if (!enabled || event.isCancelled) return@handler
        val packet = event.packet
        if (!matches(packet)) return@handler
        event.cancelEvent()
        packets.add(TimedPacket(packet, System.currentTimeMillis(), event.eventType))
    }

    val onPostMotion = handler<MotionEvent> { event ->
        if (event.eventState != EventState.POST) return@handler

        val now = System.currentTimeMillis()
        val stillEnabled = now - enabledTime < 100L && mc.currentScreen !is GuiDownloadTerrain
        enabled = stillEnabled

        if (!stillEnabled) {
            dispatch()
            return@handler
        }

        // Release packets whose hold time has elapsed. Temporarily clear [enabled] so any
        // re-entrant packet sends (e.g. via other handlers) are not re-captured.
        enabled = false
        packets.removeIf { timed ->
            if (timed.time + amount < now) {
                when (timed.state) {
                    EventState.SEND -> PacketUtils.sendPacket(timed.packet, false)
                    EventState.RECEIVE -> PacketUtils.schedulePacketProcess(timed.packet)
                    else -> {}
                }
                true
            } else {
                false
            }
        }
        enabled = true
    }

    val onWorld = handler<WorldEvent> {
        dispatch()
    }
}
