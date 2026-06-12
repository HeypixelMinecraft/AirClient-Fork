package net.ccbluex.liquidbounce.features.module.modules.movement

import net.ccbluex.liquidbounce.event.PacketEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.minecraft.network.play.client.C03PacketPlayer

object NoC03 : Module("NoC03", Category.MOVEMENT) {
    private var wasEnabled = false

    val onPacket = handler<PacketEvent> { event ->
        if (event.packet is C03PacketPlayer) {
            event.cancelEvent()
        }
    }

    override fun onEnable() {
        wasEnabled = true
        super.onEnable()
    }

    override fun onDisable() {
        wasEnabled = false
        super.onDisable()
    }
}
