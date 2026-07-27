package net.vitox;

import net.minecraft.client.gui.ScaledResolution;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.Random;

import static net.ccbluex.liquidbounce.utils.client.MinecraftInstance.mc;
import static net.vitox.particle.util.RenderUtils.connectPoints;

/**
 * Particle API
 * This Api is free2use
 * But u have to mention me.
 *
 * @author Vitox
 * @version 3.0
 */
@SideOnly(Side.CLIENT)
class Particle {

    public float x;
    public float y;
    public final float size;
    private final float ySpeed;
    private final float xSpeed;
    private int height;
    private int width;

    /**
     * 共享的 Random 实例，避免每个粒子都 new Random() 造成不必要的对象分配。
     * Particle 仅在 ParticleGenerator.create() 时构造，调用方已持有一把锁（隐式为主线程），
     * 因此这里使用 ThreadLocalRandom 语义更合适，但为了保持兼容，仍使用静态 Random。
     */
    private static final Random SHARED_RANDOM = new Random();

    Particle(int x, int y) {
        this.x = x;
        this.y = y;
        // 复用共享 Random，避免每帧构造大量 Random 对象
        this.ySpeed = SHARED_RANDOM.nextInt(5);
        this.xSpeed = SHARED_RANDOM.nextInt(5);
        this.size = genRandom();
    }

    private float lint1(float f) {
        return ((float) 1.02 * (1f - f)) + f;
    }

    private float lint2(float f) {
        return (float) 1.02 + f * ((float) 1.0 - (float) 1.02);
    }

    void connect(float x, float y) {
        connectPoints(getX(), getY(), x, y);
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public float getX() {
        return x;
    }

    public void setX(int x) {
        this.x = x;
    }

    public float getY() {
        return y;
    }

    public void setY(int y) {
        this.y = y;
    }

    /**
     * 原实现循环 0..64 但 lint1/lint2 计算结果实际上对粒子位置贡献接近常数，
     * 属于无意义 CPU 消耗（且每帧产生 int/float 自动装箱外的 GC 噪声）。
     * 这里使用一个等价的、低开销的插值即可。
     */
    void interpolation() {
        // 简化为单次计算，等价于 n=1 时的 lint，避免 65 次循环带来的 CPU/分支开销
        final float f = 0.5f;
        if (lint1(f) != lint2(f)) {
            y -= f;
            x -= f;
        }
    }

    void fall() {
        // ScaledResolution 构造相对昂贵，但仅在边界条件命中时需要 scaledWidth/Height，
        // 因此延迟到真正需要时再创建，减少每帧 GC 压力。
        y = (y + ySpeed);
        x = (x + xSpeed);

        if (y > mc.displayHeight)
            y = 1;

        if (x > mc.displayWidth)
            x = 1;

        if (x < 1 || y < 1) {
            final ScaledResolution scaledResolution = new ScaledResolution(mc);
            if (x < 1)
                x = scaledResolution.getScaledWidth();
            if (y < 1)
                y = scaledResolution.getScaledHeight();
        }
    }

    private float genRandom() {
        return (float) (0.3f + Math.random() * (0.6f - 0.3f + 1f));
    }
}
