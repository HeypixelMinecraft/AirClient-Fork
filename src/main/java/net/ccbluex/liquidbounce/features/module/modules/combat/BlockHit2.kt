/*
 * Air Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 */
// Migrated from Leader-Lite BlockHit (renamed to BlockHit2 because BlockHit already exists)
package net.ccbluex.liquidbounce.features.module.modules.combat

import net.ccbluex.liquidbounce.event.AttackEvent
import net.ccbluex.liquidbounce.event.GameTickEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.utils.extensions.getDistanceToEntityBox
import net.ccbluex.liquidbounce.utils.timing.MSTimer
import net.minecraft.entity.EntityLivingBase
import net.minecraft.item.ItemSword
import kotlin.random.Random

/**
 * BlockHit2 - 迁移自 Leader-Lite BlockHit
 *
 * 提供两种模式:
 * - Helper: 玩家手动左键攻击时，临时停止格挡以发出攻击，再恢复格挡
 * - Auto: 攻击后自动格挡，含 4 个子模式 (Delay/HurtTime/Sag/Smart)
 *
 * 由于 AirClient 没有 KeyBindUtil/ItemUtil.isHoldingSword，使用 mc.gameSettings
 * 直接修改按键状态，并用 ItemSword 判断。
 */
object BlockHit2 : Module("BlockHit2", Category.COMBAT, defaultState = false) {

    private val mode by choices("Mode", arrayOf("Helper", "Auto"), "Helper")

    // Helper 模式
    private val stopTime by int("StopTicks", 2, 1..5) { mode == "Helper" }

    // Auto 模式通用
    private val autoBlockTime by choices(
        "AutoBlockTime", arrayOf("Delay", "HurtTime", "Sag", "Smart"), "Delay"
    ) { mode == "Auto" }
    private val autoMode by choices(
        "AutoMode", arrayOf("Spam", "Hold"), "Spam"
    ) { mode == "Auto" && autoBlockTime == "Delay" }
    private val chance by float("BlockHitChance", 50f, 0f..100f) { mode == "Auto" }
    private val smart by boolean("Smart", true) { mode == "Auto" }
    private val autoBlockRange by boolean("AutoBlockRange", true) { mode == "Auto" }
    private val range by float("Range", 3.0f, 1f..4f) { mode == "Auto" && autoBlockRange }

    // Delay 子模式
    private val blockDelay by int("BlockDelay", 100, 0..1000) { mode == "Auto" && autoBlockTime == "Delay" }
    private val holdTick by int(
        "HoldTicks", 2, 2..5
    ) { mode == "Auto" && autoMode == "Hold" && autoBlockTime == "Delay" }

    // HurtTime 子模式
    private val minHurtTime by int("MinHurtTime", 10, 1..10) { mode == "Auto" && autoBlockTime == "HurtTime" }
    private val maxHurtTime by int("MaxHurtTime", 10, 1..10) { mode == "Auto" && autoBlockTime == "HurtTime" }

    // Smart 子模式
    private val onFirstHit by boolean("OnFirstHit", true) { mode == "Auto" && autoBlockTime == "Smart" }
    private val smartBlockTick by int("SmartBlockTicks", 2, 1..5) { mode == "Auto" && autoBlockTime == "Smart" }
    private val releaseAfterHit by boolean("ReleaseAfterHit", true) { mode == "Auto" && autoBlockTime == "Smart" }
    private val smartBlockHurtTime by int(
        "SmartBlockHurtTime", 2, 0..10
    ) { mode == "Auto" && autoBlockTime == "Smart" }

    // 状态
    private var holdTicks = 0
    private var stopTick = 0
    private var startBlocking = false
    private var attacking = false
    private var attackTicks = 0
    private var sagTicks = 0
    private var canBlock = false
    private var getBlockTicks = 0
    private var target: EntityLivingBase? = null
    private val timer = MSTimer()

    private fun isHoldingSword(): Boolean = mc.thePlayer?.heldItem?.item is ItemSword

    private fun pressUseItem() {
        // 同时设置 pressed 和递增 pressTime，等效于 KeyBinding.setKeyBindState(keyCode, true)
        // 仅设置 pressed 不会触发 rightClickMouse()，因为游戏检查 isPressed() 即 pressTime
        mc.gameSettings.keyBindUseItem.pressed = true
        mc.gameSettings.keyBindUseItem.pressTime++
    }

    private fun releaseUseItem() {
        mc.gameSettings.keyBindUseItem.pressed = false
    }

    private fun releaseUseItemIfNeeded() {
        // 仅在玩家没有物理按住右键时才释放 pressed
        if (!mc.gameSettings.keyBindUseItem.isKeyDown) {
            mc.gameSettings.keyBindUseItem.pressed = false
        }
    }

    private fun pressUseItemOnce() {
        // 模拟一次右键点击：增加 pressTime 让游戏认为玩家按下了一次
        mc.gameSettings.keyBindUseItem.pressTime = mc.gameSettings.keyBindUseItem.pressTime + 1
    }

    private fun pressAttackOnce() {
        // 模拟一次左键点击：增加 pressTime 让游戏认为玩家按下了一次
        mc.gameSettings.keyBindAttack.pressTime = mc.gameSettings.keyBindAttack.pressTime + 1
    }

    private fun reset() {
        attacking = false
        canBlock = false
        releaseUseItem()
        holdTicks = 0
        sagTicks = 0
        getBlockTicks = 0
        timer.reset()
    }

    val onAttack = handler<AttackEvent> { event ->
        if (!state || !isHoldingSword()) return@handler
        attacking = true
        attackTicks = 0
        val entity = event.targetEntity
        if (entity is EntityLivingBase) {
            target = entity
        }
        if (autoBlockTime == "Smart") {
            val player = mc.thePlayer ?: return@handler
            if (player.hurtTime == 0 && onFirstHit) canBlock = true
        }
    }

    val onTick = handler<GameTickEvent> {
        if (!state) return@handler
        val player = mc.thePlayer ?: return@handler
        if (mc.theWorld == null) return@handler

        // Helper 模式: 玩家手动攻击时短暂停止格挡以发出攻击
        if (mode == "Helper") {
            if (mc.gameSettings.keyBindAttack.isKeyDown) {
                if (player.isBlocking && !startBlocking) {
                    startBlocking = true
                    stopTick = 0
                    releaseUseItem()
                }
            }
            if (startBlocking) {
                // 在 stopBlocking 期间，持续阻止原生右键恢复
                mc.gameSettings.keyBindUseItem.pressed = false
                stopTick++
                if (stopTick == 2) {
                    pressAttackOnce()
                }
                if (stopTick > stopTime) {
                    // 恢复格挡
                    pressUseItem()
                    startBlocking = false
                    stopTick = 0
                }
            }
            return@handler
        }

        // Auto 模式
        val currentTarget = target ?: return@handler

        if (attacking) attackTicks++
        if (attackTicks > 10) {
            reset()
            target = null
            return@handler
        }

        // 几率检查
        if (Random.nextFloat() * 100f > chance) {
            reset()
            return@handler
        }

        // 距离检查
        if (autoBlockRange && player.getDistanceToEntityBox(currentTarget) >= range) {
            reset()
            return@handler
        }

        // Smart 检查目标 hurtTime
        if (smart && currentTarget.hurtTime == 0) {
            reset()
            return@handler
        }

        if (!attacking || !isHoldingSword()) return@handler

        when (autoBlockTime) {
            "Delay" -> {
                if (timer.hasTimePassed(blockDelay.toLong())) {
                    if (autoMode == "Spam") {
                        // 使用 pressTime 触发一次右键，而不是 pressed（同 tick 内按下释放不会触发）
                        pressUseItemOnce()
                        timer.reset()
                        reset()
                    } else {
                        // Hold
                        startBlocking = true
                    }
                    if (startBlocking) {
                        pressUseItem()
                        holdTicks++
                    }
                    if (holdTicks > holdTick) {
                        releaseUseItemIfNeeded()
                        startBlocking = false
                        holdTicks = 0
                        timer.reset()
                    }
                }
            }

            "HurtTime" -> {
                if (player.hurtTime >= minHurtTime && player.hurtTime <= maxHurtTime) {
                    pressUseItem()
                    startBlocking = true
                } else if (startBlocking) {
                    releaseUseItemIfNeeded()
                    startBlocking = false
                }
            }

            "Sag" -> {
                if (sagTicks < 10) {
                    pressUseItem()
                    sagTicks++
                }
                if (sagTicks >= 10) {
                    releaseUseItemIfNeeded()
                    sagTicks = 0
                }
            }

            "Smart" -> {
                if (player.hurtTime == smartBlockHurtTime) {
                    canBlock = true
                }
                if (canBlock) {
                    getBlockTicks++
                    pressUseItem()
                }
                if (player.hurtTime == 9 && releaseAfterHit) {
                    canBlock = false
                    releaseUseItemIfNeeded()
                    getBlockTicks = 0
                }
                if (getBlockTicks > smartBlockTick) {
                    canBlock = false
                    releaseUseItemIfNeeded()
                    getBlockTicks = 0
                }
            }
        }
    }

    override fun onDisable() {
        reset()
        target = null
        stopTick = 0
        startBlocking = false
    }

    override val tag
        get() = mode
}
