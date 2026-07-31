/*
 * Air Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 */
package net.ccbluex.liquidbounce.injection.forge.mixins.gui;

import com.mojang.realmsclient.gui.ChatFormatting;
import net.ccbluex.liquidbounce.features.module.modules.render.HUD;
import net.ccbluex.liquidbounce.ui.font.GameFontRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ChatLine;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiNewChat;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.MathHelper;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.List;

@Mixin(GuiNewChat.class)
public abstract class MixinGuiNewChat {

    private float displayPercent = 0F;
    private float animationPercent = 1F;
    private int newLines = 0;

    @Shadow
    @Final
    private Minecraft mc;

    @Shadow
    public abstract int getLineCount();

    @Shadow
    @Final
    private List<ChatLine> drawnChatLines;

    @Shadow
    public abstract boolean getChatOpen();

    @Shadow
    public abstract float getChatScale();

    @Shadow
    public abstract int getChatWidth();

    @Shadow
    private int scrollPos;

    @Shadow
    private boolean isScrolled;

    @Shadow
    public abstract void deleteChatLine(int p_deleteChatLine_1_);

    @Shadow
    @Final
    private List<ChatLine> chatLines;

    @Shadow
    public abstract void scroll(int p_scroll_1_);

    @Shadow
    public abstract void printChatMessageWithOptionalDeletion(IChatComponent chatComponent, int chatLineId);

    private String lastMessage = "";
    private int sameMessageAmount = 0;
    private int line = 0;
    private final HashMap<String, String> stringCache = new HashMap<>();

    // Fix: Reset animation on new message
    @Inject(method = "printChatMessageWithOptionalDeletion", at = @At("HEAD"))
    private void resetPercentage(CallbackInfo ci) {
        displayPercent = 0F;
    }

    // Fix: Chat combine - keep as @Overwrite since it replaces the entire message handling logic
    @Overwrite
    public void printChatMessage(IChatComponent chatComponent) {
        if (!HUD.INSTANCE.getState() || !HUD.INSTANCE.getChatCombine()) {
            printChatMessageWithOptionalDeletion(chatComponent, this.line);
            return;
        }

        String text = fixString(chatComponent.getFormattedText());
        if (text.equals(this.lastMessage)) {
            Minecraft.getMinecraft().ingameGUI.getChatGUI().deleteChatLine(this.line);
            this.sameMessageAmount++;
            this.lastMessage = text;
            chatComponent.appendText(ChatFormatting.WHITE + " (" + "x" + this.sameMessageAmount + ")");
        } else {
            this.sameMessageAmount = 1;
            this.lastMessage = text;
        }
        this.line++;
        if (this.line > 256)
            this.line = 0;

        printChatMessageWithOptionalDeletion(chatComponent, this.line);
    }

    // Fix: Redirect font rendering in drawChat to use custom font (like NekoBounce)
    @Redirect(method = "drawChat", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/FontRenderer;drawStringWithShadow(Ljava/lang/String;FFI)I"))
    private int injectFontChatDraw(FontRenderer instance, String text, float x, float y, int color) {
        String fixedText = fixString(text);
        if (HUD.INSTANCE.shouldModifyChatFont()) {
            return HUD.INSTANCE.getChatFont().drawStringWithShadow(fixedText, x, y, color);
        }
        return instance.drawStringWithShadow(fixedText, x, y, color);
    }

    // Fix: Redirect FONT_HEIGHT field access in drawChat and getChatComponent to use custom font height
    @Redirect(method = {"getChatComponent", "drawChat"}, at = @At(value = "FIELD", target = "Lnet/minecraft/client/gui/FontRenderer;FONT_HEIGHT:I"))
    private int injectFontChatHeight(FontRenderer instance) {
        if (HUD.INSTANCE.shouldModifyChatFont()) {
            FontRenderer chatFont = HUD.INSTANCE.getChatFont();
            if (chatFont instanceof GameFontRenderer) {
                return ((GameFontRenderer) chatFont).getHeight();
            }
        }
        return instance.FONT_HEIGHT;
    }

    // Fix: Redirect getStringWidth in getChatComponent to use custom font width
    @Redirect(method = "getChatComponent", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/FontRenderer;getStringWidth(Ljava/lang/String;)I"))
    private int injectFontChatWidth(FontRenderer instance, String text) {
        if (HUD.INSTANCE.shouldModifyChatFont()) {
            return HUD.INSTANCE.getChatFont().getStringWidth(text);
        }
        return instance.getStringWidth(text);
    }

    // Fix: Redirect the first translate call in drawChat to add animation offset
    @Redirect(method = "drawChat", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GlStateManager;translate(FFF)V", ordinal = 0))
    private void injectChatAnimationTranslate(float x, float y, float z) {
        // Update animation state
        if (this.isScrolled || !HUD.INSTANCE.getState() || !HUD.INSTANCE.getChatAnimation()) {
            displayPercent = 1F;
        } else if (displayPercent < 1F) {
            displayPercent += HUD.INSTANCE.getChatAnimationSpeed() * 0.1F * Minecraft.getMinecraft().timer.renderPartialTicks;
            displayPercent = MathHelper.clamp_float(displayPercent, 0F, 1F);
        }

        float t = displayPercent;
        animationPercent = MathHelper.clamp_float(1F - (--t) * t * t * t, 0F, 1F);

        float offsetY = 0F;
        if (HUD.INSTANCE.getState() && HUD.INSTANCE.getChatAnimation()) {
            offsetY = (1F - animationPercent) * 9F * this.getChatScale();
        }

        GlStateManager.translate(x, y + offsetY, z);
    }

    // Fix: Redirect chat background drawRect to support chatRect toggle
    @Redirect(method = "drawChat", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;drawRect(IIIII)V", ordinal = 0))
    private void injectChatRect(int left, int top, int right, int bottom, int color) {
        // When HUD is on and chatRect is off, hide the background
        if (HUD.INSTANCE.getState() && !HUD.INSTANCE.getChatRect()) {
            return;
        }
        Gui.drawRect(left, top, right, bottom, color);
    }

    // Fix: Track new lines for animation
    @ModifyVariable(method = "setChatLine", at = @At("STORE"), ordinal = 0)
    private List<IChatComponent> setNewLines(List<IChatComponent> original) {
        newLines = original.size() - 1;
        return original;
    }

    // Fix: Convert fullwidth characters to halfwidth for CJK text rendering
    private String fixString(String str) {
        if (stringCache.containsKey(str)) return stringCache.get(str);

        str = str.replaceAll("\uF8FF", "");

        StringBuilder sb = new StringBuilder();
        for (char c : str.toCharArray()) {
            if ((int) c > (33 + 65248) && (int) c < (128 + 65248))
                sb.append(Character.toChars((int) c - 65248));
            else
                sb.append(c);
        }

        String result = sb.toString();
        stringCache.put(str, result);

        return result;
    }
}
