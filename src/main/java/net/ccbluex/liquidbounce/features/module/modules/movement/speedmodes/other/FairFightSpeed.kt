/*
 * Air Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 */
package net.ccbluex.liquidbounce.features.module.modules.movement.speedmodes.other

import net.ccbluex.liquidbounce.features.module.modules.movement.speedmodes.SpeedMode
import net.ccbluex.liquidbounce.utils.extensions.isMoving
import net.ccbluex.liquidbounce.utils.extensions.tryJump
import net.ccbluex.liquidbounce.utils.movement.MovementUtils

object FairFightSpeed : SpeedMode("FairFight") {
    override fun onMotion() {
        val player = mc.thePlayer ?: return

        if (!player.isMoving) {
            player.motionX = 0.0
            player.motionZ = 0.0
            return
        }

        player.isSprinting = true

        if (player.onGround) {
            MovementUtils.strafe(0.2873F, strength = 0.35)
            player.tryJump()
            return
        }

        MovementUtils.strafe(MovementUtils.speed.coerceAtMost(0.36F), strength = 0.08)
    }
}
