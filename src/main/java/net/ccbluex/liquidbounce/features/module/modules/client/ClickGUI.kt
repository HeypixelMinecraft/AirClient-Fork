/*
 * Air Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 */
package net.ccbluex.liquidbounce.features.module.modules.client

import net.ccbluex.liquidbounce.LiquidBounce.clickGui
import net.ccbluex.liquidbounce.event.PacketEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.ui.client.clickgui.ClickGui
import net.ccbluex.liquidbounce.ui.client.clickgui.astolfo.AstolfoClickGui
import net.ccbluex.liquidbounce.ui.client.clickgui.augustus.AugustusClickGui
import net.ccbluex.liquidbounce.ui.client.clickgui.flat.FlatClickGui
import net.ccbluex.liquidbounce.ui.client.clickgui.moonlight.MoonLightClickGui
import net.ccbluex.liquidbounce.ui.client.clickgui.neverlose.NeverloseScreen
import net.ccbluex.liquidbounce.ui.client.clickgui.opai.OpaiScreen
import net.ccbluex.liquidbounce.ui.client.clickgui.rise.RiseClickGui
import net.ccbluex.liquidbounce.ui.client.clickgui.style.styles.LiquidBounceStyle
import net.ccbluex.liquidbounce.ui.client.clickgui.style.styles.MinimalStyle
import net.ccbluex.liquidbounce.ui.client.clickgui.style.styles.SlowlyStyle
import net.minecraft.network.play.server.S2EPacketCloseWindow
import org.lwjgl.input.Keyboard
import java.awt.Color

object ClickGUI : Module("ClickGUI", Category.CLIENT, Keyboard.KEY_RSHIFT, canBeEnabled = false) {
    private val style by choices(
        "Style",
        arrayOf("LiquidBounce", "Slowly", "Minimal", "Neverlose", "Augustus", "Opai", "Astolfo", "MoonLight", "Rise", "Flat"),
        "LiquidBounce"
    ).onChanged {
        updateStyle()
    }
    var scale by float("Scale", 0.8f, 0.5f..1.5f)
    val maxElements by int("MaxElements", 15, 1..30)
    val fadeSpeed by float("FadeSpeed", 1f, 0.5f..4f)
    val scrolls by boolean("Scrolls", true)
    val spacedModules by boolean("SpacedModules", false)
    val panelsForcedInBoundaries by boolean("PanelsForcedInBoundaries", false)

    private val color by color("Color", Color(0, 160, 255)) { style !in arrayOf("Slowly", "Neverlose", "Augustus", "Opai", "Astolfo", "MoonLight", "Rise", "Flat") }

    val guiColor
        get() = color.rgb

    // Neverlose style settings - expose Value objects for ClickGUI interaction
    val nlThemeValue = choices("NLTheme", arrayOf("Ocean", "Sunset", "Forest", "Royal", "Crimson", "Mono"), "Ocean") { style == "Neverlose" || style == "Rise" }
    val nlBgOpacityValue = float("NLBgOpacity", 0.6f, 0.1f..1.0f) { style == "Neverlose" || style == "Rise" }
    val nlBlurValue = boolean("NLBlur", true) { style == "Neverlose" || style == "Rise" }
    val nlBlurModeValue = choices("NLBlurMode", arrayOf("Gaussian", "Dual", "Better"), "Better") { (style == "Neverlose" || style == "Rise") && nlBlur }
    val nlBlurStrengthValue = float("NLBlurStrength", 10f, 1f..50f) { (style == "Neverlose" || style == "Rise") && nlBlur }
    val nlAnimationSpeedValue = float("NLAnimationSpeed", 1.0f, 0.1f..4.0f) { style == "Neverlose" || style == "Rise" }
    val nlGlowIntensityValue = float("NLGlowIntensity", 1.0f, 0f..1f) { style == "Neverlose" || style == "Rise" }

    val nlTheme by nlThemeValue
    val nlBgOpacity by nlBgOpacityValue
    val nlBlur by nlBlurValue
    val nlBlurMode by nlBlurModeValue
    val nlBlurStrength by nlBlurStrengthValue
    val nlAnimationSpeed by nlAnimationSpeedValue
    val nlGlowIntensity by nlGlowIntensityValue

    // Theme presets: each theme has accent color and background color
    val nlAccentColor: Color
        get() = when (nlTheme) {
            "Ocean" -> Color(74, 144, 217)
            "Sunset" -> Color(255, 130, 50)
            "Forest" -> Color(80, 200, 120)
            "Royal" -> Color(160, 100, 255)
            "Crimson" -> Color(255, 80, 100)
            "Mono" -> Color(220, 220, 230)
            else -> Color(74, 144, 217)
        }

    val nlThemeBgColor: Color
        get() = when (nlTheme) {
            "Ocean" -> Color(0x0D, 0x1B, 0x2A)
            "Sunset" -> Color(0x1A, 0x0E, 0x14)
            "Forest" -> Color(0x0D, 0x1A, 0x0F)
            "Royal" -> Color(0x14, 0x0D, 0x1A)
            "Crimson" -> Color(0x1A, 0x0D, 0x10)
            "Mono" -> Color(0x12, 0x12, 0x1C)
            else -> Color(0x12, 0x12, 0x1C)
        }

    // Theme accent colors for the color dots display
    val nlThemeAccents: Array<Pair<String, Color>>
        get() = arrayOf(
            "Ocean" to Color(74, 144, 217),
            "Sunset" to Color(255, 130, 50),
            "Forest" to Color(80, 200, 120),
            "Royal" to Color(160, 100, 255),
            "Crimson" to Color(255, 80, 100),
            "Mono" to Color(220, 220, 230)
        )

    override fun onEnable() {
        openSelectedStyle()
        Keyboard.enableRepeatEvents(true)
    }

    private fun openSelectedStyle() {
        when (style) {
            "Neverlose" -> mc.displayGuiScreen(NeverloseScreen())
            "Augustus" -> mc.displayGuiScreen(AugustusClickGui())
            "Opai" -> mc.displayGuiScreen(OpaiScreen.INSTANCE)
            "Astolfo" -> mc.displayGuiScreen(AstolfoClickGui())
            "MoonLight" -> mc.displayGuiScreen(MoonLightClickGui())
            "Rise" -> mc.displayGuiScreen(RiseClickGui())
            "Flat" -> mc.displayGuiScreen(FlatClickGui())
            else -> {
                updateStyle()
                mc.displayGuiScreen(clickGui)
            }
        }
    }

    private fun updateStyle() {
        clickGui.style = when (style) {
            "LiquidBounce" -> LiquidBounceStyle
            "Slowly" -> SlowlyStyle
            "Minimal" -> MinimalStyle
            else -> return
        }
    }

    val onPacket = handler<PacketEvent>(always = true) { event ->
        if (event.packet is S2EPacketCloseWindow && mc.currentScreen is ClickGui) {
            event.cancelEvent()
        }
    }
}
