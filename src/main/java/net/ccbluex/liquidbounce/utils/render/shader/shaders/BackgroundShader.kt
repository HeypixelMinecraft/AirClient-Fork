/*
 * Air Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 */
package net.ccbluex.liquidbounce.utils.render.shader.shaders

import net.ccbluex.liquidbounce.utils.render.RenderUtils.deltaTime
import net.ccbluex.liquidbounce.utils.render.shader.Shader
import org.lwjgl.opengl.Display
import org.lwjgl.opengl.GL20.*
import java.io.File
import java.io.IOException
import net.minecraft.util.ResourceLocation

class BackgroundShader : Shader {

    @Throws(IOException::class)
    constructor(fragmentShader: File) : super(fragmentShader)

    @Throws(IOException::class)
    constructor(fragmentShader: ResourceLocation) : super(fragmentShader)

    private var time = 0f

    override fun setupUniforms() {
        setupUniform("iResolution")
        setupUniform("iTime")
    }

    override fun updateUniforms() {
        val resolutionID = getUniform("iResolution")
        if (resolutionID > -1)
            glUniform2f(resolutionID, Display.getWidth().toFloat(), Display.getHeight().toFloat())

        val timeID = getUniform("iTime")
        if (timeID > -1) glUniform1f(timeID, time)

        time += 0.002f * deltaTime
    }
}
