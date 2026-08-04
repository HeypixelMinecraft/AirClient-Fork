package net.ccbluex.liquidbounce.file.configs.models

import com.google.gson.GsonBuilder
import net.ccbluex.liquidbounce.LiquidBounce
import net.ccbluex.liquidbounce.config.Configurable
import net.ccbluex.liquidbounce.file.FileManager
import net.ccbluex.liquidbounce.utils.client.MinecraftInstance
import net.ccbluex.liquidbounce.utils.render.IconUtils
import org.lwjgl.opengl.Display
import java.io.File

object ClientConfiguration : Configurable("ClientConfiguration"), MinecraftInstance {
    var clientTitle by boolean("ClientTitle", true)
    var customBackground by boolean("CustomBackground", false)
    var particles by boolean("Particles", false)
    var mainMenuStyle by text("MainMenuStyle", "Default")
    var customMenuBackgroundImageIndex by int("CustomMenuBackgroundImageIndex", 0, 0..20)
    var splashBackground by choices("SplashBackground", arrayOf("splash", "qcf", "cat", "miku"), "splash")
    var stylisedAlts by boolean("StylisedAlts", true)
    var unformattedAlts by boolean("CleanAlts", true)
    var altsLength by int("AltsLength", 16, 4..20)
    var altsPrefix by text("AltsPrefix", "")
    var overrideLanguage by text("OverrideLanguage","")

    private val gson = GsonBuilder().setPrettyPrinting().create()

    /**
     * 将启动背景选择写入 splash.json，供 MixinSplashProgress 在下次启动时读取
     * 必须在 SplashProgress 启动之前就能被读取，因此使用独立的 JSON 文件而非依赖配置系统
     */
    fun saveSplashConfig() {
        try {
            val configFile = File(FileManager.dir, "splash.json")
            val json = mutableMapOf<String, String>()
            json["background"] = splashBackground
            configFile.writeText(gson.toJson(json))
        } catch (_: Exception) {
            // 写入失败不影响客户端运行
        }
    }

    fun updateClientWindow() {
        if (clientTitle) {
            // Set LiquidBounce title
            Display.setTitle(LiquidBounce.clientTitle)
            // Update favicon
            IconUtils.favicon?.let { icons ->
                if (icons.all { it != null }) {
                    Display.setIcon(icons)
                }
            }
        } else {
            // Set original title
            Display.setTitle("Minecraft 1.8.9")
            // Update favicon
            mc.setWindowIcon()
        }
    }

}