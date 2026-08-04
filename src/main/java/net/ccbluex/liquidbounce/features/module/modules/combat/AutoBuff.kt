/*
 * Air Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 */
package net.ccbluex.liquidbounce.features.module.modules.combat

import net.ccbluex.liquidbounce.event.Render2DEvent
import net.ccbluex.liquidbounce.event.RotationUpdateEvent
import net.ccbluex.liquidbounce.event.UpdateEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.features.module.modules.world.scaffolds.Scaffold
import net.ccbluex.liquidbounce.ui.font.Fonts
import net.ccbluex.liquidbounce.utils.extensions.tryJump
import net.ccbluex.liquidbounce.utils.inventory.InventoryUtils
import net.ccbluex.liquidbounce.utils.inventory.InventoryUtils.serverOpenInventory
import net.ccbluex.liquidbounce.utils.inventory.SilentHotbar
import net.ccbluex.liquidbounce.utils.inventory.inventorySlot
import net.ccbluex.liquidbounce.utils.inventory.isSplashPotion
import net.ccbluex.liquidbounce.utils.kotlin.RandomUtils.nextFloat
import net.ccbluex.liquidbounce.utils.movement.FallingPlayer
import net.ccbluex.liquidbounce.utils.rotation.Rotation
import net.ccbluex.liquidbounce.utils.rotation.RotationSettings
import net.ccbluex.liquidbounce.utils.rotation.RotationUtils.setTargetRotation
import net.ccbluex.liquidbounce.utils.timing.MSTimer
import net.minecraft.client.settings.GameSettings
import net.minecraft.client.settings.KeyBinding
import net.minecraft.item.ItemBlock
import net.minecraft.item.ItemFood
import net.minecraft.item.ItemPotion
import net.minecraft.item.ItemSword
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement
import net.minecraft.potion.Potion
import net.minecraft.util.BlockPos
import java.awt.Color

/**
 * Automatically uses buff potions (both splash and drinkable) from inventory.
 * Skips buffs the player already has.
 * Shows estimated time to apply all remaining buffs on HUD.
 *
 * Splash potions: switch slot → look down → right-click (throw)
 * Drinkable potions: switch slot → hold right-click for drink duration (~32 ticks)
 */
object AutoBuff : Module("AutoBuff", Category.COMBAT) {

    private val delay by int("Delay", 500, 100..2000)
    private val groundDistance by float("GroundDistance", 2F, 0F..5F)
    private val throwMode by choices("ThrowMode", arrayOf("Jump", "Port", "None"), "Jump") { true }
    private val showTimer by boolean("ShowTimer", true)
    private val hotbarOnly by boolean("HotbarOnly", false)

    // Which buff types to look for
    private val strengthPotion by boolean("Strength", true)
    private val speedPotion by boolean("Speed", true)
    private val fireResPotion by boolean("FireRes", true)
    private val jumpPotion by boolean("JumpBoost", true)
    private val regenPotion by boolean("Regen", false)
    private val resistancePotion by boolean("Resistance", false)
    private val nightVisionPotion by boolean("NightVision", false)
    private val waterBreathingPotion by boolean("WaterBreathing", false)

    private val options = RotationSettings(this).withoutKeepRotation().apply {
        resetTicksValue.excludeWithState()
        immediate = true
    }

    private val msTimer = MSTimer()

    // Drinking state: tracks how many ticks we've been holding right-click for a drinkable potion
    private var drinking = false
    private var drinkingTicks = 0
    // Potion drinking takes ~32 ticks (1.6 seconds at 20 tps)
    private val DRINK_DURATION_TICKS = 32

    // Splash throw state: simulates a single right-click press
    private var throwing = false
    // Phase 1 = aiming down (rotation syncing to server), Phase 2 = ready to throw
    private var throwPhase = 0

    // HUD display data
    private var remainingBuffs = listOf<String>()
    private var estimatedTimeMs = 0L

    override fun onDisable() {
        stopUsing()
        remainingBuffs = emptyList()
        estimatedTimeMs = 0L
    }

    private fun stopUsing() {
        if (drinking || throwing) {
            mc.gameSettings.keyBindUseItem.pressed = GameSettings.isKeyDown(mc.gameSettings.keyBindUseItem)
            drinking = false
            drinkingTicks = 0
            throwing = false
            throwPhase = 0
        }
    }

    /**
     * All buff potions we care about, paired with their setting provider and display name
     */
    private val buffPotions = listOf(
        Triple(Potion.damageBoost, { strengthPotion }, "Strength"),
        Triple(Potion.moveSpeed, { speedPotion }, "Speed"),
        Triple(Potion.fireResistance, { fireResPotion }, "FireRes"),
        Triple(Potion.jump, { jumpPotion }, "JumpBoost"),
        Triple(Potion.regeneration, { regenPotion }, "Regen"),
        Triple(Potion.resistance, { resistancePotion }, "Resistance"),
        Triple(Potion.nightVision, { nightVisionPotion }, "NightVision"),
        Triple(Potion.waterBreathing, { waterBreathingPotion }, "WaterBreathing")
    )

    /**
     * Check if a potion ID corresponds to a buff we care about
     */
    private fun findBuffForPotionId(potionId: Int): Triple<Potion, () -> Boolean, String>? {
        return buffPotions.find { it.first.id == potionId }
    }

    /**
     * Handle drinking/throwing progress each tick
     */
    val onUpdate = handler<UpdateEvent> {
        val player = mc.thePlayer ?: run { stopUsing(); return@handler }

        // Handle splash throw phase 2: rotation has been synced, now throw
        if (throwPhase == 1) {
            val stack = player.inventoryContainer?.getSlot(SilentHotbar.currentSlot + 36)?.stack
            if (stack != null && stack.item is ItemPotion && stack.isSplashPotion()) {
                // Rotation should now be synced to server, throw the potion
                player.sendQueue?.addToSendQueue(
                    C08PacketPlayerBlockPlacement(
                        BlockPos(-1, -1, -1), 255,
                        stack, 0f, 0f, 0f
                    )
                )
                mc.gameSettings.keyBindUseItem.pressed = true
                KeyBinding.onTick(mc.gameSettings.keyBindUseItem.keyCode)
            }
            throwPhase = 2 // Will release key next tick
            return@handler
        }

        // Handle splash throw phase 3: release right-click after throw
        if (throwPhase == 2) {
            mc.gameSettings.keyBindUseItem.pressed = GameSettings.isKeyDown(mc.gameSettings.keyBindUseItem)
            throwing = false
            throwPhase = 0
            msTimer.reset()
            return@handler
        }

        if (!drinking) return@handler

        drinkingTicks++

        // Check if player is still holding the potion
        val heldStack = player.inventoryContainer?.getSlot(SilentHotbar.currentSlot + 36)?.stack
        if (heldStack == null || heldStack.item !is ItemPotion || heldStack.isSplashPotion()) {
            // Lost the potion (slot changed, item consumed, etc.)
            stopUsing()
            return@handler
        }

        // Check if drinking completed
        if (drinkingTicks >= DRINK_DURATION_TICKS) {
            stopUsing()
            msTimer.reset()
        }
    }

    val onRotationUpdate = handler<RotationUpdateEvent> {
        // Don't search for new potions while drinking or throwing
        if (drinking || throwing) return@handler

        // Scaffold 启用时停止工作
        if (Scaffold.state) return@handler

        if (!msTimer.hasTimePassed(delay) || mc.playerController.isInCreativeMode)
            return@handler

        val player = mc.thePlayer ?: return@handler

        // Recalculate remaining buffs for HUD display
        calculateRemainingBuffs()

        // Find buff potion in hotbar
        val potionInHotbar = findBuffPotion(36, 44)

        if (potionInHotbar != null) {
            val hotbarIndex = potionInHotbar - 36
            val stack = player.inventoryContainer?.getSlot(potionInHotbar)?.stack
            if (stack == null || stack.item !is ItemPotion) return@handler
            val isSplash = stack.isSplashPotion()

            if (isSplash) {
                // === Splash potion: switch slot → look down → (next tick) throw ===

                if (player.onGround && throwMode != "None") {
                    when (throwMode.lowercase()) {
                        "jump" -> player.tryJump()
                        "port" -> player.moveEntity(0.0, 0.42, 0.0)
                    }
                }

                // Void check: only when player is in the air (on ground = standing on something)
                if (!player.onGround) {
                    val fallingPlayer = FallingPlayer(player)
                    val collisionBlock = fallingPlayer.findCollision(20)?.pos
                    if (collisionBlock == null || player.posY - collisionBlock.y - 1 > groundDistance)
                        return@handler
                }

                // Switch to potion slot silently (immediate = true to sync with server)
                SilentHotbar.selectSlotSilently(
                    this,
                    hotbarIndex,
                    ticksUntilReset = 3,
                    immediate = true,
                    render = false,
                    resetManually = true
                )

                // Set rotation to look straight down for splash potions
                // Phase 1: just aim down, throw happens next tick after rotation syncs to server
                setTargetRotation(
                    Rotation(player.rotationYaw, 90F).fixedSensitivity(),
                    options
                )

                throwing = true
                throwPhase = 1

                return@handler
            } else {
                // === Drinkable potion: switch slot → hold right-click ===

                // Switch to potion slot silently
                SilentHotbar.selectSlotSilently(
                    this,
                    hotbarIndex,
                    ticksUntilReset = DRINK_DURATION_TICKS + 5,
                    immediate = true,
                    render = false,
                    resetManually = true
                )

                // Start holding right-click to drink
                mc.gameSettings.keyBindUseItem.pressed = true
                KeyBinding.onTick(mc.gameSettings.keyBindUseItem.keyCode)
                drinking = true
                drinkingTicks = 0
                return@handler
            }
        }

        // Find buff potion in inventory (9-35) and move to hotbar
        if (hotbarOnly) return@handler
        val potionInInventory = findBuffPotion(9, 35) ?: return@handler

        val targetSlot = if (InventoryUtils.hasSpaceInHotbar()) {
            // Has empty slot: shift-click into it
            potionInInventory
        } else {
            // No empty slot: swap with a non-essential item in hotbar
            val swapSlot = findNonEssentialHotbarSlot() ?: return@handler
            potionInInventory to (swapSlot - 36)
        }

        // 使用 serverOpenInventory 模拟背包打开（与 InventoryManager 共享状态，避免冲突）
        val wasInventoryOpen = serverOpenInventory
        if (!wasInventoryOpen) {
            serverOpenInventory = true
        }

        if (targetSlot is Int) {
            mc.playerController.windowClick(0, targetSlot, 0, 1, player)
        } else {
            val pair = targetSlot as Pair<Int, Int>
            mc.playerController.windowClick(0, pair.first, pair.second, 2, player)
        }

        // 操作完成后关闭模拟背包（仅当是我们打开的时才关闭）
        if (!wasInventoryOpen) {
            serverOpenInventory = false
        }

        msTimer.reset()
    }

    /**
     * Find a non-essential hotbar slot to swap with a potion.
     * Priority: blocks > food > other items (never swap swords or potions)
     * Returns inventory slot (36-44) or null if all slots are essential.
     */
    private fun findNonEssentialHotbarSlot(): Int? {
        val player = mc.thePlayer ?: return null

        val candidates = mutableListOf<Pair<Int, Int>>()

        for (i in 36..44) {
            val stack = player.inventorySlot(i).stack ?: continue
            val item = stack.item

            val priority = when {
                item is ItemBlock -> 1
                item is ItemFood -> 2
                item !is ItemSword && item !is ItemPotion -> 3
                else -> -1
            }

            if (priority > 0) {
                candidates.add(i to priority)
            }
        }

        return candidates.minByOrNull { it.second }?.first
    }

    /**
     * Find a potion that provides a buff the player doesn't already have.
     * Searches both splash and drinkable potions.
     */
    private fun findBuffPotion(startSlot: Int, endSlot: Int): Int? {
        val player = mc.thePlayer ?: return null

        for (i in startSlot..endSlot) {
            val stack = player.inventorySlot(i).stack

            if (stack == null || stack.item !is ItemPotion)
                continue

            val itemPotion = stack.item as ItemPotion

            for (potionEffect in itemPotion.getEffects(stack)) {
                val potionId = potionEffect.potionID
                val buffInfo = findBuffForPotionId(potionId) ?: continue

                if (!buffInfo.second.invoke()) continue

                val potion = buffInfo.first

                if (player.isPotionActive(potion)) {
                    val activeEffect = player.getActivePotionEffect(potion)
                    if (activeEffect != null && activeEffect.duration > 60) continue
                }

                return i
            }
        }

        return null
    }

    /**
     * Calculate remaining buffs and estimated time to apply them all
     */
    private fun calculateRemainingBuffs() {
        val player = mc.thePlayer ?: return
        val buffs = mutableListOf<String>()
        var count = 0

        for (i in 9..44) {
            val stack = player.inventorySlot(i).stack
            if (stack == null || stack.item !is ItemPotion) continue

            val itemPotion = stack.item as ItemPotion

            for (potionEffect in itemPotion.getEffects(stack)) {
                val potionId = potionEffect.potionID
                val buffInfo = findBuffForPotionId(potionId) ?: continue

                if (!buffInfo.second.invoke()) continue

                val potion = buffInfo.first

                val needsBuff = if (player.isPotionActive(potion)) {
                    val activeEffect = player.getActivePotionEffect(potion)
                    activeEffect == null || activeEffect.duration <= 60
                } else true

                if (needsBuff) {
                    val potionName = buffInfo.third
                    if (potionName !in buffs) {
                        buffs.add(potionName)
                    }
                    count++
                }
            }
        }

        remainingBuffs = buffs
        estimatedTimeMs = count.toLong() * (delay + 500)
    }

    val onRender2D = handler<Render2DEvent> {
        if (!showTimer || remainingBuffs.isEmpty()) return@handler

        val font = Fonts.fontRegular35
        val x = 4f
        var y = 4f

        val title = if (drinking) "AutoBuff §7(drinking ${drinkingTicks}/${DRINK_DURATION_TICKS})" else if (throwing) "AutoBuff §7(throwing)" else "AutoBuff"
        font.drawString(title, x, y, Color(255, 255, 255).rgb, true)
        y += font.FONT_HEIGHT + 2f

        for (buff in remainingBuffs) {
            font.drawString("§7- $buff", x + 4f, y, Color(200, 200, 200).rgb)
            y += font.FONT_HEIGHT
        }

        y += 2f
        val seconds = estimatedTimeMs / 1000.0
        val timeStr = String.format("%.1fs", seconds)
        font.drawString("§7Est: $timeStr", x + 4f, y, Color(180, 180, 180).rgb)
    }

    override val tag
        get() = if (drinking) "drinking" else if (throwing) "throwing" else if (remainingBuffs.isNotEmpty()) "${remainingBuffs.size}buffs" else null
}
