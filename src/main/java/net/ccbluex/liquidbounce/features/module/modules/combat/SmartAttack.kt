/*
 * Air Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 */
// Migrated from Leader-Lite SmartAttack
package net.ccbluex.liquidbounce.features.module.modules.combat

import net.ccbluex.liquidbounce.event.AttackEvent
import net.ccbluex.liquidbounce.event.GameTickEvent
import net.ccbluex.liquidbounce.event.UpdateEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.minecraft.entity.EntityLivingBase

/**
 * SmartAttack - 智能攻击控制
 *
 * 迁移自 Leader-Lite，根据自身/目标状态决定是否取消点击：
 * - 玩家在地面/上升时取消攻击
 * - 目标 hurtTime 过低时不取消
 * - 玩家被击中超过阈值时不取消
 *
 * 注意：原版使用 LeftClickMouseEvent 取消，AirClient 无此事件，
 * 改为在 GameTick 中将 keyBindAttack.pressTime 置零以消耗点击。
 */
object SmartAttack : Module("SmartAttack", Category.COMBAT, defaultState = false) {

    private val cancelGroundAttack by boolean("CancelGroundAttack", true)
    private val cancelRisingAttack by boolean("CancelRisingAttack", true)
    private val stopHurtTime by int("StopHurtTime", 7, 0..9)

    val onKillAura by boolean("OnKillAura", true)
    val cancelAuraBlocking by boolean("CancelAuraBlocking", true) { onKillAura }

    @Volatile
    var shouldCancel: Boolean = false
        private set

    private var target: EntityLivingBase? = null

    val onAttack = handler<AttackEvent> { event ->
        if (!state) return@handler
        val entity = event.targetEntity
        if (entity is EntityLivingBase) {
            target = entity
        }
    }

    val onUpdate = handler<UpdateEvent> {
        if (!state) return@handler
        val player = mc.thePlayer ?: return@handler
        val currentTarget = target

        if (currentTarget == null || player.getDistanceToEntity(currentTarget) > 6f) {
            target = null
            shouldCancel = false
            return@handler
        }

        if (player.onGround && cancelGroundAttack) shouldCancel = true
        if (player.motionY >= 0.0 && cancelRisingAttack) shouldCancel = true
        if (currentTarget.hurtTime <= 2) shouldCancel = false
        if (currentTarget.isBurning) shouldCancel = false
        if (player.hurtTime > stopHurtTime) shouldCancel = false
    }

    val onTick = handler<GameTickEvent> {
        if (!state) return@handler
        if (shouldCancel) {
            // 消耗左键点击事件，等效于取消 LeftClickMouseEvent
            mc.gameSettings.keyBindAttack.pressTime = 0
        }
    }

    override fun onDisable() {
        target = null
        shouldCancel = false
    }
}
