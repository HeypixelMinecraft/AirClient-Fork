// skid OpenMyau
package net.ccbluex.liquidbounce.features.module.modules.render

import net.ccbluex.liquidbounce.event.AttackEvent
import net.ccbluex.liquidbounce.event.Render2DEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.ui.font.Fonts
import net.ccbluex.liquidbounce.utils.render.RenderUtils
import net.minecraft.client.gui.ScaledResolution
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.player.EntityPlayer
import org.lwjgl.opengl.GL11
import java.awt.Color

object KillEffect : Module("KillEffect", Category.RENDER) {

    private val style by choices("Style", arrayOf("Text", "Particles", "Flash", "Combo"), "Text")
    private val effectColor by color("Color", Color(255, 215, 0))
    private val duration by float("Duration", 1000f, 300f..3000f)
    private val scale by float("Scale", 2.0f, 0.5f..4f)
    private val sound by boolean("Sound", true)

    private val anims = mutableListOf<KillAnim>()
    private var killCombo = 0
    private var lastKillTime = 0L
    private var lastEffectTime = 0L
    private val MAX_ANIMS = 16

    // 帧间血量追踪
    private val healthMap = mutableMapOf<Int, Float>()
    private val deadEntities = mutableSetOf<Int>()
    // 玩家最近攻击过的实体ID（3秒内）
    private val attackedEntities = mutableMapOf<Int, Long>()

    val onAttack = handler<AttackEvent> { event ->
        val target = event.targetEntity ?: return@handler
        if (target is EntityLivingBase) {
            attackedEntities[target.entityId] = System.currentTimeMillis()
        }
    }

    val onRender2D = handler<Render2DEvent> {
        if (mc.theWorld == null || mc.thePlayer == null) return@handler

        val now = System.currentTimeMillis()

        // 清理过期的攻击记录（3秒）
        attackedEntities.entries.removeIf { e -> now - e.value > 3000 }

        // 1. 帧间血量追踪 - 检测击杀
        for (entity in mc.theWorld.loadedEntityList) {
            if (entity !is EntityLivingBase) continue
            if (entity == mc.thePlayer) continue

            val id = entity.entityId
            val currentHealth = entity.health
            val lastHealth = healthMap.getOrDefault(id, currentHealth)

            // 检测死亡：上一帧还有血，这一帧没血了（或实体被移除）
            if (lastHealth > 0 && (currentHealth <= 0 || entity.isDead)) {
                if (!deadEntities.contains(id)) {
                    if (attackedEntities.containsKey(id)) {
                        onKill(entity)
                    }
                    deadEntities.add(id)
                }
            }

            healthMap[id] = currentHealth
        }

        // 清理已移除的实体
        healthMap.entries.removeIf { e -> mc.theWorld.getEntityByID(e.key) == null }
        deadEntities.removeIf { id -> mc.theWorld.getEntityByID(id) == null }

        // 2. 渲染动画
        if (anims.isEmpty()) return@handler

        GL11.glPushMatrix()
        GL11.glEnable(GL11.GL_BLEND)
        net.minecraft.client.renderer.GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA)

        val iter = anims.iterator()
        while (iter.hasNext()) {
            val anim = iter.next()
            val elapsed = System.currentTimeMillis() - anim.time
            if (elapsed > duration) {
                iter.remove()
                continue
            }

            val progress = elapsed / duration
            val alpha = 1f - progress * progress * progress

            when (style) {
                "Text" -> drawTextEffect(anim, progress, alpha)
                "Particles" -> drawParticleEffect(anim, progress, alpha)
                "Flash" -> drawFlashEffect(anim, progress, alpha)
                "Combo" -> drawComboEffect(anim, progress, alpha)
            }
        }

        GL11.glDisable(GL11.GL_BLEND)
        GL11.glPopMatrix()
    }

    private fun onKill(target: EntityLivingBase) {
        val now = System.currentTimeMillis()
        // 1s 冷却（不可开关），1s 内不会重复触发
        if (now - lastEffectTime < 1000) return
        lastEffectTime = now

        if (now - lastKillTime < 3000) {
            killCombo++
        } else {
            killCombo = 1
        }
        lastKillTime = now

        val sr = ScaledResolution(mc)
        if (anims.size < MAX_ANIMS) {
            anims.add(KillAnim(sr.scaledWidth / 2f, sr.scaledHeight / 2f, killCombo, now))
        }

        if (sound) {
            mc.thePlayer.playSound("random.levelup", 0.5f, 1.0f)
        }
    }

    private fun drawTextEffect(anim: KillAnim, progress: Float, alpha: Float) {
        val c = Color(effectColor.rgb, true)
        val textColor = Color(c.red, c.green, c.blue, (c.alpha * alpha).toInt()).rgb

        val yOffset = -progress * 50f
        val currentScale = scale * (1f + Math.sin(progress * Math.PI).toFloat() * 0.3f)

        val text = "KILL!"
        val font = Fonts.fontBold180
        val textWidth = font.getStringWidth(text)

        GL11.glPushMatrix()
        GL11.glTranslatef(anim.x, anim.y + yOffset, 0f)
        GL11.glScalef(currentScale, currentScale, 1f)
        font.drawString(text, (-textWidth / 2f).toInt(), (-font.FONT_HEIGHT / 2f).toInt(), textColor)
        GL11.glPopMatrix()
    }

    private fun drawParticleEffect(anim: KillAnim, progress: Float, alpha: Float) {
        val c = Color(effectColor.rgb, true)
        val count = 20
        for (i in 0 until count) {
            val angle = (Math.PI * 2 / count) * i + progress * Math.PI
            val dist = progress * 100f
            val px = (anim.x + Math.cos(angle) * dist).toFloat()
            val py = (anim.y + Math.sin(angle) * dist).toFloat()
            val size = (1f - progress) * 4f

            val pColor = Color(c.red, c.green, c.blue, (c.alpha * alpha * (1f - progress)).toInt()).rgb
            RenderUtils.drawRect(px - size, py - size, px + size, py + size, pColor)
        }
    }

    private fun drawFlashEffect(anim: KillAnim, progress: Float, alpha: Float) {
        val flashAlpha = Math.sin(progress * Math.PI).toFloat() * 0.3f * alpha
        val sr = ScaledResolution(mc)
        val c = Color(effectColor.rgb, true)
        RenderUtils.drawRect(0f, 0f, sr.scaledWidth.toFloat(), sr.scaledHeight.toFloat(),
            Color(c.red, c.green, c.blue, (c.alpha * flashAlpha).toInt()).rgb)
    }

    private fun drawComboEffect(anim: KillAnim, progress: Float, alpha: Float) {
        val c = Color(effectColor.rgb, true)
        val textColor = Color(c.red, c.green, c.blue, (c.alpha * alpha).toInt()).rgb

        val yOffset = -progress * 30f
        val currentScale = scale * (1f + Math.sin(progress * Math.PI).toFloat() * 0.5f)

        val comboText = "${anim.combo}x COMBO!"
        val font = Fonts.fontBold180
        val textWidth = font.getStringWidth(comboText)

        GL11.glPushMatrix()
        GL11.glTranslatef(anim.x, anim.y + yOffset, 0f)
        GL11.glScalef(currentScale, currentScale, 1f)
        font.drawString(comboText, (-textWidth / 2f).toInt(), (-font.FONT_HEIGHT / 2f).toInt(), textColor)
        GL11.glPopMatrix()
    }

    private class KillAnim(val x: Float, val y: Float, val combo: Int, val time: Long)

    override fun onDisable() {
        anims.clear()
        healthMap.clear()
        deadEntities.clear()
        attackedEntities.clear()
        killCombo = 0
        lastKillTime = 0
        lastEffectTime = 0
    }
}
