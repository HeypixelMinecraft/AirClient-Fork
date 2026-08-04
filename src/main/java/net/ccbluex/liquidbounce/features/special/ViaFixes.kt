/*
 * Air Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 */
package net.ccbluex.liquidbounce.features.special

import com.viaversion.viabackwards.protocol.v1_17to1_16_4.Protocol1_17To1_16_4
import com.viaversion.viaversion.api.Via
import com.viaversion.viaversion.api.protocol.packet.PacketWrapper
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion
import com.viaversion.viaversion.api.type.Types
import com.viaversion.viaversion.protocols.v1_16_4to1_17.packet.ServerboundPackets1_17
import de.florianmichael.vialoadingbase.ViaLoadingBase
import net.ccbluex.liquidbounce.config.Configurable
import net.ccbluex.liquidbounce.event.*
import net.ccbluex.liquidbounce.utils.client.MinecraftInstance
import net.ccbluex.liquidbounce.utils.client.PacketUtils
import net.minecraft.block.BlockLadder
import net.minecraft.block.state.IBlockState
import net.minecraft.network.play.client.*
import net.minecraft.network.play.client.C02PacketUseEntity.Action
import net.minecraft.potion.Potion
import net.minecraft.util.AxisAlignedBB
import net.minecraft.util.BlockPos
import net.minecraft.util.EnumFacing

/**
 * Migration of RiseClient's viamcp FixComponents.
 *
 * Each handler is gated by the protocol version returned by ViaLoadingBase. Handlers are always
 * registered (handleEvents defaults to true); version checks inside each handler decide whether
 * the fix actually applies for the current connection.
 */
object ViaFixes : Configurable("ViaFixes"), MinecraftInstance, Listenable {

    private val version
        get() = ViaLoadingBase.getInstance().targetVersion

    // ---- BlockPlacementFix (1.11+: server expects a normalized facing vector) ----
    val onBlockPlacement = handler<PacketEvent> { event ->
        if (event.eventType != EventState.SEND) return@handler
        if (!version.newerThanOrEqualTo(ProtocolVersion.v1_11)) return@handler

        val packet = event.packet
        if (packet is C08PacketPlayerBlockPlacement) {
            event.cancelEvent()
            PacketUtils.sendPacket(
                C08PacketPlayerBlockPlacement(
                    packet.position,
                    packet.placedBlockDirection,
                    packet.stack,
                    packet.placedBlockOffsetX / 16.0f,
                    packet.placedBlockOffsetY / 16.0f,
                    packet.placedBlockOffsetZ / 16.0f
                ),
                false
            )
        }
    }

    // ---- BoundsFix (1.9+: tighter AABB reported to the server) ----
    val onBoundsUpdate = handler<UpdateEvent> {
        if (!version.newerThan(ProtocolVersion.v1_8)) return@handler
        val player = mc.thePlayer ?: return@handler
        player.setEntityBoundingBox(
            AxisAlignedBB(
                player.posX - 0.3, player.posY, player.posZ - 0.3,
                player.posX + 0.3, player.posY + 1.8, player.posZ + 0.3
            )
        )
    }

    // ---- FlyingPacketFix (1.9+: drop redundant C03 packets) ----
    private var lastGround = false

    val onFlyingPacket = handler<PacketEvent>(priority = -2) { event ->
        if (event.eventType != EventState.SEND) return@handler
        if (!version.newerThan(ProtocolVersion.v1_8)) return@handler

        val packet = event.packet
        if (packet !is C03PacketPlayer) return@handler
        if (!packet.isMoving && !packet.rotating && packet.onGround == lastGround) {
            event.cancelEvent()
        }
        lastGround = packet.onGround
    }

    // ---- InteractEntityFix (1.9+: only ATTACK action is valid for C02) ----
    val onInteractEntity = handler<PacketEvent>(priority = -2) { event ->
        if (event.isCancelled) return@handler
        if (event.eventType != EventState.SEND) return@handler
        if (!version.newerThan(ProtocolVersion.v1_8)) return@handler

        val packet = event.packet
        if (packet is C02PacketUseEntity && packet.action != Action.ATTACK) {
            event.cancelEvent()
        }
    }

    // ---- LadderFix (1.9+: ladder bounding box uses FACING metadata) ----
    val onBlockBB = handler<BlockBBEvent> { event ->
        if (!version.newerThan(ProtocolVersion.v1_8)) return@handler
        if (event.block !is BlockLadder) return@handler

        val blockPos = BlockPos(event.x, event.y, event.z)
        val state: IBlockState = mc.theWorld.getBlockState(blockPos)
        if (state.block !== event.block) return@handler

        val thickness = (0.125f + 0.0625f).toDouble()
        val bb = when (state.getValue(BlockLadder.FACING)) {
            EnumFacing.NORTH ->
                AxisAlignedBB(0.0, 0.0, 1.0 - thickness, 1.0, 1.0, 1.0)
            EnumFacing.SOUTH ->
                AxisAlignedBB(0.0, 0.0, 0.0, 1.0, 1.0, thickness)
            EnumFacing.WEST ->
                AxisAlignedBB(1.0 - thickness, 0.0, 0.0, 1.0, 1.0, 1.0)
            EnumFacing.EAST ->
                AxisAlignedBB(0.0, 0.0, 0.0, thickness, 1.0, 1.0)
            else -> return@handler
        }
        event.boundingBox = bb.offset(event.x.toDouble(), event.y.toDouble(), event.z.toDouble())
    }

    // ---- TransactionFix (1.17+: replace C0F transactions with PONG packets) ----
    val onTransaction = handler<PacketEvent>(priority = -2) { event ->
        if (event.isCancelled) return@handler
        if (event.eventType != EventState.SEND) return@handler
        if (!version.newerThanOrEqualTo(ProtocolVersion.v1_17)) return@handler

        val packet = event.packet
        if (packet !is C0FPacketConfirmTransaction) return@handler

        val connection = Via.getManager().connectionManager.connections.stream().findFirst().orElse(null)
        if (connection != null) {
            val pong = PacketWrapper.create(ServerboundPackets1_17.PONG, connection)
            pong.write(Types.VAR_INT, packet.uid.toInt())
            pong.sendToServer(Protocol1_17To1_16_4::class.java)
        }
        event.cancelEvent()
    }

    // ---- SpeedFix (1.17+: jump-potion friction differs from native) ----
    val onStrafe = handler<StrafeEvent>(priority = 1) { event ->
        if (!version.newerThanOrEqualTo(ProtocolVersion.v1_17)) return@handler
        val player = mc.thePlayer ?: return@handler
        if (!player.isPotionActive(Potion.moveSpeed)) return@handler

        val friction = arrayOf(
            floatArrayOf(0.11999998f, 0.15599997f),
            floatArrayOf(0.13999997f, 0.18199998f)
        )
        val effect = player.getActivePotionEffect(Potion.moveSpeed) ?: return@handler
        val speed = effect.amplifier.coerceAtMost(1)
        val sprinting = if (player.isSprinting) 1 else 0

        if (player.onGround) {
            event.friction = friction[speed][sprinting]
        }
    }

    // ---- PostFix (1.9+: keep alive spoof so transaction/pong packets are batched with motion) ----
    val onPostUpdate = handler<UpdateEvent> {
        if (!version.newerThan(ProtocolVersion.v1_8)) return@handler
        ViaPingSpoof.spoof(1, regular = true, velocity = true, teleports = false, players = true)
    }

    val onTeleport = handler<TeleportEvent> {
        if (!version.newerThan(ProtocolVersion.v1_8)) return@handler
        ViaPingSpoof.dispatch()
    }
}
