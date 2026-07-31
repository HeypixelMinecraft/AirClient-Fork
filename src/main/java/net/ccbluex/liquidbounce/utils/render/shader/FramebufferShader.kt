/*
 * Air Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 */
package net.ccbluex.liquidbounce.utils.render.shader

import net.minecraft.client.gui.ScaledResolution
import net.minecraft.client.renderer.GlStateManager.*
import net.minecraft.client.renderer.RenderHelper
import net.minecraft.client.renderer.Tessellator
import net.minecraft.client.renderer.vertex.DefaultVertexFormats
import net.minecraft.client.shader.Framebuffer
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL20.glUseProgram
import java.awt.Color
import kotlin.math.roundToInt

/**
 * @author TheSlowly, Navex
 */
abstract class FramebufferShader(fragmentShader: String) : Shader(fragmentShader) {

    companion object {
        private var framebuffer: Framebuffer? = null
    }
    
    protected var red = 1f
    protected var green = 1f
    protected var blue = 1f
    protected var alpha = 1f
    protected var radius = 5
    protected var fade = 10
    protected var renderScale = 1f
    protected var targetAlpha = 0f
    
    private var entityShadows = false
    fun startDraw(partialTicks: Float, renderScale: Float) {
        this.renderScale = renderScale
        
        pushMatrix()
        enableAlpha()
        pushAttrib()
        
        framebuffer = setupFrameBuffer(framebuffer, renderScale)
        framebuffer!!.framebufferClear()
        framebuffer!!.bindFramebuffer(true)
        
        entityShadows = mc.gameSettings.entityShadows
        mc.gameSettings.entityShadows = false
        mc.entityRenderer.setupCameraTransform(partialTicks, 0)
    }

    fun stopDraw(color: Color, radius: Int, fade: Int, targetAlpha: Float) {
        mc.gameSettings.entityShadows = entityShadows
        glEnable(GL_BLEND)
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
        mc.framebuffer.bindFramebuffer(true)
        
        red = color.red / 255f
        green = color.green / 255f
        blue = color.blue / 255f
        alpha = color.alpha / 255f
        this.radius = radius
        this.fade = fade
        this.targetAlpha = targetAlpha
        
        mc.entityRenderer.disableLightmap()
        RenderHelper.disableStandardItemLighting()
        
        startShader()
        mc.entityRenderer.setupOverlayRendering()
        drawFramebuffer(framebuffer!!)
        stopShader()
        
        mc.entityRenderer.disableLightmap()
        
        popMatrix()
        popAttrib()
    }

    /**
     * @author TheSlowly, Navex
     *
     * 性能优化: 仅在尺寸变化时重建 Framebuffer, 复用现有 Framebuffer 避免每帧分配 GPU 内存。
     * 原实现每帧 deleteFramebuffer + new Framebuffer, 是 Glow/Outline 渲染的头号性能杀手。
     */
    fun setupFrameBuffer(frameBuffer: Framebuffer?, renderScale: Float): Framebuffer {
        val targetWidth = (mc.displayWidth * renderScale).roundToInt()
        val targetHeight = (mc.displayHeight * renderScale).roundToInt()

        // 尺寸未变化则直接复用, 避免每帧分配全屏纹理+深度缓冲
        if (frameBuffer != null
            && frameBuffer.framebufferWidth == targetWidth
            && frameBuffer.framebufferHeight == targetHeight) {
            return frameBuffer
        }

        frameBuffer?.deleteFramebuffer()
        return Framebuffer(targetWidth, targetHeight, true)
    }

    /**
     * @author Navex
     */
    fun drawFramebuffer(framebuffer: Framebuffer) {
        val scaledResolution = ScaledResolution(mc)
        val scaledWidth = scaledResolution.scaledWidth_double
        val scaledHeight = scaledResolution.scaledHeight_double

        val tessellator = Tessellator.getInstance()
        val buffer = tessellator.worldRenderer

        glBindTexture(GL_TEXTURE_2D, framebuffer.framebufferTexture)
        buffer.begin(GL_QUADS, DefaultVertexFormats.POSITION_TEX)
        buffer.pos(0.0, 0.0, 1.0).tex(0.0, 1.0).endVertex()
        buffer.pos(0.0, scaledHeight, 1.0).tex(0.0, 0.0).endVertex()
        buffer.pos(scaledWidth, scaledHeight, 1.0).tex(1.0, 0.0).endVertex()
        buffer.pos(scaledWidth, 0.0, 0.0).tex(1.0, 1.0).endVertex()
        tessellator.draw()
        glUseProgram(0)
    }

    /**
     * 释放 Shader 程序和 framebuffer 资源。
     * 调用后此 FramebufferShader 实例无法再使用。
     */
    override fun cleanup() {
        super.cleanup()
        framebuffer?.deleteFramebuffer()
        framebuffer = null
    }
}
