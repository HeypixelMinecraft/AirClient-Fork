// skid OpenMyau
package net.ccbluex.liquidbounce.features.module.modules.combat

import net.ccbluex.liquidbounce.event.UpdateEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.minecraft.item.ItemSword

/**
 * AutoBlock - 受伤后按状态机延迟格挡：IDLE → WAITING → BLOCKING → IDLE
 */
object AutoBlock : Module("AutoBlock", Category.COMBAT) {

    private enum class State {
        IDLE,
        WAITING,
        BLOCKING
    }

    private val hurtDelay by int("HurtDelay", 18, 10..20)
    private val blockDuration by int("BlockDuration", 3, 1..10)

    private var blockState = BlockState.IDLE
    private var stateCounter = 0
    var isBlocking = false
        private set
    private var lastHurtTime = 0

    override fun onEnable() {
        resetState()
    }

    override fun onDisable() {
        if (isBlocking) {
            mc.gameSettings.keyBindUseItem.pressed = false
            isBlocking = false
        }
        resetState()
    }

    private fun resetState() {
        blockState = BlockState.IDLE
        stateCounter = 0
        isBlocking = false
        lastHurtTime = 0
    }

    private fun startBlock() {
        if (isBlocking) return
        mc.gameSettings.keyBindUseItem.pressed = true
        isBlocking = true
    }

    private fun stopBlock() {
        if (!isBlocking) return
        mc.gameSettings.keyBindUseItem.pressed = false
        isBlocking = false
    }

    private fun checkHurt(): Boolean {
        val currentHurtTime = mc.thePlayer?.hurtTime ?: 0
        val hurt = currentHurtTime > 0 && lastHurtTime == 0
        lastHurtTime = currentHurtTime
        return hurt
    }

    val onUpdate = handler<UpdateEvent> {
        val player = mc.thePlayer ?: return@handler
        if (mc.theWorld == null) return@handler
        if (mc.currentScreen != null) return@handler

        val holdingSword = player.heldItem?.item is ItemSword
        val hurt = checkHurt()

        if (hurt && holdingSword && blockState == BlockState.IDLE) {
            blockState = BlockState.WAITING
            stateCounter = hurtDelay
        }

        when (blockState) {
            BlockState.IDLE -> {
                if (isBlocking) {
                    stopBlock()
                }
            }
            BlockState.WAITING -> {
                stateCounter--
                if (stateCounter <= 0) {
                    if (holdingSword) {
                        blockState = BlockState.BLOCKING
                        stateCounter = blockDuration
                        startBlock()
                    } else {
                        blockState = BlockState.IDLE
                        stateCounter = 0
                    }
                }
            }
            BlockState.BLOCKING -> {
                stateCounter--
                if (stateCounter <= 0) {
                    stopBlock()
                    blockState = BlockState.IDLE
                    stateCounter = 0
                }
            }
        }
    }
}

private enum class BlockState {
    IDLE,
    WAITING,
    BLOCKING
}
