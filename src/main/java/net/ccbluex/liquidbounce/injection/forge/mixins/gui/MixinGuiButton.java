/*
 * Air Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 */
package net.ccbluex.liquidbounce.injection.forge.mixins.gui;

import net.ccbluex.liquidbounce.ui.font.AWTFontRenderer;
import net.ccbluex.liquidbounce.ui.font.Fonts;
import net.ccbluex.liquidbounce.utils.render.BlurUtils;
import net.ccbluex.liquidbounce.utils.render.RenderUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.*;

import java.awt.*;

import static net.minecraft.client.renderer.GlStateManager.resetColor;

@Mixin(GuiButton.class)
@SideOnly(Side.CLIENT)
public abstract class MixinGuiButton extends Gui {

    @Shadow
    public boolean visible;

    @Shadow
    public int xPosition;

    @Shadow
    public int yPosition;

    @Shadow
    public int width;

    @Shadow
    public int height;

    @Shadow
    protected boolean hovered;

    @Shadow
    public boolean enabled;

    @Shadow
    protected abstract void mouseDragged(Minecraft mc, int mouseX, int mouseY);

    @Shadow
    public String displayString;

    @Shadow
    @Final
    protected static ResourceLocation buttonTextures;

    @Shadow
    public int id;

    @Unique
    private long startTime = -1L;

    @Unique
    private boolean lastHover = false;

    @Unique
    private float hoverAlpha = 0F;

    /**
     * @author CCBlueX
     */
    @Overwrite
    public void drawButton(Minecraft mc, int mouseX, int mouseY) {
        if (visible) {
            hovered = mouseX >= xPosition && mouseY >= yPosition && mouseX < xPosition + width && mouseY < yPosition + height;

            if ((Object) this instanceof GuiOptionSlider) {
                hovered = true;
            }

            if ((Object) this instanceof GuiScreenOptionsSounds.Button) {
                hovered = true;
            }

            if (hovered != lastHover) {
                if (System.currentTimeMillis() - startTime > 100L) {
                    startTime = System.currentTimeMillis();
                }
                lastHover = hovered;
            }

            long elapsed = System.currentTimeMillis() - startTime;
            float animProgress = MathHelper.clamp_float(elapsed / 100f, 0f, 1f);

            if (enabled && hovered) {
                hoverAlpha = Math.min(hoverAlpha + animProgress * 0.12F, 1F);
            } else {
                hoverAlpha = Math.max(hoverAlpha - animProgress * 0.12F, 0F);
            }

            float x1 = xPosition;
            float y1 = yPosition;
            float x2 = xPosition + width;
            float y2 = yPosition + height;

            // Blur 背景
            if (enabled) {
                BlurUtils.INSTANCE.blurAreaRounded(x1, y1, x2, y2, 4F, 8F);
            }

            // 细白色边框
            if (enabled) {
                int borderAlpha = (int)(40 + 60 * hoverAlpha);
                RenderUtils.INSTANCE.drawRoundedBorderRect(x1, y1, x2, y2, 1F,
                    new Color(0, 0, 0, 0).getRGB(),
                    new Color(255, 255, 255, borderAlpha).getRGB(),
                    4F);
            }

            // 底部细线（悬停时变粗）
            if (enabled) {
                float lineH = 0.5F + 1F * hoverAlpha;
                int lineAlpha = (int)(80 + 175 * hoverAlpha);
                RenderUtils.INSTANCE.drawRect(x1 + 2, y2 - lineH, x2 - 2, y2,
                    new Color(200, 200, 200, lineAlpha).getRGB());
            }

            mc.getTextureManager().bindTexture(buttonTextures);
            mouseDragged(mc, mouseX, mouseY);

            AWTFontRenderer.Companion.setAssumeNonVolatile(true);

            final FontRenderer fontRenderer = Fonts.fontSemibold35;
            // 默认浅灰色，悬停时白色
            int textColor = enabled
                ? (hovered ? new Color(255, 255, 255).getRGB() : new Color(170, 170, 170).getRGB())
                : new Color(80, 80, 85).getRGB();
            fontRenderer.drawString(displayString, (int)(xPosition + width / 2 - fontRenderer.getStringWidth(displayString) / 2), (int)(yPosition + (height - 5) / 2F), textColor);

            AWTFontRenderer.Companion.setAssumeNonVolatile(false);

            resetColor();
        }
    }
}