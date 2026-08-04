// skid LiquidBounce
package net.ccbluex.liquidbounce.features.module.modules.combat

import net.ccbluex.liquidbounce.event.AttackEvent
import net.ccbluex.liquidbounce.event.EventState
import net.ccbluex.liquidbounce.event.MotionEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.features.module.modules.player.Gapple
import net.ccbluex.liquidbounce.utils.client.PacketUtils.sendPacket
import net.ccbluex.liquidbounce.utils.inventory.attackDamage
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemAxe
import net.minecraft.item.ItemSword
import net.minecraft.network.play.client.C0EPacketClickWindow
import java.util.LinkedList
import java.util.Queue

object ArmorBreaker2 : Module("ArmorBreaker2", Category.COMBAT, spacedName = "Armor Breaker 2") {

    val sendEvent by choices("SendEvent", arrayOf("PreAttack", "Attack", "PostMotion", "PreMotion"), "PreAttack")
    val clickMode by choices("ClickMode", arrayOf("WindowClick", "SendPacket"), "WindowClick")
    val hurtTime by int("MinHurtTime", 3, 0..9)
    val axe by boolean("AddAxeToArmorBreak", false)
    val switchBack by boolean("AutoSwitchBackToLowestDamageWeapon", false)
    val onGap by boolean("onGap", false)

    var lastSwordDamage = -1.0
    var switchEnd = false
    var attack = false

    fun getSwitchSlot(hurtTime: Int): Int {
        val swordDamages: Queue<Double> = LinkedList()
        val swordSlots: Queue<Int> = LinkedList()

        if (hurtTime <= this.hurtTime) {
            lastSwordDamage = -1.0
            switchEnd = false
        }

        if (switchEnd && switchBack) {
            lastSwordDamage = -1.0
        }

        for (slot in 9 until 45) {
            val stack = mc.thePlayer.openContainer.getSlot(slot).stack ?: continue
            val item = stack.item
            if (item !is ItemSword && (!(item is ItemAxe) || !axe)) {
                continue
            }

            val damage = stack.attackDamage
            if (damage > lastSwordDamage) {
                swordDamages.add(damage)
                swordSlots.add(slot)
            }
        }

        var minSwordDamage = 10000.0
        var minSwordSlot = -1

        while (swordDamages.isNotEmpty()) {
            val damage = swordDamages.poll() ?: break
            val slot = swordSlots.poll() ?: break
            if (damage < minSwordDamage) {
                minSwordDamage = damage
                minSwordSlot = slot
            }
        }

        if (minSwordDamage != 10000.0) {
            lastSwordDamage = minSwordDamage
            return minSwordSlot
        }

        switchEnd = true
        return -1
    }

    fun findBestSword(): Int {
        var maxDamage = -1.0
        var slot = -1

        for (index in 9 until 45) {
            val stack = mc.thePlayer.openContainer.getSlot(index).stack ?: continue
            if (stack.item !is ItemSword) {
                continue
            }

            val damage = stack.attackDamage
            if (damage > maxDamage) {
                maxDamage = damage
                slot = index
            }
        }

        return slot
    }

    fun doIt(targetEntity: EntityLivingBase) {
        if (Gapple.isEating && !onGap) {
            val slot = findBestSword()
            if (slot != -1) {
                sendPacket(
                    C0EPacketClickWindow(
                        mc.thePlayer.openContainer.windowId,
                        slot,
                        mc.thePlayer.inventory.currentItem,
                        2,
                        mc.thePlayer.inventory.getStackInSlot(mc.thePlayer.inventory.currentItem + 36),
                        mc.thePlayer.openContainer.getNextTransactionID(mc.thePlayer.inventory)
                    ),
                    false
                )
            }
            return
        }

        val slot = getSwitchSlot(targetEntity.hurtTime)
        if (slot == -1) {
            return
        }

        if (clickMode == "WindowClick") {
            mc.playerController.windowClick(
                mc.thePlayer.openContainer.windowId,
                slot,
                mc.thePlayer.inventory.currentItem,
                2,
                mc.thePlayer as EntityPlayer
            )
        } else {
            mc.netHandler.addToSendQueue(
                C0EPacketClickWindow(
                    mc.thePlayer.openContainer.windowId,
                    slot,
                    mc.thePlayer.inventory.currentItem,
                    2,
                    mc.thePlayer.inventory.getStackInSlot(mc.thePlayer.inventory.currentItem + 36),
                    mc.thePlayer.openContainer.getNextTransactionID(mc.thePlayer.inventory)
                )
            )
        }
    }

    val onMotion = handler<MotionEvent> { event ->
        if (event.eventState == EventState.POST && sendEvent == "PostMotion" && KillAura.target != null) {
            doIt(KillAura.target!!)
        }
        if (event.eventState == EventState.PRE && sendEvent == "PreMotion" && KillAura.target != null) {
            doIt(KillAura.target!!)
        }
    }

    val onAttack = handler<AttackEvent> { event ->
        if (sendEvent == "Attack" || sendEvent == "PreAttack") {
            doIt(event.targetEntity as EntityLivingBase)
        }
    }
}
