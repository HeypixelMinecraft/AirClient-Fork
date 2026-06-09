/*
 * AirClient Hacked Client
 * A free open source mixin-based injection hacked client built on Liquidbounce legacy codebase.
 * https://github.com/lmx0721/AirClient
 */
package net.ccbluex.liquidbounce.ui.client.clickgui.neverlose

import net.ccbluex.liquidbounce.LiquidBounce
import net.ccbluex.liquidbounce.config.BoolValue
import net.ccbluex.liquidbounce.config.FloatValue
import net.ccbluex.liquidbounce.config.IntValue
import net.ccbluex.liquidbounce.config.ListValue
import net.ccbluex.liquidbounce.config.TextValue
import net.ccbluex.liquidbounce.config.Value
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.file.FileManager.modulesConfig
import net.ccbluex.liquidbounce.file.FileManager.saveConfig
import net.ccbluex.liquidbounce.ui.font.Fonts
import net.ccbluex.liquidbounce.utils.render.RenderUtils
import net.ccbluex.liquidbounce.utils.render.RoundedUtil
import net.minecraft.client.gui.GuiScreen
import org.lwjgl.input.Keyboard
import org.lwjgl.input.Mouse
import java.awt.Color
import java.io.IOException
import kotlin.math.roundToInt

class NeverloseScreen : GuiScreen() {

    private val panels = Category.entries.associateWith { category ->
        LiquidBounce.moduleManager[category].sortedBy { it.name.lowercase() }
    }
    private var selectedCategory = Category.COMBAT
    private var selectedModule: Module? = panels[selectedCategory]?.firstOrNull()
    private var search = ""
    private var dragging = false
    private var dragX = 0
    private var dragY = 0
    private var posX = 40
    private var posY = 40
    private var moduleScroll = 0
    private var valueScroll = 0

    private val guiWidth = 520
    private val guiHeight = 420
    private val sidebarWidth = 136
    private val headerHeight = 48

    override fun initGui() {
        Keyboard.enableRepeatEvents(true)
        posX = ((width - guiWidth) / 2).coerceAtLeast(20)
        posY = ((height - guiHeight) / 2).coerceAtLeast(20)
    }

    override fun onGuiClosed() {
        Keyboard.enableRepeatEvents(false)
    }

    override fun doesGuiPauseGame() = false

    override fun drawScreen(mouseX: Int, mouseY: Int, partialTicks: Float) {
        drawDefaultBackground()

        if (dragging) {
            posX = mouseX + dragX
            posY = mouseY + dragY
        }

        drawShell()
        drawSidebar(mouseX, mouseY)
        drawModules(mouseX, mouseY)
        drawValues(mouseX, mouseY)
        drawUserFooter()

        handleWheel(mouseX, mouseY)
        super.drawScreen(mouseX, mouseY, partialTicks)
    }

    private fun drawShell() {
        RoundedUtil.drawRound(posX.toFloat(), posY.toFloat(), guiWidth.toFloat(), guiHeight.toFloat(), 6f, BG_OVERLAY)
        RoundedUtil.drawRound((posX + sidebarWidth).toFloat(), posY.toFloat(), (guiWidth - sidebarWidth).toFloat(), guiHeight.toFloat(), 6f, BG)
        RoundedUtil.drawRound((posX + sidebarWidth).toFloat(), posY.toFloat(), (guiWidth - sidebarWidth).toFloat(), headerHeight.toFloat(), 6f, TOP)
        RenderUtils.drawRect((posX + sidebarWidth).toFloat(), posY.toFloat(), (posX + sidebarWidth + 4).toFloat(), (posY + guiHeight).toFloat(), BG)
        RenderUtils.drawRect((posX + sidebarWidth).toFloat(), (posY + headerHeight - 1).toFloat(), (posX + guiWidth).toFloat(), (posY + headerHeight).toFloat(), LINE)
        RenderUtils.drawRect((posX + sidebarWidth - 1).toFloat(), posY.toFloat(), (posX + sidebarWidth).toFloat(), (posY + guiHeight).toFloat(), LINE)

        Fonts.font40.drawStringWithShadow("AirClient", (posX + 18).toFloat(), (posY + 14).toFloat(), TEXT.rgb)
        Fonts.font35.drawString("Neverlose", (posX + sidebarWidth + 16).toFloat(), (posY + 16).toFloat(), TEXT.rgb)

        val searchX = posX + guiWidth - 150
        val searchY = posY + 14
        RoundedUtil.drawRound(searchX.toFloat(), searchY.toFloat(), 128f, 22f, 4f, SEARCH_BG)
        Fonts.font30.drawString(if (search.isBlank()) "Search" else search, (searchX + 8).toFloat(), (searchY + 7).toFloat(), if (search.isBlank()) MUTED.rgb else TEXT.rgb)
    }

    private fun drawSidebar(mouseX: Int, mouseY: Int) {
        val groups = listOf(
            "Combat" to listOf(Category.COMBAT, Category.PLAYER),
            "Common" to listOf(Category.MOVEMENT, Category.WORLD, Category.MISC, Category.EXPLOIT),
            "Visuals" to listOf(Category.RENDER, Category.FUN),
            "Presets" to listOf(Category.CLIENT, Category.MUSIC)
        )

        var y = posY + 42
        groups.forEach { (title, categories) ->
            Fonts.font30.drawString(title, (posX + 14).toFloat(), y.toFloat(), MUTED.rgb)
            y += 18

            categories.forEach { category ->
                val hovered = isHovered(posX + 8, y - 4, 120, 19, mouseX, mouseY)
                if (category == selectedCategory || hovered) {
                    RoundedUtil.drawRound((posX + 8).toFloat(), (y - 4).toFloat(), 120f, 19f, 5f, if (category == selectedCategory) SELECTED else HOVER)
                }

                Fonts.font35.drawString(categoryIcon(category), (posX + 14).toFloat(), (y + 1).toFloat(), ACCENT.rgb)
                Fonts.font30.drawString(category.displayName, (posX + 34).toFloat(), y.toFloat(), TEXT.rgb)
                y += 24
            }
            y += 10
        }
    }

    private fun drawModules(mouseX: Int, mouseY: Int) {
        val listX = posX + sidebarWidth + 12
        val listY = posY + headerHeight + 12
        val listWidth = 168
        val listHeight = guiHeight - headerHeight - 24
        val modules = filteredModules()

        RoundedUtil.drawRound(listX.toFloat(), listY.toFloat(), listWidth.toFloat(), listHeight.toFloat(), 4f, CARD)
        Fonts.font30.drawString("Modules", (listX + 10).toFloat(), (listY + 8).toFloat(), MUTED.rgb)

        var y = listY + 28 + moduleScroll
        modules.forEach { module ->
            if (y > listY + 20 && y < listY + listHeight - 8) {
                val selected = module == selectedModule
                val hovered = isHovered(listX + 8, y, listWidth - 16, 22, mouseX, mouseY)
                if (selected || hovered) {
                    RoundedUtil.drawRound((listX + 8).toFloat(), y.toFloat(), (listWidth - 16).toFloat(), 22f, 4f, if (selected) SELECTED else HOVER)
                }

                val dotColor = if (module.state) ACCENT else MUTED
                RoundedUtil.drawRound((listX + 14).toFloat(), (y + 8).toFloat(), 6f, 6f, 3f, dotColor)
                Fonts.font30.drawString(module.name, (listX + 28).toFloat(), (y + 7).toFloat(), if (module.state) TEXT.rgb else DISABLED.rgb)
            }
            y += 26
        }
    }

    private fun drawValues(mouseX: Int, mouseY: Int) {
        val valueX = posX + sidebarWidth + 192
        val valueY = posY + headerHeight + 12
        val valueWidth = guiWidth - sidebarWidth - 204
        val valueHeight = guiHeight - headerHeight - 24
        val module = selectedModule

        RoundedUtil.drawRound(valueX.toFloat(), valueY.toFloat(), valueWidth.toFloat(), valueHeight.toFloat(), 4f, CARD)

        if (module == null) {
            Fonts.font35.drawCenteredString("No module", (valueX + valueWidth / 2).toFloat(), (valueY + 120).toFloat(), MUTED.rgb)
            return
        }

        Fonts.font35.drawString(module.name, (valueX + 12).toFloat(), (valueY + 10).toFloat(), TEXT.rgb)
        val stateText = if (module.state) "Enabled" else "Disabled"
        Fonts.font30.drawString(stateText, (valueX + valueWidth - Fonts.font30.getStringWidth(stateText) - 12).toFloat(), (valueY + 13).toFloat(), if (module.state) ACCENT.rgb else MUTED.rgb)
        RenderUtils.drawRect((valueX + 12).toFloat(), (valueY + 34).toFloat(), (valueX + valueWidth - 12).toFloat(), (valueY + 35).toFloat(), LINE)

        val values = module.values.filter { it.shouldRender() }
        if (values.isEmpty()) {
            Fonts.font30.drawString("No settings", (valueX + 12).toFloat(), (valueY + 50).toFloat(), MUTED.rgb)
            return
        }

        var y = valueY + 48 + valueScroll
        values.forEach { value ->
            if (y > valueY + 34 && y < valueY + valueHeight - 12) {
                drawValue(value, valueX + 12, y, valueWidth - 24, mouseX, mouseY)
            }
            y += valueHeight(value)
        }
    }

    private fun drawValue(value: Value<*>, x: Int, y: Int, width: Int, mouseX: Int, mouseY: Int) {
        val hovered = isHovered(x, y, width, 24, mouseX, mouseY)
        if (hovered) {
            RoundedUtil.drawRound(x.toFloat(), y.toFloat(), width.toFloat(), 24f, 4f, HOVER)
        }

        Fonts.font30.drawString(value.name, (x + 6).toFloat(), (y + 8).toFloat(), TEXT.rgb)

        when (value) {
            is BoolValue -> drawBooleanValue(value, x, y, width)
            is IntValue -> drawNumberValue(value.get().toFloat(), value.minimum.toFloat(), value.maximum.toFloat(), x, y, width, value.get().toString())
            is FloatValue -> drawNumberValue(value.get(), value.minimum, value.maximum, x, y, width, "%.2f".format(value.get()))
            is ListValue -> drawChoiceValue(value, x, y, width)
            is TextValue -> drawTextValue(value, x, y, width)
            else -> Fonts.font30.drawString(value.toText(), (x + width - Fonts.font30.getStringWidth(value.toText()) - 6).toFloat(), (y + 8).toFloat(), MUTED.rgb)
        }
    }

    private fun drawBooleanValue(value: BoolValue, x: Int, y: Int, width: Int) {
        val enabled = value.get()
        val bg = if (enabled) BOOL_ON_BG else BOOL_OFF_BG
        val circle = if (enabled) ACCENT else MUTED
        val switchX = x + width - 38
        RoundedUtil.drawRound(switchX.toFloat(), (y + 7).toFloat(), 28f, 12f, 6f, bg)
        RoundedUtil.drawRound((switchX + if (enabled) 16 else 2).toFloat(), (y + 9).toFloat(), 8f, 8f, 4f, circle)
    }

    private fun drawNumberValue(current: Float, min: Float, max: Float, x: Int, y: Int, width: Int, text: String) {
        val sliderX = x + 92
        val sliderY = y + 17
        val sliderWidth = width - 150
        val progress = ((current - min) / (max - min)).coerceIn(0f, 1f)
        Fonts.font30.drawString(text, (x + width - Fonts.font30.getStringWidth(text) - 6).toFloat(), (y + 8).toFloat(), MUTED.rgb)
        RoundedUtil.drawRound(sliderX.toFloat(), sliderY.toFloat(), sliderWidth.toFloat(), 4f, 2f, SLIDER_BG)
        RoundedUtil.drawRound(sliderX.toFloat(), sliderY.toFloat(), sliderWidth * progress, 4f, 2f, ACCENT)
        RoundedUtil.drawRound((sliderX + sliderWidth * progress - 3).toFloat(), (sliderY - 2).toFloat(), 8f, 8f, 4f, ACCENT)
    }

    private fun drawChoiceValue(value: ListValue, x: Int, y: Int, width: Int) {
        val text = value.get()
        Fonts.font30.drawString(text, (x + width - Fonts.font30.getStringWidth(text) - 6).toFloat(), (y + 8).toFloat(), ACCENT.rgb)
    }

    private fun drawTextValue(value: TextValue, x: Int, y: Int, width: Int) {
        val text = value.get().take(18)
        Fonts.font30.drawString(text, (x + width - Fonts.font30.getStringWidth(text) - 6).toFloat(), (y + 8).toFloat(), MUTED.rgb)
    }

    private fun drawUserFooter() {
        RenderUtils.drawRect(posX.toFloat(), (posY + guiHeight - 36).toFloat(), (posX + sidebarWidth - 2).toFloat(), (posY + guiHeight - 35).toFloat(), LINE)
        val playerName = mc.thePlayer?.name ?: "Offline"
        Fonts.font30.drawString(playerName, (posX + 14).toFloat(), (posY + guiHeight - 24).toFloat(), TEXT.rgb)
        Fonts.font30.drawString("Lifetime", (posX + 14).toFloat(), (posY + guiHeight - 13).toFloat(), ACCENT.rgb)
    }

    @Throws(IOException::class)
    override fun mouseClicked(mouseX: Int, mouseY: Int, mouseButton: Int) {
        if (mouseButton == 0 && isHovered(posX, posY, sidebarWidth, 42, mouseX, mouseY)) {
            dragging = true
            dragX = posX - mouseX
            dragY = posY - mouseY
        }

        handleSidebarClick(mouseX, mouseY, mouseButton)
        handleModuleClick(mouseX, mouseY, mouseButton)
        handleValueClick(mouseX, mouseY, mouseButton)
        super.mouseClicked(mouseX, mouseY, mouseButton)
    }

    private fun handleSidebarClick(mouseX: Int, mouseY: Int, mouseButton: Int) {
        if (mouseButton != 0) return

        val groups = listOf(
            listOf(Category.COMBAT, Category.PLAYER),
            listOf(Category.MOVEMENT, Category.WORLD, Category.MISC, Category.EXPLOIT),
            listOf(Category.RENDER, Category.FUN),
            listOf(Category.CLIENT, Category.MUSIC)
        )

        var y = posY + 60
        groups.forEach { categories ->
            categories.forEach { category ->
                if (isHovered(posX + 8, y - 4, 120, 19, mouseX, mouseY)) {
                    selectedCategory = category
                    selectedModule = filteredModules().firstOrNull()
                    moduleScroll = 0
                    valueScroll = 0
                    return
                }
                y += 24
            }
            y += 28
        }
    }

    private fun handleModuleClick(mouseX: Int, mouseY: Int, mouseButton: Int) {
        val listX = posX + sidebarWidth + 12
        val listY = posY + headerHeight + 12
        val modules = filteredModules()
        var y = listY + 28 + moduleScroll

        modules.forEach { module ->
            if (isHovered(listX + 8, y, 152, 22, mouseX, mouseY)) {
                when (mouseButton) {
                    0 -> {
                        selectedModule = module
                        valueScroll = 0
                    }
                    1 -> module.toggle()
                }
                return
            }
            y += 26
        }
    }

    private fun handleValueClick(mouseX: Int, mouseY: Int, mouseButton: Int) {
        val module = selectedModule ?: return
        val valueX = posX + sidebarWidth + 192
        val valueY = posY + headerHeight + 12
        val valueWidth = guiWidth - sidebarWidth - 204
        var y = valueY + 48 + valueScroll

        module.values.filter { it.shouldRender() }.forEach { value ->
            if (isHovered(valueX + 12, y, valueWidth - 24, 24, mouseX, mouseY)) {
                when (value) {
                    is BoolValue -> if (mouseButton == 0) value.toggle()
                    is IntValue -> if (mouseButton == 0) setIntValueByMouse(value, mouseX, valueX + 104, valueWidth - 174)
                    is FloatValue -> if (mouseButton == 0) setFloatValueByMouse(value, mouseX, valueX + 104, valueWidth - 174)
                    is ListValue -> if (mouseButton == 0) nextChoice(value) else if (mouseButton == 1) previousChoice(value)
                    is TextValue -> if (mouseButton == 1) value.set("")
                    else -> Unit
                }
                return
            }
            y += valueHeight(value)
        }
    }

    @Throws(IOException::class)
    override fun keyTyped(typedChar: Char, keyCode: Int) {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(null)
            return
        }

        if (keyCode == Keyboard.KEY_BACK) {
            if (search.isNotEmpty()) search = search.dropLast(1)
            return
        }

        if (keyCode == Keyboard.KEY_RETURN) {
            search = ""
            return
        }

        if (!Character.isISOControl(typedChar)) {
            search += typedChar
            selectedModule = filteredModules().firstOrNull() ?: selectedModule
            moduleScroll = 0
        }
    }

    override fun mouseReleased(mouseX: Int, mouseY: Int, state: Int) {
        if (state == 0) dragging = false
        saveConfig(modulesConfig)
        super.mouseReleased(mouseX, mouseY, state)
    }

    private fun handleWheel(mouseX: Int, mouseY: Int) {
        val wheel = Mouse.getDWheel()
        if (wheel == 0) return

        if (isHovered(posX + sidebarWidth + 12, posY + headerHeight + 12, 168, guiHeight - headerHeight - 24, mouseX, mouseY)) {
            moduleScroll = (moduleScroll + if (wheel > 0) 18 else -18).coerceAtMost(0)
        }

        if (isHovered(posX + sidebarWidth + 192, posY + headerHeight + 12, guiWidth - sidebarWidth - 204, guiHeight - headerHeight - 24, mouseX, mouseY)) {
            valueScroll = (valueScroll + if (wheel > 0) 18 else -18).coerceAtMost(0)
        }
    }

    private fun filteredModules(): List<Module> {
        val modules = panels[selectedCategory].orEmpty()
        if (search.isBlank()) return modules
        return modules.filter { module ->
            module.name.contains(search, ignoreCase = true) || module.spacedName.contains(search, ignoreCase = true)
        }
    }

    private fun valueHeight(value: Value<*>) = when (value) {
        is IntValue, is FloatValue -> 30
        else -> 26
    }

    private fun setIntValueByMouse(value: IntValue, mouseX: Int, sliderX: Int, sliderWidth: Int) {
        val progress = ((mouseX - sliderX).toFloat() / sliderWidth).coerceIn(0f, 1f)
        value.set((value.minimum + (value.maximum - value.minimum) * progress).roundToInt())
    }

    private fun setFloatValueByMouse(value: FloatValue, mouseX: Int, sliderX: Int, sliderWidth: Int) {
        val progress = ((mouseX - sliderX).toFloat() / sliderWidth).coerceIn(0f, 1f)
        value.set(value.minimum + (value.maximum - value.minimum) * progress)
    }

    private fun nextChoice(value: ListValue) {
        val index = value.values.indexOfFirst { it.equals(value.get(), ignoreCase = true) }
        value.set(value.values[(index + 1).floorMod(value.values.size)])
    }

    private fun previousChoice(value: ListValue) {
        val index = value.values.indexOfFirst { it.equals(value.get(), ignoreCase = true) }
        value.set(value.values[(index - 1).floorMod(value.values.size)])
    }

    private fun Int.floorMod(mod: Int) = ((this % mod) + mod) % mod

    private fun categoryIcon(category: Category) = when (category) {
        Category.COMBAT -> "⚔"
        Category.PLAYER -> "●"
        Category.MOVEMENT -> "➜"
        Category.RENDER -> "◈"
        Category.WORLD -> "◆"
        Category.MISC -> "◇"
        Category.EXPLOIT -> "◎"
        Category.FUN -> "★"
        Category.CLIENT -> "⚙"
        Category.MUSIC -> "♫"
    }

    private fun isHovered(x: Int, y: Int, width: Int, height: Int, mouseX: Int, mouseY: Int): Boolean {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height
    }

    private companion object {
        val BG = Color(0x000C18)
        val BG_OVERLAY = Color(0xDA081222.toInt(), true)
        val TOP = Color(0x080D13)
        val CARD = Color(0x07111D)
        val SEARCH_BG = Color(0x000314)
        val LINE = Color(0x131C29)
        val SELECTED = Color(0x003454)
        val HOVER = Color(0x182637)
        val ACCENT = Color(0x00BBFF)
        val TEXT = Color(0xF4F7FB)
        val MUTED = Color(0x7A899A)
        val DISABLED = Color(0x4A5260)
        val BOOL_ON_BG = Color(0x00173A)
        val BOOL_OFF_BG = Color(0x000314)
        val SLIDER_BG = Color(0x000F25)
    }
}