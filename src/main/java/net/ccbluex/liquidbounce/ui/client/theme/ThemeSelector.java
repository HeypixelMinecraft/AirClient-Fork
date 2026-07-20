/*
 * Air Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 */
package net.ccbluex.liquidbounce.ui.client.theme;

import net.ccbluex.liquidbounce.features.module.modules.client.ThemeManager;
import net.ccbluex.liquidbounce.ui.font.Fonts;
import net.ccbluex.liquidbounce.utils.client.ClientThemesUtils;
import net.ccbluex.liquidbounce.utils.render.RenderUtils;
import net.ccbluex.liquidbounce.utils.render.RoundedUtil;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ThemeSelector extends GuiScreen {
    private static ThemeSelector instance;

    // 滚动状态（平滑滚动）
    private float scrollOffset = 0f;
    private float targetScrollOffset = 0f;

    // 布局常量
    private static final int ITEMS_PER_ROW = 5;
    private static final int ITEM_HEIGHT = 58;
    private static final int ITEM_SPACING = 8;
    private static final int HEADER_HEIGHT = 46;
    private static final int CURRENT_THEME_HEIGHT = 62;
    private static final int SEARCH_HEIGHT = 30;
    private static final int FOOTER_HEIGHT = 28;
    private static final int PADDING = 16;
    private static final int SECTION_GAP = 10;

    // 主题数据
    private final List<ThemeColor> themeColors = new ArrayList<>();
    private List<ThemeColor> filteredThemes = new ArrayList<>();
    private String searchText = "";
    private boolean searchFocused = false;

    // 交互状态
    private int hoveredIndex = -1;
    private boolean closeHovered = false;
    private boolean searchHovered = false;

    public static ThemeSelector getInstance() {
        return instance == null ? instance = new ThemeSelector() : instance;
    }

    public ThemeSelector() {
        initThemeColors();
        filteredThemes = new ArrayList<>(themeColors);
    }

    private void initThemeColors() {
        themeColors.add(new ThemeColor("MoonPurple", "moonpurple"));
        themeColors.add(new ThemeColor("Astolfo", "astolfo"));
        themeColors.add(new ThemeColor("Rainbow", "rainbow"));
        themeColors.add(new ThemeColor("Water", "water"));
        themeColors.add(new ThemeColor("Fire", "fire"));
        themeColors.add(new ThemeColor("Aqua", "aqua"));
        themeColors.add(new ThemeColor("Mint", "mint"));
        themeColors.add(new ThemeColor("FDP", "fdp"));
        themeColors.add(new ThemeColor("Magic", "magic"));
        themeColors.add(new ThemeColor("Tree", "tree"));
        themeColors.add(new ThemeColor("Sun", "sun"));
        themeColors.add(new ThemeColor("Flower", "flower"));
        themeColors.add(new ThemeColor("Loyoi", "loyoi"));
        themeColors.add(new ThemeColor("May", "may"));
        themeColors.add(new ThemeColor("Cero", "cero"));
        themeColors.add(new ThemeColor("Azure", "azure"));
        themeColors.add(new ThemeColor("Pumpkin", "pumpkin"));
        themeColors.add(new ThemeColor("Polarized", "polarized"));
        themeColors.add(new ThemeColor("Sundae", "sundae"));
        themeColors.add(new ThemeColor("Terminal", "terminal"));
        themeColors.add(new ThemeColor("Coral", "coral"));
        themeColors.add(new ThemeColor("Peony", "peony"));
        themeColors.add(new ThemeColor("VerGreen", "vergren"));
        themeColors.add(new ThemeColor("EveningSunshine", "eveningsunshine"));
        themeColors.add(new ThemeColor("LightOrange", "lightorange"));
        themeColors.add(new ThemeColor("Reef", "reef"));
        themeColors.add(new ThemeColor("Amin", "amin"));
        themeColors.add(new ThemeColor("MagicS", "magics"));
        themeColors.add(new ThemeColor("MangoPulp", "mangopulp"));
        themeColors.add(new ThemeColor("Aqualicious", "aqualicious"));
        themeColors.add(new ThemeColor("Stripe", "stripe"));
        themeColors.add(new ThemeColor("Shifter", "shifter"));
        themeColors.add(new ThemeColor("QuePal", "quepal"));
        themeColors.add(new ThemeColor("Orca", "orca"));
        themeColors.add(new ThemeColor("SublimeVivid", "sublimevivid"));
        themeColors.add(new ThemeColor("MoonAsteroid", "moonasteroid"));
        themeColors.add(new ThemeColor("SummerDog", "summerdog"));
        themeColors.add(new ThemeColor("PinkFlavour", "pinkflavour"));
        themeColors.add(new ThemeColor("SinCityRed", "sincityred"));
        themeColors.add(new ThemeColor("Timber", "timber"));
        themeColors.add(new ThemeColor("PinotNoir", "pinotnoir"));
        themeColors.add(new ThemeColor("DirtyFog", "dirtyfog"));
        themeColors.add(new ThemeColor("Piglet", "piglet"));
        themeColors.add(new ThemeColor("LittleLeaf", "littleleaf"));
        themeColors.add(new ThemeColor("Nelson", "nelson"));
        themeColors.add(new ThemeColor("TurquoiseFlow", "turquoiseflow"));
        themeColors.add(new ThemeColor("Purplin", "purplin"));
        themeColors.add(new ThemeColor("Martini", "martini"));
        themeColors.add(new ThemeColor("SoundCloud", "soundcloud"));
        themeColors.add(new ThemeColor("Inbox", "inbox"));
        themeColors.add(new ThemeColor("Amethyst", "amethyst"));
        themeColors.add(new ThemeColor("Blush", "blush"));
        themeColors.add(new ThemeColor("MochaRose", "mocharose"));
        themeColors.add(new ThemeColor("NeonCrimson", "neoncrimson"));
        themeColors.add(new ThemeColor("AcidGreen", "acidgreen"));
        themeColors.add(new ThemeColor("VaporWave", "vaporwave"));
        themeColors.add(new ThemeColor("Noir", "noir"));
        themeColors.add(new ThemeColor("Obsidian", "obsidian"));
        themeColors.add(new ThemeColor("Champagne", "champagne"));
        themeColors.add(new ThemeColor("RoseGold", "rosegold"));
        themeColors.add(new ThemeColor("Arctic", "arctic"));
        themeColors.add(new ThemeColor("Frost", "frost"));
        themeColors.add(new ThemeColor("Glacier", "glacier"));
        themeColors.add(new ThemeColor("Slate", "slate"));
        themeColors.add(new ThemeColor("Abyss", "abyss"));
        themeColors.add(new ThemeColor("BioLum", "biolum"));
        themeColors.add(new ThemeColor("EverGreen", "evergreen"));
        themeColors.add(new ThemeColor("Dusk", "dusk"));
        themeColors.add(new ThemeColor("Aurora", "aurora"));
        themeColors.add(new ThemeColor("RetroWave", "retrowave"));
        themeColors.add(new ThemeColor("Y2K", "y2k"));
        themeColors.add(new ThemeColor("DustyRose", "dustyrose"));
        themeColors.add(new ThemeColor("Sage", "sage"));
        themeColors.add(new ThemeColor("CloudBurst", "cloudburst"));
        themeColors.add(new ThemeColor("Monolith", "monolith"));
        themeColors.add(new ThemeColor("Bloodline", "bloodline"));
        themeColors.add(new ThemeColor("Lavender", "lavender"));
        themeColors.add(new ThemeColor("Butter", "butter"));
        themeColors.add(new ThemeColor("Gothic", "gothic"));
        themeColors.add(new ThemeColor("Phantom", "phantom"));
        themeColors.add(new ThemeColor("QuickSilver", "quicksilver"));
        themeColors.add(new ThemeColor("Mercury", "mercury"));
        themeColors.add(new ThemeColor("Tropical", "tropical"));
        themeColors.add(new ThemeColor("Mango", "mango"));
        themeColors.add(new ThemeColor("Rust", "rust"));
        themeColors.add(new ThemeColor("Concrete", "concrete"));
        themeColors.add(new ThemeColor("Nebula", "nebula"));
        themeColors.add(new ThemeColor("SuperNova", "supernova"));
        themeColors.add(new ThemeColor("Eclipse", "eclipse"));
        themeColors.add(new ThemeColor("Iceberg", "iceberg"));
        themeColors.add(new ThemeColor("Scarlet", "scarlet"));
        themeColors.add(new ThemeColor("CyberPink", "cyberpink"));
        themeColors.add(new ThemeColor("Matrix", "matrix"));
        themeColors.add(new ThemeColor("SolarGlare", "solarglare"));
        themeColors.add(new ThemeColor("Zywl", "zywl"));
        themeColors.add(new ThemeColor("DarkNight", "darknight"));
        themeColors.add(new ThemeColor("Emerald", "emerald"));
        themeColors.add(new ThemeColor("Sapphire", "sapphire"));
        themeColors.add(new ThemeColor("Ruby", "ruby"));
        themeColors.add(new ThemeColor("Topaz", "topaz"));
        themeColors.add(new ThemeColor("Amethyst2", "amethyst2"));
        themeColors.add(new ThemeColor("Jade", "jade"));
        themeColors.add(new ThemeColor("Opal", "opal"));
        themeColors.add(new ThemeColor("Garnet", "garnet"));
        themeColors.add(new ThemeColor("Turquoise", "turquoise"));
        themeColors.add(new ThemeColor("Citrine", "citrine"));
        themeColors.add(new ThemeColor("Peridot", "peridot"));
        themeColors.add(new ThemeColor("Aquamarine", "aquamarine"));
        themeColors.add(new ThemeColor("Tanzanite", "tanzanite"));
        themeColors.add(new ThemeColor("Morganite", "morganite"));
        themeColors.add(new ThemeColor("Kunzite", "kunzite"));
        themeColors.add(new ThemeColor("Spinel", "spinel"));
        themeColors.add(new ThemeColor("Zircon", "zircon"));
        themeColors.add(new ThemeColor("Tourmaline", "tourmaline"));
        themeColors.add(new ThemeColor("Alexandrite", "alexandrite"));
        themeColors.add(new ThemeColor("Iolite", "iolite"));
        themeColors.add(new ThemeColor("Chrysoberyl", "chrysoberyl"));
        themeColors.add(new ThemeColor("Beryl", "beryl"));
        themeColors.add(new ThemeColor("Corundum", "corundum"));
        themeColors.add(new ThemeColor("Quartz", "quartz"));
        themeColors.add(new ThemeColor("Moonstone", "moonstone"));
        themeColors.add(new ThemeColor("Sunstone", "sunstone"));
        themeColors.add(new ThemeColor("Labradorite", "labradorite"));
        themeColors.add(new ThemeColor("Spectrolite", "spectrolite"));
        themeColors.add(new ThemeColor("Apatite", "apatite"));
        themeColors.add(new ThemeColor("Fluorite", "fluorite"));
        themeColors.add(new ThemeColor("Calcite", "calcite"));
        themeColors.add(new ThemeColor("Sodalite", "sodalite"));
        themeColors.add(new ThemeColor("Lapis", "lapis"));
        themeColors.add(new ThemeColor("Malachite", "malachite"));
        themeColors.add(new ThemeColor("Azurite", "azurite"));
        themeColors.add(new ThemeColor("Rhodochrosite", "rhodochrosite"));
        themeColors.add(new ThemeColor("Rhodonite", "rhodonite"));
        themeColors.add(new ThemeColor("Serpentine", "serpentine"));
        themeColors.add(new ThemeColor("Howlite", "howlite"));
        themeColors.add(new ThemeColor("Onyx", "onyx"));
        themeColors.add(new ThemeColor("Jasper", "jasper"));
        themeColors.add(new ThemeColor("Agate", "agate"));
        themeColors.add(new ThemeColor("Basalt", "basalt"));
        themeColors.add(new ThemeColor("Granite", "granite"));
        themeColors.add(new ThemeColor("Marble", "marble"));
        themeColors.add(new ThemeColor("Sandstone", "sandstone"));
        themeColors.add(new ThemeColor("Ocean", "ocean"));
        themeColors.add(new ThemeColor("Sunset", "sunset"));
        themeColors.add(new ThemeColor("Forest", "forest"));
        themeColors.add(new ThemeColor("Midnight", "midnight"));
        themeColors.add(new ThemeColor("Cherry", "cherry"));
        themeColors.add(new ThemeColor("Minty", "minty"));
        themeColors.add(new ThemeColor("Thunder", "thunder"));
        themeColors.add(new ThemeColor("Honey", "honey"));
        themeColors.add(new ThemeColor("Ice", "ice"));
        themeColors.add(new ThemeColor("Velvet", "velvet"));
        themeColors.add(new ThemeColor("Plum", "plum"));
        themeColors.add(new ThemeColor("Storm", "storm"));
        themeColors.add(new ThemeColor("Peach", "peach"));
        themeColors.add(new ThemeColor("Denim", "denim"));
        themeColors.add(new ThemeColor("Wine", "wine"));
        themeColors.add(new ThemeColor("Sky", "sky"));
        themeColors.add(new ThemeColor("Amber", "amber"));
        themeColors.add(new ThemeColor("Fern", "fern"));
        themeColors.add(new ThemeColor("Iris", "iris"));
        themeColors.add(new ThemeColor("Crimson", "crimson"));
        themeColors.add(new ThemeColor("Indigo", "indigo"));
        themeColors.add(new ThemeColor("Magenta", "magenta"));
        themeColors.add(new ThemeColor("Violet", "violet"));
        themeColors.add(new ThemeColor("Chartreuse", "chartreuse"));
        themeColors.add(new ThemeColor("Fuchsia", "fuchsia"));
        themeColors.add(new ThemeColor("Lime", "lime"));
        themeColors.add(new ThemeColor("Navy", "navy"));
        themeColors.add(new ThemeColor("Teal", "teal"));
        themeColors.add(new ThemeColor("Cyan", "cyan"));
        themeColors.add(new ThemeColor("Bronze", "bronze"));
        themeColors.add(new ThemeColor("Pearl", "pearl"));
    }

    private void updateFilteredThemes() {
        filteredThemes.clear();
        if (searchText.isEmpty()) {
            filteredThemes.addAll(themeColors);
        } else {
            String lower = searchText.toLowerCase();
            for (ThemeColor t : themeColors) {
                if (t.getDisplayName().toLowerCase().contains(lower)
                        || t.getMode().toLowerCase().contains(lower)) {
                    filteredThemes.add(t);
                }
            }
        }
        targetScrollOffset = 0f;
        scrollOffset = 0f;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        // 平滑滚动插值
        scrollOffset += (targetScrollOffset - scrollOffset) * 0.25f;
        if (Math.abs(targetScrollOffset - scrollOffset) < 0.5f) {
            scrollOffset = targetScrollOffset;
        }

        final ScaledResolution sr = new ScaledResolution(mc);
        final int screenWidth = sr.getScaledWidth();
        final int screenHeight = sr.getScaledHeight();

        // 面板尺寸与位置
        final float panelWidth = Math.min(640, screenWidth - 60);
        final float panelHeight = Math.min(580, screenHeight - 60);
        final float panelX = (screenWidth - panelWidth) / 2f;
        final float panelY = (screenHeight - panelHeight) / 2f;

        // 计算网格区域
        final float currentY = panelY + HEADER_HEIGHT + PADDING;
        final float searchY = currentY + CURRENT_THEME_HEIGHT + SECTION_GAP;
        final float gridTop = searchY + SEARCH_HEIGHT + SECTION_GAP;
        final float gridBottom = panelY + panelHeight - FOOTER_HEIGHT - PADDING;
        final float gridHeight = gridBottom - gridTop;
        final float contentX = panelX + PADDING;
        final float gridWidth = panelWidth - PADDING * 2;
        final float boxWidth = (gridWidth - (ITEMS_PER_ROW - 1) * ITEM_SPACING) / ITEMS_PER_ROW;

        // 滚轮处理
        int scrollWheel = Mouse.getDWheel();
        if (scrollWheel != 0) {
            int totalRows = (int) Math.ceil((double) filteredThemes.size() / ITEMS_PER_ROW);
            int visibleRows = Math.max(1, (int) (gridHeight / (ITEM_HEIGHT + ITEM_SPACING)));
            int maxScroll = Math.max(0, (totalRows - visibleRows) * (ITEM_HEIGHT + ITEM_SPACING));
            targetScrollOffset -= scrollWheel / 6f;
            targetScrollOffset = Math.max(0, Math.min(targetScrollOffset, maxScroll));
        }

        // 颜色定义
        Color panelColor = ThemeManager.INSTANCE.getPanelColor();
        Color headerOverlay = new Color(0, 0, 0, 70);
        Color footerOverlay = new Color(0, 0, 0, 50);
        Color dividerColor = new Color(255, 255, 255, 20);

        // === 面板主体 ===
        RenderUtils.INSTANCE.drawRoundedRect(
                panelX, panelY, panelX + panelWidth, panelY + panelHeight,
                panelColor.getRGB(), 14f, RenderUtils.RoundedCorners.ALL
        );

        // === 顶部标题栏 ===
        RenderUtils.INSTANCE.drawRoundedRect(
                panelX, panelY, panelX + panelWidth, panelY + HEADER_HEIGHT,
                headerOverlay.getRGB(), 14f, RenderUtils.RoundedCorners.TOP_ONLY
        );

        // 标题
        String titleText = "Theme Manager";
        Fonts.INSTANCE.getFontSF40().drawString(
                titleText,
                panelX + PADDING,
                panelY + (HEADER_HEIGHT - Fonts.INSTANCE.getFontSF40().getHeight()) / 2f,
                Color.WHITE.getRGB()
        );

        // 主题数量标签
        String countText = filteredThemes.size() + " themes";
        Fonts.INSTANCE.getFontRegular35().drawString(
                countText,
                panelX + PADDING + Fonts.INSTANCE.getFontSF40().getStringWidth(titleText) + 12,
                panelY + (HEADER_HEIGHT - Fonts.INSTANCE.getFontRegular35().getHeight()) / 2f + 2,
                new Color(255, 255, 255, 150).getRGB()
        );

        // 关闭按钮
        float closeBtnSize = 18;
        float closeBtnX = panelX + panelWidth - PADDING - closeBtnSize;
        float closeBtnY = panelY + (HEADER_HEIGHT - closeBtnSize) / 2f;
        closeHovered = mouseX >= closeBtnX && mouseX <= closeBtnX + closeBtnSize
                && mouseY >= closeBtnY && mouseY <= closeBtnY + closeBtnSize;
        Color closeBg = closeHovered
                ? new Color(255, 90, 90, 200)
                : new Color(255, 255, 255, 30);
        RenderUtils.INSTANCE.drawRoundedRect(
                closeBtnX, closeBtnY, closeBtnX + closeBtnSize, closeBtnY + closeBtnSize,
                closeBg.getRGB(), 6f, RenderUtils.RoundedCorners.ALL
        );
        // 简单的 X：用两条对角线（用 SF 字体绘制 ×）
        Fonts.INSTANCE.getFontSF35().drawString(
                "x",
                closeBtnX + (closeBtnSize - Fonts.INSTANCE.getFontSF35().getStringWidth("x")) / 2f,
                closeBtnY + (closeBtnSize - Fonts.INSTANCE.getFontSF35().getHeight()) / 2f + 1,
                closeHovered ? Color.WHITE.getRGB() : new Color(255, 255, 255, 180).getRGB()
        );

        // === 当前主题展示卡 ===
        String currentThemeName = ThemeManager.INSTANCE.getCurrentTheme();
        Color[] currentColors = getThemePreviewColors(currentThemeName);

        // 渐变填充
        RoundedUtil.applyGradientHorizontal(
                panelX + PADDING, currentY,
                panelWidth - PADDING * 2, CURRENT_THEME_HEIGHT,
                10f, currentColors[0], currentColors[1],
                new Runnable() {
                    @Override
                    public void run() {
                    }
                }
        );

        // 半透明黑色叠加增强文字对比
        RenderUtils.INSTANCE.drawRoundedRect(
                panelX + PADDING, currentY,
                panelX + panelWidth - PADDING, currentY + CURRENT_THEME_HEIGHT,
                new Color(0, 0, 0, 70).getRGB(), 10f, RenderUtils.RoundedCorners.ALL
        );

        // "CURRENT" 小标签
        String labelText = "CURRENT THEME";
        Fonts.INSTANCE.getFontRegular35().drawString(
                labelText,
                panelX + PADDING + 14,
                currentY + 11,
                new Color(255, 255, 255, 200).getRGB()
        );

        // 当前主题名（大字）
        Fonts.INSTANCE.getFontSF40().drawString(
                currentThemeName,
                panelX + PADDING + 14,
                currentY + 30,
                Color.WHITE.getRGB()
        );

        // 右侧色块预览
        float swatchSize = CURRENT_THEME_HEIGHT - 16;
        float swatchX = panelX + panelWidth - PADDING - swatchSize - 8;
        float swatchY = currentY + 8;
        RoundedUtil.applyGradientHorizontal(
                swatchX, swatchY, swatchSize, swatchSize, 6f,
                currentColors[0], currentColors[1],
                new Runnable() {
                    @Override
                    public void run() {
                    }
                }
        );
        // 边框
        RenderUtils.INSTANCE.drawRoundedRect(
                swatchX - 1, swatchY - 1, swatchX + swatchSize + 1, swatchY + swatchSize + 1,
                new Color(255, 255, 255, 80).getRGB(), 7f, RenderUtils.RoundedCorners.ALL
        );
        RoundedUtil.applyGradientHorizontal(
                swatchX, swatchY, swatchSize, swatchSize, 6f,
                currentColors[0], currentColors[1],
                new Runnable() {
                    @Override
                    public void run() {
                    }
                }
        );

        // === 搜索框 ===
        searchHovered = mouseX >= contentX && mouseX <= contentX + gridWidth
                && mouseY >= searchY && mouseY <= searchY + SEARCH_HEIGHT;
        Color searchBg = (searchHovered || searchFocused)
                ? new Color(255, 255, 255, 38)
                : new Color(255, 255, 255, 22);
        RenderUtils.INSTANCE.drawRoundedRect(
                contentX, searchY, contentX + gridWidth, searchY + SEARCH_HEIGHT,
                searchBg.getRGB(), 8f, RenderUtils.RoundedCorners.ALL
        );

        // 搜索提示/输入文本
        String placeholder = "Search themes...";
        String displayText = searchText.isEmpty() ? placeholder : searchText;
        int textColor = searchText.isEmpty()
                ? new Color(255, 255, 255, 110).getRGB()
                : Color.WHITE.getRGB();
        Fonts.INSTANCE.getFontRegular35().drawString(
                displayText,
                contentX + 12,
                searchY + (SEARCH_HEIGHT - Fonts.INSTANCE.getFontRegular35().getHeight()) / 2f + 1,
                textColor
        );

        // 光标闪烁
        if (searchFocused && (System.currentTimeMillis() / 500) % 2 == 0) {
            float cursorX = contentX + 12
                    + Fonts.INSTANCE.getFontRegular35().getStringWidth(displayText);
            RenderUtils.INSTANCE.drawRoundedRect(
                    cursorX, searchY + 8, cursorX + 1, searchY + SEARCH_HEIGHT - 8,
                    Color.WHITE.getRGB(), 0f, RenderUtils.RoundedCorners.ALL
            );
        }

        // 搜索框右侧提示
        String hint = "Type to filter";
        Fonts.INSTANCE.getFontRegular35().drawString(
                hint,
                contentX + gridWidth - Fonts.INSTANCE.getFontRegular35().getStringWidth(hint) - 12,
                searchY + (SEARCH_HEIGHT - Fonts.INSTANCE.getFontRegular35().getHeight()) / 2f + 1,
                new Color(255, 255, 255, 90).getRGB()
        );

        // === 网格区域（带 scissor 裁剪） ===
        GlStateManager.pushMatrix();
        int scaleFactor = sr.getScaleFactor();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(
                (int) (contentX * scaleFactor),
                (int) ((screenHeight - gridBottom) * scaleFactor),
                (int) (gridWidth * scaleFactor),
                (int) (gridHeight * scaleFactor)
        );

        hoveredIndex = -1;
        int totalRows = (int) Math.ceil((double) filteredThemes.size() / ITEMS_PER_ROW);
        String currentMode = ClientThemesUtils.INSTANCE.getClientColorMode();

        for (int row = 0; row < totalRows; row++) {
            for (int col = 0; col < ITEMS_PER_ROW; col++) {
                int index = row * ITEMS_PER_ROW + col;
                if (index >= filteredThemes.size()) break;

                ThemeColor theme = filteredThemes.get(index);
                float boxX = contentX + col * (boxWidth + ITEM_SPACING);
                float boxY = gridTop + row * (ITEM_HEIGHT + ITEM_SPACING) - scrollOffset;

                // 跳过完全不可见的项
                if (boxY + ITEM_HEIGHT < gridTop - ITEM_SPACING
                        || boxY > gridBottom + ITEM_SPACING) {
                    continue;
                }

                boolean isHovered = mouseX >= boxX && mouseX <= boxX + boxWidth
                        && mouseY >= boxY && mouseY <= boxY + ITEM_HEIGHT
                        && mouseY >= gridTop && mouseY <= gridBottom;
                boolean isSelected = theme.getMode().equalsIgnoreCase(currentMode);

                if (isHovered) hoveredIndex = index;

                drawThemeCard(boxX, boxY, boxWidth, ITEM_HEIGHT, theme, isHovered, isSelected);
            }
        }

        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GlStateManager.popMatrix();

        // 滚动条
        int visibleRows = Math.max(1, (int) (gridHeight / (ITEM_HEIGHT + ITEM_SPACING)));
        int maxScroll = Math.max(0, (totalRows - visibleRows) * (ITEM_HEIGHT + ITEM_SPACING));
        if (maxScroll > 0 && filteredThemes.size() > 0) {
            float trackHeight = gridHeight;
            float barHeight = Math.max(24f, trackHeight * ((float) visibleRows * ITEMS_PER_ROW
                    / Math.max(1, filteredThemes.size())));
            float barY = gridTop + (trackHeight - barHeight)
                    * (scrollOffset / Math.max(1, maxScroll));
            float barX = panelX + panelWidth - 4;
            RenderUtils.INSTANCE.drawRoundedRect(
                    barX, barY, barX + 2, barY + barHeight,
                    new Color(255, 255, 255, 100).getRGB(), 1f, RenderUtils.RoundedCorners.ALL
            );
        }

        // === 底部状态栏 ===
        RenderUtils.INSTANCE.drawRoundedRect(
                panelX, panelY + panelHeight - FOOTER_HEIGHT,
                panelX + panelWidth, panelY + panelHeight,
                footerOverlay.getRGB(), 14f, RenderUtils.RoundedCorners.BOTTOM_ONLY
        );

        // 分隔线
        RenderUtils.INSTANCE.drawRoundedRect(
                panelX + PADDING, panelY + panelHeight - FOOTER_HEIGHT,
                panelX + panelWidth - PADDING, panelY + panelHeight - FOOTER_HEIGHT + 1,
                dividerColor.getRGB(), 0f, RenderUtils.RoundedCorners.ALL
        );

        // 状态信息
        String footerText;
        if (hoveredIndex >= 0 && hoveredIndex < filteredThemes.size()) {
            footerText = "> " + filteredThemes.get(hoveredIndex).getDisplayName()
                    + "  (" + filteredThemes.get(hoveredIndex).getMode() + ")";
        } else {
            footerText = "Scroll: wheel  |  Search: type  |  ESC: close";
        }
        Fonts.INSTANCE.getFontRegular35().drawString(
                footerText,
                panelX + PADDING,
                panelY + panelHeight - FOOTER_HEIGHT
                        + (FOOTER_HEIGHT - Fonts.INSTANCE.getFontRegular35().getHeight()) / 2f + 1,
                new Color(255, 255, 255, 160).getRGB()
        );

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    /**
     * 绘制单个主题卡片
     */
    private void drawThemeCard(float x, float y, float w, float h, ThemeColor theme,
                                boolean hovered, boolean selected) {
        Color[] colors = getThemePreviewColors(theme.getMode());

        // 选中：白色外边框；悬停：半透明白色外边框
        if (selected) {
            RenderUtils.INSTANCE.drawRoundedRect(
                    x - 1.5f, y - 1.5f, x + w + 1.5f, y + h + 1.5f,
                    Color.WHITE.getRGB(), 9.5f, RenderUtils.RoundedCorners.ALL
            );
        } else if (hovered) {
            RenderUtils.INSTANCE.drawRoundedRect(
                    x - 1f, y - 1f, x + w + 1f, y + h + 1f,
                    new Color(255, 255, 255, 200).getRGB(), 9f, RenderUtils.RoundedCorners.ALL
            );
        }

        // 渐变填充
        RoundedUtil.applyGradientHorizontal(
                x, y, w, h, 8f, colors[0], colors[1],
                new Runnable() {
                    @Override
                    public void run() {
                    }
                }
        );

        // 底部名称背景（半透明黑底，BOTTOM_ONLY 圆角）
        float nameBgHeight = 18;
        RenderUtils.INSTANCE.drawRoundedRect(
                x, y + h - nameBgHeight, x + w, y + h,
                new Color(0, 0, 0, 150).getRGB(), 8f, RenderUtils.RoundedCorners.BOTTOM_ONLY
        );

        // 名称（带截断）
        String name = theme.getDisplayName();
        while (Fonts.INSTANCE.getFontRegular35().getStringWidth(name) > w - 10
                && name.length() > 1) {
            name = name.substring(0, name.length() - 1);
        }
        if (!name.equals(theme.getDisplayName()) && name.length() > 0) {
            name = name.substring(0, Math.max(0, name.length() - 1)) + "...";
        }
        Fonts.INSTANCE.getFontRegular35().drawString(
                name,
                x + (w - Fonts.INSTANCE.getFontRegular35().getStringWidth(name)) / 2,
                y + h - nameBgHeight
                        + (nameBgHeight - Fonts.INSTANCE.getFontRegular35().getHeight()) / 2f + 1,
                Color.WHITE.getRGB()
        );

        // 选中标记：右上角白色圆点
        if (selected) {
            float dotR = 4f;
            float dotX = x + w - 8;
            float dotY = y + 8;
            // 外圆（白）
            RenderUtils.INSTANCE.drawFilledCircle((int) dotX, (int) dotY, dotR + 1f,
                    new Color(255, 255, 255, 230));
            // 内圆（主题色）
            RenderUtils.INSTANCE.drawFilledCircle((int) dotX, (int) dotY, dotR,
                    colors[0]);
        }
    }

    private Color[] getThemePreviewColors(String mode) {
        try {
            kotlin.Pair<Color, Color> colorPair =
                    ClientThemesUtils.INSTANCE.getThemeColorPair(mode);
            if (colorPair != null) {
                return new Color[]{colorPair.getFirst(), colorPair.getSecond()};
            }
            Color color = ClientThemesUtils.INSTANCE.getColorForMode(mode, 0);
            return new Color[]{color, color};
        } catch (Exception e) {
            return new Color[]{Color.GRAY, Color.GRAY};
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (mouseButton != 0) {
            super.mouseClicked(mouseX, mouseY, mouseButton);
            return;
        }

        final ScaledResolution sr = new ScaledResolution(mc);
        final int screenWidth = sr.getScaledWidth();
        final int screenHeight = sr.getScaledHeight();

        final float panelWidth = Math.min(640, screenWidth - 60);
        final float panelHeight = Math.min(580, screenHeight - 60);
        final float panelX = (screenWidth - panelWidth) / 2f;
        final float panelY = (screenHeight - panelHeight) / 2f;

        // 关闭按钮
        float closeBtnSize = 18;
        float closeBtnX = panelX + panelWidth - PADDING - closeBtnSize;
        float closeBtnY = panelY + (HEADER_HEIGHT - closeBtnSize) / 2f;
        if (mouseX >= closeBtnX && mouseX <= closeBtnX + closeBtnSize
                && mouseY >= closeBtnY && mouseY <= closeBtnY + closeBtnSize) {
            mc.displayGuiScreen(null);
            ThemeManager.INSTANCE.setState(false);
            return;
        }

        // 当前主题展示卡区域
        float currentY = panelY + HEADER_HEIGHT + PADDING;
        float searchY = currentY + CURRENT_THEME_HEIGHT + SECTION_GAP;
        float gridTop = searchY + SEARCH_HEIGHT + SECTION_GAP;
        float gridBottom = panelY + panelHeight - FOOTER_HEIGHT - PADDING;
        float contentX = panelX + PADDING;
        float gridWidth = panelWidth - PADDING * 2;
        float boxWidth = (gridWidth - (ITEMS_PER_ROW - 1) * ITEM_SPACING) / ITEMS_PER_ROW;

        // 搜索框
        if (mouseX >= contentX && mouseX <= contentX + gridWidth
                && mouseY >= searchY && mouseY <= searchY + SEARCH_HEIGHT) {
            searchFocused = true;
            return;
        } else {
            searchFocused = false;
        }

        // 网格区域点击
        if (mouseY >= gridTop && mouseY <= gridBottom) {
            int totalRows = (int) Math.ceil((double) filteredThemes.size() / ITEMS_PER_ROW);
            for (int row = 0; row < totalRows; row++) {
                for (int col = 0; col < ITEMS_PER_ROW; col++) {
                    int index = row * ITEMS_PER_ROW + col;
                    if (index >= filteredThemes.size()) break;

                    ThemeColor theme = filteredThemes.get(index);
                    float boxX = contentX + col * (boxWidth + ITEM_SPACING);
                    float boxY = gridTop + row * (ITEM_HEIGHT + ITEM_SPACING) - scrollOffset;

                    if (mouseX >= boxX && mouseX <= boxX + boxWidth
                            && mouseY >= boxY && mouseY <= boxY + ITEM_HEIGHT) {
                        ThemeManager.INSTANCE.setTheme(theme.getMode());
                        return;
                    }
                }
            }
        }

        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (searchFocused) {
            if (keyCode == Keyboard.KEY_BACK) {
                if (searchText.length() > 0) {
                    searchText = searchText.substring(0, searchText.length() - 1);
                    updateFilteredThemes();
                }
                return;
            }
            if (keyCode == Keyboard.KEY_ESCAPE) {
                if (searchText.isEmpty()) {
                    mc.displayGuiScreen(null);
                    ThemeManager.INSTANCE.setState(false);
                } else {
                    searchText = "";
                    updateFilteredThemes();
                }
                return;
            }
            if (keyCode == Keyboard.KEY_RETURN) {
                searchFocused = false;
                return;
            }
            // 接受字母、数字、空格、下划线、连字符
            if (Character.isLetterOrDigit(typedChar)
                    || typedChar == ' ' || typedChar == '_' || typedChar == '-') {
                searchText += Character.toLowerCase(typedChar);
                updateFilteredThemes();
                return;
            }
            // 其他键不调用 super，避免触发 mc 操作
            return;
        }

        if (keyCode == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(null);
            ThemeManager.INSTANCE.setState(false);
            return;
        }

        // 按 F 聚焦搜索框
        if (keyCode == Keyboard.KEY_F) {
            searchFocused = true;
            return;
        }

        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    public static class ThemeColor {
        private final String displayName;
        private final String mode;

        public ThemeColor(String displayName, String mode) {
            this.displayName = displayName;
            this.mode = mode;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getMode() {
            return mode;
        }
    }
}
