/*
 * Air Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 */
// skid some
package net.ccbluex.liquidbounce.features.module.modules.combat

import net.ccbluex.liquidbounce.event.AttackEvent
import net.ccbluex.liquidbounce.event.GameTickEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.minecraft.item.ItemSword

/**
 * BlockHit - After attacking a target, automatically block with sword for a few ticks.
 * Only activates if the target is within range and the held item is a sword.
 * Blocks by simulating right-click (keyBindUseItem).
 */
object BlockHit : Module("BlockHit", Category.COMBAT) {

    private val range by float("Range", 3F, 1F..6F)
    private val blockTicks by int("BlockTicks", 5, 1..20)
    private val requireRightClick by boolean("RequireRightClick", false)
    private val alwaysBlockAnim by boolean("AlwaysBlockAnim", false)

    private var blockCounter = 0
    var isBlocking = false
        private set

    override fun onEnable() {
        blockCounter = 0
        isBlocking = false
    }

    override fun onDisable() {
        stopBlocking()
        blockCounter = 0
    }

    private fun startBlocking() {
        if (isBlocking) return
        mc.gameSettings.keyBindUseItem.pressed = true
        isBlocking = true
    }

    private fun stopBlocking() {
        if (!isBlocking) return
        mc.gameSettings.keyBindUseItem.pressed = false
        isBlocking = false
    }

    val onAttack = handler<AttackEvent> { event ->
        val player = mc.thePlayer ?: return@handler
        val target = event.targetEntity ?: return@handler

        // Check if holding a sword
        if (player.heldItem?.item !is ItemSword) return@handler

        // Check if target is within range
        val distSq = player.getDistanceSqToEntity(target)
        if (distSq > (range * range).toDouble()) return@handler

        // Check requireRightClick setting
        if (requireRightClick && !mc.gameSettings.keyBindUseItem.isKeyDown) return@handler

        // Start blocking
        blockCounter = blockTicks
        startBlocking()
    }

    val onGameTick = handler<GameTickEvent> {
        val player = mc.thePlayer ?: return@handler

        if (mc.currentScreen != null) {
            stopBlocking()
            blockCounter = 0
            return@handler
        }

        if (blockCounter > 0) {
            // Check still holding sword
            val holdingSword = player.heldItem?.item is ItemSword

            if (!holdingSword) {
                stopBlocking()
                blockCounter = 0
                return@handler
            }

            // Keep blocking - ensure right click is pressed
            if (!isBlocking) {
                startBlocking()
            }

            // If alwaysBlockAnim, re-press the key each tick to sustain animation
            if (alwaysBlockAnim) {
                mc.gameSettings.keyBindUseItem.pressed = true
            }

            blockCounter--
        } else {
            // Done blocking
            stopBlocking()
        }
    }

    override val tag
        get() = if (isBlocking) "${blockCounter}t" else null
}
