/*
 * Air Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 */
package net.ccbluex.liquidbounce.features.module.modules.client

import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.web.WebClickGuiServer
import java.awt.Desktop
import java.net.URI

object WebGUI : Module("WebGUI", Category.CLIENT, canBeEnabled = false) {
    private val url by text("URL", "http://localhost:${WebClickGuiServer.PORT}")

    override fun onEnable() {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI(url))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        state = false
    }
}
