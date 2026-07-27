// skid xylitol
package net.ccbluex.liquidbounce.utils.render.shader

import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.shader.Framebuffer
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL13
import org.lwjgl.opengl.GL14

/**
 * Kawase Bloom 实现
 * 迁移自 xylitol 客户端
 */
object KawaseBloom {
    private val mc = Minecraft.getMinecraft()

    private var kawaseDown: ShaderUtil? = null
    private var kawaseUp: ShaderUtil? = null

    @JvmField
    var framebuffer = Framebuffer(1, 1, false)
    @JvmField
    var stencilFramebuffer = Framebuffer(1, 1, false)

    private var currentIterations = 0
    private val framebufferList = mutableListOf<Framebuffer>()

    private fun initShaders() {
        if (kawaseDown == null) {
            try { kawaseDown = ShaderUtil("kawaseDownBloom") }
            catch (e: Exception) { e.printStackTrace() }
        }
        if (kawaseUp == null) {
            try { kawaseUp = ShaderUtil("kawaseUpBloom") }
            catch (e: Exception) { e.printStackTrace() }
        }
    }
    
    private fun initFramebuffers(iterations: Float) {
        // 清理旧的 framebuffer
        framebufferList.forEach { it.deleteFramebuffer() }
        framebufferList.clear()
        
        // 创建主 framebuffer
        framebuffer = createFrameBuffer(framebuffer)
        framebufferList.add(framebuffer)
        
        // 创建多级 framebuffer
        for (i in 1..iterations.toInt()) {
            val width = (mc.displayWidth / Math.pow(2.0, i.toDouble())).toInt()
            val height = (mc.displayHeight / Math.pow(2.0, i.toDouble())).toInt()
            val currentBuffer = Framebuffer(width, height, false)
            currentBuffer.setFramebufferFilter(GL11.GL_LINEAR)
            
            GlStateManager.bindTexture(currentBuffer.framebufferTexture)
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL14.GL_MIRRORED_REPEAT)
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL14.GL_MIRRORED_REPEAT)
            GlStateManager.bindTexture(0)
            
            framebufferList.add(currentBuffer)
        }
    }
    
    /**
     * 渲染阴影效果
     */
    fun shadow(drawMod: Runnable, iterations: Int, offset: Int) {
        stencilFramebuffer = createFrameBuffer(stencilFramebuffer)
        stencilFramebuffer.framebufferClear()
        stencilFramebuffer.bindFramebuffer(false)
        drawMod.run()
        stencilFramebuffer.unbindFramebuffer()
        renderBlur(stencilFramebuffer.framebufferTexture, iterations, offset)
    }
    
    fun renderBlur(framebufferTexture: Int, iterations: Int, offset: Int) {
        initShaders()
        val down = kawaseDown
        val up = kawaseUp
        if (down == null || up == null) {
            System.err.println("KawaseBloom shaders not initialized, skipping bloom render")
            return
        }
        if (currentIterations != iterations || framebuffer.framebufferWidth != mc.displayWidth || framebuffer.framebufferHeight != mc.displayHeight) {
            initFramebuffers(iterations.toFloat())
            currentIterations = iterations
        }

        setAlphaLimit(0f)
        GlStateManager.enableBlend()
        GlStateManager.blendFunc(GL11.GL_ONE, GL11.GL_ONE)

        GL11.glClearColor(0f, 0f, 0f, 0f)

        val baseOffset = offset.toFloat()
        // 第一次 downsample
        var currentOffset = baseOffset
        renderFBO(framebufferList[1], framebufferTexture, down, currentOffset)

        // Downsample 阶段：每级 offset 按 1.5^i 递减，与 NekoBounce 实现一致
        for (i in 1 until iterations) {
            currentOffset = baseOffset / Math.pow(1.5, i.toDouble()).toFloat()
            renderFBO(framebufferList[i + 1], framebufferList[i].framebufferTexture, down, currentOffset)
        }

        // Upsample 阶段
        for (i in iterations downTo 2) {
            currentOffset = baseOffset / Math.pow(1.5, (i - 1).toDouble()).toFloat()
            renderFBO(framebufferList[i - 1], framebufferList[i].framebufferTexture, up, currentOffset)
        }

        // 最后一次 upsample，带 stencil 检查
        val lastBuffer = framebufferList[0]
        lastBuffer.framebufferClear()
        lastBuffer.bindFramebuffer(false)
        up.init()
        up.setUniformf("offset", baseOffset, baseOffset)
        up.setUniformi("inTexture", 0)
        up.setUniformi("check", 1)
        up.setUniformi("textureToCheck", 16)
        up.setUniformf("halfpixel", 1.0f / lastBuffer.framebufferWidth, 1.0f / lastBuffer.framebufferHeight)
        up.setUniformf("iResolution", lastBuffer.framebufferWidth.toFloat(), lastBuffer.framebufferHeight.toFloat())

        GlStateManager.setActiveTexture(GL13.GL_TEXTURE16)
        bindTexture(framebufferTexture)
        GlStateManager.setActiveTexture(GL13.GL_TEXTURE0)
        bindTexture(framebufferList[1].framebufferTexture)
        ShaderUtil.drawQuads()
        up.unload()
        
        // 渲染到主 framebuffer
        GlStateManager.clearColor(0f, 0f, 0f, 0f)
        mc.framebuffer.bindFramebuffer(false)
        bindTexture(framebufferList[0].framebufferTexture)
        setAlphaLimit(0f)
        startBlend()
        ShaderUtil.drawQuads()
        GlStateManager.bindTexture(0)
        setAlphaLimit(0f)
        endBlend()
        GlStateManager.disableBlend()
    }
    
    private fun renderFBO(framebuffer: Framebuffer, framebufferTexture: Int, shader: ShaderUtil, offset: Float) {
        framebuffer.framebufferClear()
        framebuffer.bindFramebuffer(false)
        shader.init()
        bindTexture(framebufferTexture)
        shader.setUniformf("offset", offset, offset)
        shader.setUniformi("inTexture", 0)
        shader.setUniformi("check", 0)
        shader.setUniformf("halfpixel", 1.0f / framebuffer.framebufferWidth, 1.0f / framebuffer.framebufferHeight)
        shader.setUniformf("iResolution", framebuffer.framebufferWidth.toFloat(), framebuffer.framebufferHeight.toFloat())
        ShaderUtil.drawQuads()
        shader.unload()
    }
    
    /**
     * 创建或重新创建 Framebuffer
     */
    fun createFrameBuffer(framebuffer: Framebuffer?): Framebuffer {
        var fb = framebuffer
        if (fb == null || fb.framebufferWidth != mc.displayWidth || fb.framebufferHeight != mc.displayHeight) {
            fb?.deleteFramebuffer()
            fb = Framebuffer(mc.displayWidth, mc.displayHeight, false)
        }
        fb.framebufferClear()
        fb.bindFramebuffer(false)
        return fb
    }
    
    /**
     * 绑定纹理
     */
    fun bindTexture(texture: Int) {
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture)
    }
    
    /**
     * 设置 Alpha 限制
     */
    fun setAlphaLimit(limit: Float) {
        GlStateManager.enableAlpha()
        GlStateManager.alphaFunc(GL11.GL_GREATER, limit)
    }
    
    /**
     * 开启混合
     */
    fun startBlend() {
        GlStateManager.enableBlend()
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA)
    }
    
    /**
     * 关闭混合
     */
    fun endBlend() {
        GlStateManager.disableBlend()
    }
}
