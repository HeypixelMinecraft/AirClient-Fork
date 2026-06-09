/*
 * AirClient Hacked Client
 * A free open source mixin-based injection hacked client built on Liquidbounce legacy codebase.
 * https://github.com/lmx0721/AirClient
 */
package net.ccbluex.liquidbounce.ui.client.clickgui.neverlose

import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import org.lwjgl.input.Keyboard

object NeverloseClickGui : Module("NeverloseClickGui", Category.CLIENT, Keyboard.KEY_NONE, canBeEnabled = false, gameDetecting = false) {

    override fun onEnable() {
        mc.displayGuiScreen(NeverloseScreen())
        Keyboard.enableRepeatEvents(true)
    }
}