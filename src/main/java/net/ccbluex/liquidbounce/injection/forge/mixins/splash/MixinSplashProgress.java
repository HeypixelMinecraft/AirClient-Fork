/*
 * AirClient
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 */

package net.ccbluex.liquidbounce.injection.forge.mixins.splash;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.client.SplashProgress;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;

@Mixin(value = SplashProgress.class, remap = false)
public abstract class MixinSplashProgress {

    @Shadow(aliases = "SplashProgress", remap = false)
    private static boolean enabled;

    @Shadow
    private static int backgroundColor;

    @Shadow
    private static int fontColor;

    @Shadow
    private static int barBorderColor;

    @Shadow
    private static int barColor;

    @Shadow
    private static int barBackgroundColor;

    /**
     * 强制启用 SplashProgress 并自定义颜色主题为深色
     */
    @Inject(method = "start", at = @At(value = "FIELD", target = "Lnet/minecraftforge/fml/client/SplashProgress;enabled:Z", opcode = 178, remap = false, ordinal = 0), remap = false, require = 1, allow = 1)
    private static void start(CallbackInfo callbackInfo) {
        enabled = true;
        // 深色主题: backgroundColor 同时用于 glClearColor 和纹理着色
        // 在渲染循环的 MixinSplashProgressRunnable 中会单独将纹理着色设为白色
        backgroundColor = 0x1A1A1A;
        fontColor = 0xE0E0E0;
        barBorderColor = 0x3A3A3A;
        barColor = 0x4A9EFF;
        barBackgroundColor = 0x2A2A2A;
    }

    /**
     * 取消 finish() 的后续操作，保持 OpenGL 上下文
     */
    @Inject(method = "finish", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/Drawable;makeCurrent()V", shift = At.Shift.AFTER, remap = false, ordinal = 0), remap = false, cancellable = true, require = 1, allow = 1)
    private static void finish(CallbackInfo callbackInfo) {
        callbackInfo.cancel();
    }

    /**
     * 可选的启动背景图片列表
     * 与 setup 目录下的图片对应
     */
    private static final String[] SPLASH_BACKGROUNDS = {
        "splash", "miku", "Mortis", "ba", "cat", "girl", "girl2", "qcf", "soyo", "youxiang"
    };

    /**
     * 启动背景图片的宽高比（宽 / 高）
     * 每个背景图片对应一个宽高比，用于 cover 模式计算
     * 在运行时通过反射获取实际纹理尺寸来更新
     */
    private static float splashAspect = 1602f / 1142f;

    /**
     * 获取当前选中的启动背景名称
     * 从 AirClient-1.8.9/splash.json 中读取 "background" 字段
     * 如果文件不存在或读取失败，返回默认值 "splash"
     */
    private static String getSelectedSplashBackground() {
        try {
            File dir = new File(net.minecraft.client.Minecraft.getMinecraft().mcDataDir, "AirClient-1.8.9");
            File configFile = new File(dir, "splash.json");
            if (configFile.exists()) {
                FileInputStream fis = new FileInputStream(configFile);
                JsonObject json = new JsonParser().parse(new InputStreamReader(fis)).getAsJsonObject();
                fis.close();
                String bg = json.has("background") ? json.get("background").getAsString() : "splash";
                // 验证是否在可选列表中
                for (String valid : SPLASH_BACKGROUNDS) {
                    if (valid.equals(bg)) return bg;
                }
            }
        } catch (Exception ignored) {
            // 读取失败使用默认值
        }
        return "splash";
    }

    /**
     * 将 Mojang logo 的纹理路径替换为用户选择的启动背景图片
     * ordinal=1 对应 start() 中第二个 getString 调用: getString("logoTexture", ...)
     */
    @Redirect(method = "start", at = @At(value = "INVOKE", target = "Lnet/minecraftforge/fml/client/SplashProgress;getString(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;", remap = false, ordinal = 1), remap = false)
    private static String redirectLogoTexture(String name, String def) {
        String selected = getSelectedSplashBackground();
        // 确定文件扩展名: splash 是 .png, 其他是 .jpeg
        if ("splash".equals(selected)) {
            return "airclient/setup/splash.png";
        } else {
            return "airclient/setup/" + selected + ".jpeg";
        }
    }

    /**
     * 为 open() 方法添加 classpath 加载回退
     * 原始 open() 只从 miscPack/fmlPack/mcPack 加载资源
     * 这里添加对 airclient/ 路径（含子目录）的 classpath 加载支持
     */
    @Inject(method = "open", at = @At("HEAD"), cancellable = true, remap = false)
    private static void injectOpen(ResourceLocation loc, CallbackInfoReturnable<InputStream> cir) {
        String path = loc.getResourcePath();
        if (path != null && path.startsWith("airclient/")) {
            InputStream is = SplashProgress.class.getClassLoader().getResourceAsStream("assets/minecraft/" + path);
            if (is != null) {
                cir.setReturnValue(is);
            }
        }
    }
}
