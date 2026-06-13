/*
 * Air Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 */
package net.ccbluex.liquidbounce.features.module.modules.movement.flymodes.other

import net.ccbluex.liquidbounce.features.module.modules.movement.flymodes.FlyMode
import net.ccbluex.liquidbounce.utils.extensions.isMoving
import net.ccbluex.liquidbounce.utils.extensions.tryJump
import net.ccbluex.liquidbounce.utils.movement.MovementUtils

object FairFightFly : FlyMode("FairFight") {
    override fun onUpdate() {
        val player = mc.thePlayer ?: return

        if (!player.isMoving) {
            player.motionX = 0.0
            player.motionZ = 0.0
            return
        }

        player.isSprinting = true

        if (player.onGround && mc.gameSettings.keyBindJump.isKeyDown) {
            player.tryJump()
        }

        if (player.onGround) {
            MovementUtils.strafe(0.2873F, strength = 0.25)
        }
    }
}
