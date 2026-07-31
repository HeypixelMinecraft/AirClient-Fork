/*
 * Air Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 */
package net.ccbluex.liquidbounce.ui.client.mainmenu

import net.ccbluex.liquidbounce.ui.client.GuiMainMenu
import net.minecraft.util.ResourceLocation

/**
 * 主菜单风格统一管理。
 *
 * 循环顺序：Default -> Custom -> Minimal -> Sidebar -> Dock -> Split -> Grid -> Orbit -> Header -> Diagonal -> Default
 */
object MainMenuStyles {

    val STYLE_DEFAULT = "Default"
    val STYLE_CUSTOM = "Custom"
    val STYLE_MINIMAL = "Minimal"
    val STYLE_SIDEBAR = "Sidebar"
    val STYLE_DOCK = "Dock"
    val STYLE_SPLIT = "Split"
    val STYLE_GRID = "Grid"
    val STYLE_ORBIT = "Orbit"
    val STYLE_HEADER = "Header"
    val STYLE_DIAGONAL = "Diagonal"

    val STYLES = listOf(
        STYLE_DEFAULT, STYLE_CUSTOM, STYLE_MINIMAL, STYLE_SIDEBAR, STYLE_DOCK, STYLE_SPLIT,
        STYLE_GRID, STYLE_ORBIT, STYLE_HEADER, STYLE_DIAGONAL
    )

    val STYLE_NAMES = mapOf(
        STYLE_DEFAULT to "Default",
        STYLE_CUSTOM to "Custom",
        STYLE_MINIMAL to "Minimal",
        STYLE_SIDEBAR to "Sidebar",
        STYLE_DOCK to "Dock",
        STYLE_SPLIT to "Split",
        STYLE_GRID to "Grid",
        STYLE_ORBIT to "Orbit",
        STYLE_HEADER to "Header",
        STYLE_DIAGONAL to "Diagonal"
    )

    /**
     * 自定义主菜单使用的图片背景列表。
     * 包含根目录的 miku.png 以及 setup 目录下的全部图片。
     * 索引存储在 ClientConfiguration.customMenuBackgroundImageIndex，实际取模避免越界。
     */
    val BACKGROUND_IMAGES = listOf(
        ResourceLocation("airclient/miku.png"),
        ResourceLocation("airclient/setup/miku.jpeg"),
        ResourceLocation("airclient/setup/Mortis.jpeg"),
        ResourceLocation("airclient/setup/ba.jpeg"),
        ResourceLocation("airclient/setup/cat.jpeg"),
        ResourceLocation("airclient/setup/girl.jpeg"),
        ResourceLocation("airclient/setup/girl2.jpeg"),
        ResourceLocation("airclient/setup/qcf.jpeg"),
        ResourceLocation("airclient/setup/soyo.jpeg"),
        ResourceLocation("airclient/setup/splash.png"),
        ResourceLocation("airclient/setup/youxiang.jpeg")
    )

    val BACKGROUND_IMAGE_NAMES = listOf(
        "miku", "MCDOG", "Mortis", "Ba", "Cat",
        "Girl", "Girl 2", "QCF", "Soyo", "Splash", "Youxiang"
    )

    @JvmStatic
    fun backgroundImageIndex(raw: Int): Int = ((raw % BACKGROUND_IMAGES.size) + BACKGROUND_IMAGES.size) % BACKGROUND_IMAGES.size

    @JvmStatic
    fun backgroundImage(raw: Int): ResourceLocation =
        BACKGROUND_IMAGES[backgroundImageIndex(raw)]

    @JvmStatic
    fun backgroundDisplayName(raw: Int): String =
        BACKGROUND_IMAGE_NAMES[backgroundImageIndex(raw)]

    @JvmStatic
    fun next(current: String): String {
        val idx = STYLES.indexOf(current)
        if (idx < 0) return STYLE_DEFAULT
        return STYLES[(idx + 1) % STYLES.size]
    }

    @JvmStatic
    fun displayName(style: String): String = STYLE_NAMES[style] ?: "Default"

    /**
     * 根据风格名创建对应的主菜单屏幕实例。
     */
    @JvmStatic
    fun createScreen(style: String): Any = when (style) {
        STYLE_CUSTOM -> CustomMainMenu()
        STYLE_MINIMAL -> MinimalMainMenu()
        STYLE_SIDEBAR -> SidebarMainMenu()
        STYLE_DOCK -> DockMainMenu()
        STYLE_SPLIT -> SplitMainMenu()
        STYLE_GRID -> GridMainMenu()
        STYLE_ORBIT -> OrbitMainMenu()
        STYLE_HEADER -> HeaderMainMenu()
        STYLE_DIAGONAL -> DiagonalMainMenu()
        else -> GuiMainMenu()
    }
}

