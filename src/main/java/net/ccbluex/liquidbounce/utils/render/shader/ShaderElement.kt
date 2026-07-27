// skid xylitol
package net.ccbluex.liquidbounce.utils.render.shader

import net.ccbluex.liquidbounce.utils.render.RenderUtils
import net.minecraft.client.Minecraft
import net.minecraft.client.shader.Framebuffer
import org.lwjgl.opengl.GL11
import java.util.ArrayList

/**
 * Shader 任务队列管理：用于延迟执行模糊/泛光任务。
 * 迁移自 xylitol 客户端的 ShaderElement。
 */
object ShaderElement {

    private val tasks = ArrayList<Runnable>()
    private val bloomTasks = ArrayList<Runnable>()

    @JvmStatic
    fun getTasks(): ArrayList<Runnable> = tasks

    @JvmStatic
    fun addBlurTask(context: Runnable) {
        tasks.add(context)
    }

    @JvmStatic
    fun getBloomTasks(): ArrayList<Runnable> = bloomTasks

    @JvmStatic
    fun addBloomTask(context: Runnable) {
        bloomTasks.add(context)
    }

    @JvmStatic
    fun bindTexture(texture: Int) {
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture)
    }

    /**
     * 创建或重新创建 Framebuffer。
     * 注意：与 KawaseBloom.createFrameBuffer 不同，此方法不会自动 clear/bind framebuffer，
     * 仅保证 framebuffer 尺寸与当前 display 一致。
     */
    @JvmStatic
    fun createFrameBuffer(framebuffer: Framebuffer?): Framebuffer {
        val mc = Minecraft.getMinecraft()
        if (framebuffer == null
            || framebuffer.framebufferWidth != mc.displayWidth
            || framebuffer.framebufferHeight != mc.displayHeight) {
            framebuffer?.deleteFramebuffer()
            return Framebuffer(mc.displayWidth, mc.displayHeight, true)
        }
        return framebuffer
    }

    /**
     * 添加一个矩形绘制任务到模糊任务队列。
     * 参数为右下角坐标 (x2, y2)，与 RenderUtils.drawRect 签名一致。
     */
    @JvmStatic
    fun blurArea(x: Double, y: Double, x2: Double, y2: Double) {
        addBlurTask(Runnable {
            RenderUtils.drawRect(x.toFloat(), y.toFloat(), x2.toFloat(), y2.toFloat(), -1)
        })
    }
}
