/*
 * Air Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 */
package net.ccbluex.liquidbounce.features.module.modules.movement.flymodes.other

import net.ccbluex.liquidbounce.event.BlockBBEvent
import net.ccbluex.liquidbounce.features.module.modules.movement.Fly
import net.ccbluex.liquidbounce.features.module.modules.movement.flymodes.FlyMode
import net.ccbluex.liquidbounce.utils.extensions.isMoving
import net.ccbluex.liquidbounce.utils.movement.MovementUtils
import net.minecraft.block.BlockLadder
import net.minecraft.block.material.Material
import net.minecraft.util.AxisAlignedBB

object FairFightFly : FlyMode("FairFight") {
    override fun onEnable() {
        mc.thePlayer?.motionY = 0.0
    }

    override fun onUpdate() {
        val player = mc.thePlayer ?: return

        player.motionY = when {
            mc.gameSettings.keyBindJump.isKeyDown -> 0.42
            mc.gameSettings.keyBindSneak.isKeyDown -> -0.08
            else -> 0.0
        }

        if (player.isMoving) {
            player.isSprinting = true
            MovementUtils.strafe(0.18F, strength = 0.35)
        } else {
            player.motionX = 0.0
            player.motionZ = 0.0
        }
    }

    override fun onBB(event: BlockBBEvent) {
        if (mc.gameSettings.keyBindSneak.isKeyDown)
            return

        if (event.y.toDouble() > Fly.startY)
            return

        if (!event.block.material.blocksMovement() &&
            event.block.material != Material.carpet &&
            event.block.material != Material.vine &&
            event.block.material != Material.snow &&
            event.block !is BlockLadder
        ) {
            event.boundingBox = AxisAlignedBB.fromBounds(
                event.x.toDouble(),
                Fly.startY - 1.0,
                event.z.toDouble(),
                event.x.toDouble() + 1.0,
                Fly.startY,
                event.z.toDouble() + 1.0
            )
        }
    }

    override fun onDisable() {
        mc.thePlayer?.motionY = 0.0
    }
}
