// skid Leader-Lite
/*
 * Air Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 */
package net.ccbluex.liquidbounce.features.module.modules.render

import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module

/**
 * BetterFPS
 *
 * Migrated from Leader-Lite. Skips forced [System.gc] calls during world load
 * and integrated server launch to reduce load-time stutter.
 *
 * The actual interception is performed in
 * [net.ccbluex.liquidbounce.injection.forge.mixins.client.MixinMinecraft].
 */
object BetterFPS : Module("BetterFPS", Category.RENDER, gameDetecting = false) {

    val fastLoad by boolean("FastLoad", true)

    var using = false
        private set

    override fun onEnable() {
        using = true
    }

    override fun onDisable() {
        using = false
    }
}
