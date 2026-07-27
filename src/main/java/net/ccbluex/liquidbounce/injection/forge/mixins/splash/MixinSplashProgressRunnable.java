/*
 * Air Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 */

package net.ccbluex.liquidbounce.injection.forge.mixins.splash;

import org.lwjgl.opengl.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static org.lwjgl.opengl.GL11.*;

/**
 * Mixin 目标: SplashProgress$3 (start() 方法中的匿名 Runnable 渲染线程)
 * <p>
 * 本 Mixin 的修改:
 * 1. 在第一个 setColor 之后注入白色覆盖，使 AirClient logo 纹理以原色显示
 * 2. 将 Mojang/AirClient logo 的 4 个顶点改为 cover 模式（保持比例覆盖全屏）
 * 3. 第二个 setColor 保持 backgroundColor(深色)，使 Forge logo 近乎不可见
 * 4. 将 logoTexture 的过滤模式从 GL_NEAREST 改为 GL_LINEAR，提升缩放清晰度
 * 5. 纹理加载后动态获取实际宽高比，支持不同尺寸的启动背景图片
 */
@Mixin(targets = "net.minecraftforge.fml.client.SplashProgress$3", remap = false)
public abstract class MixinSplashProgressRunnable {

    /**
     * 默认宽高比 (splash.png: 1602 / 1142)
     * 在纹理加载后会通过反射获取实际尺寸并更新
     */
    private static float splashAspect = 1602f / 1142f;

    /**
     * 在 Mojang/AirClient logo 的 setColor(backgroundColor) 调用之后，
     * 覆盖顶点颜色为白色，使纹理以原始颜色显示
     */
    @Inject(method = "run", at = @At(value = "INVOKE", target = "Lnet/minecraftforge/fml/client/SplashProgress$3;setColor(I)V", remap = false, ordinal = 0, shift = At.Shift.AFTER))
    private void overrideLogoColor(CallbackInfo ci) {
        glColor3ub((byte) 0xFF, (byte) 0xFF, (byte) 0xFF);
    }

    /**
     * 在 Forge 纹理加载完成后:
     * 1. 将 logoTexture 的过滤模式改为 GL_LINEAR，提升缩放清晰度
     * 2. 通过反射获取 logoTexture 的实际宽高，更新 splashAspect 用于 cover 模式计算
     * <p>
     * ordinal=2 对应 run() 中第三个 Texture 构造调用: forgeTexture = new Texture(forgeLoc)
     * 在它之后 logoTexture 已创建完成
     */
    @Inject(method = "run", at = @At(value = "INVOKE", target = "Lnet/minecraftforge/fml/client/SplashProgress$Texture;<init>(Lnet/minecraft/util/ResourceLocation;)V", remap = false, ordinal = 2, shift = At.Shift.AFTER))
    private void upgradeLogoFiltering(CallbackInfo ci) {
        try {
            java.lang.reflect.Field logoField = net.minecraftforge.fml.client.SplashProgress.class.getDeclaredField("logoTexture");
            logoField.setAccessible(true);
            Object logoTex = logoField.get(null);
            if (logoTex != null) {
                // 获取纹理ID并设置线性过滤
                java.lang.reflect.Method getNameMethod = logoTex.getClass().getMethod("getName");
                int texId = (int) getNameMethod.invoke(logoTex);
                glBindTexture(GL_TEXTURE_2D, texId);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
                glBindTexture(GL_TEXTURE_2D, 0);

                // 获取纹理实际宽高，动态更新宽高比
                java.lang.reflect.Method getWidthMethod = logoTex.getClass().getMethod("getWidth");
                java.lang.reflect.Method getHeightMethod = logoTex.getClass().getMethod("getHeight");
                int width = (int) getWidthMethod.invoke(logoTex);
                int height = (int) getHeightMethod.invoke(logoTex);
                if (width > 0 && height > 0) {
                    splashAspect = (float) width / (float) height;
                }
            }
        } catch (Exception ignored) {
            // 如果反射失败，回退到默认的宽高比和 GL_NEAREST 过滤
        }
    }

    /**
     * 以下4个重定向将 Mojang/AirClient logo 的顶点坐标改为 cover 模式
     * <p>
     * Cover 模式: 保持图片原始比例，缩放至完全覆盖屏幕，超出部分被视口裁剪
     * <p>
     * 投影坐标系以 (320, 240) 为中心
     * 计算方式:
     * - 如果图片比屏幕窄 (texAspect < screenAspect): 适配宽度，上下裁剪
     * - 如果图片比屏幕宽 (texAspect > screenAspect): 适配高度，左右裁剪
     */

    // 左上角
    @Redirect(method = "run", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glVertex2f(FF)V", remap = false, ordinal = 0))
    private void coverVertex0(float x, float y) {
        glVertex2f(320 - coverHalfW(), 240 - coverHalfH());
    }

    // 左下角
    @Redirect(method = "run", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glVertex2f(FF)V", remap = false, ordinal = 1))
    private void coverVertex1(float x, float y) {
        glVertex2f(320 - coverHalfW(), 240 + coverHalfH());
    }

    // 右下角
    @Redirect(method = "run", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glVertex2f(FF)V", remap = false, ordinal = 2))
    private void coverVertex2(float x, float y) {
        glVertex2f(320 + coverHalfW(), 240 + coverHalfH());
    }

    // 右上角
    @Redirect(method = "run", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glVertex2f(FF)V", remap = false, ordinal = 3))
    private void coverVertex3(float x, float y) {
        glVertex2f(320 + coverHalfW(), 240 - coverHalfH());
    }

    /**
     * 计算 cover 模式下的半宽
     */
    private static float coverHalfW() {
        float screenW = Display.getWidth();
        float screenH = Display.getHeight();
        float screenAspect = screenW / screenH;
        if (splashAspect > screenAspect) {
            // 图片比屏幕宽 -> 适配高度，左右裁剪
            return (screenH / 2f) * splashAspect;
        } else {
            // 图片比屏幕窄 -> 适配宽度，上下裁剪
            return screenW / 2f;
        }
    }

    /**
     * 计算 cover 模式下的半高
     */
    private static float coverHalfH() {
        float screenW = Display.getWidth();
        float screenH = Display.getHeight();
        float screenAspect = screenW / screenH;
        if (splashAspect > screenAspect) {
            // 图片比屏幕宽 -> 适配高度
            return screenH / 2f;
        } else {
            // 图片比屏幕窄 -> 适配宽度
            return (screenW / 2f) / splashAspect;
        }
    }
}
