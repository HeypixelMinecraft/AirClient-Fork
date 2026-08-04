// skid LiquidBounce
package net.ccbluex.liquidbounce.features.module.modules.combat

import net.ccbluex.liquidbounce.event.PacketEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.features.module.modules.player.Gapple
import net.ccbluex.liquidbounce.utils.client.PacketUtils.sendPacket
import net.minecraft.enchantment.Enchantment
import net.minecraft.enchantment.EnchantmentHelper
import net.minecraft.item.ItemStack
import net.minecraft.network.play.client.C02PacketUseEntity
import net.minecraft.network.play.client.C09PacketHeldItemChange

object MoreDamage : Module("MoreDamage", Category.COMBAT) {

    val minSharp by int("MinSharp", 1, 1..5)
    val onGap by boolean("OnGap", false)

    fun findItem(start: Int, end: Int): Int {
        var slot = start
        while (slot <= end) {
            val stack: ItemStack? = mc.thePlayer.openContainer.getSlot(slot).stack
            if (stack != null && EnchantmentHelper.getEnchantmentLevel(Enchantment.sharpness.effectId, stack) >= minSharp) {
                return slot
            }
            slot++
        }
        return -1
    }

    val onPacket = handler<PacketEvent> { event ->
        if (!onGap && Gapple.isEating) {
            return@handler
        }

        val packet = event.packet
        if (packet is C02PacketUseEntity && packet.action == C02PacketUseEntity.Action.ATTACK) {
            val slot = findItem(36, 44) - 36
            if (slot >= 0) {
                event.cancelEvent()
                sendPacket(C09PacketHeldItemChange(slot), false)
                sendPacket(packet, false)
                sendPacket(C09PacketHeldItemChange(mc.thePlayer.inventory.currentItem), false)
            }
        }
    }
}
