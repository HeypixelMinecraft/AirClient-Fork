package net.vitox;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static net.ccbluex.liquidbounce.utils.client.MinecraftInstance.mc;
import static net.vitox.particle.util.RenderUtils.drawCircle;

/**
 * Particle API This Api is free2use But u have to mention me.
 *
 * @author Vitox
 * @version 3.0
 */
@SideOnly(Side.CLIENT)
public class ParticleGenerator {

    private final List<Particle> particles = new ArrayList<>();
    private final int amount;

    private int prevWidth;
    private int prevHeight;

    /**
     * 复用的 Random，避免每次 create() 都构造新实例。
     */
    private final Random random = new Random();

    public ParticleGenerator(final int amount) {
        this.amount = amount;
    }

    public void draw(final int mouseX, final int mouseY) {
        if (particles.isEmpty() || prevWidth != mc.displayWidth || prevHeight != mc.displayHeight) {
            particles.clear();
            create();
        }

        prevWidth = mc.displayWidth;
        prevHeight = mc.displayHeight;

        final int range = 50;
        final int particlesSize = particles.size();

        // 第一遍：所有粒子统一更新位置与插值
        for (int i = 0; i < particlesSize; i++) {
            final Particle particle = particles.get(i);
            particle.fall();
            particle.interpolation();
        }

        // 第二遍：绘制并处理连线。
        // 原实现使用 particles.stream().filter(...).forEach(...) 在每个 mouseOver 的粒子上都创建一条
        // 流水线（lambda + 多次 Iterator/Supplier 实例），属于热路径，会产生持续 GC 压力。
        // 这里改为普通的双层 for 循环，避免任何 lambda/Stream 对象分配。
        for (int i = 0; i < particlesSize; i++) {
            final Particle particle = particles.get(i);
            final float px = particle.getX();
            final float py = particle.getY();

            final boolean mouseOver = (mouseX >= px - range) && (mouseY >= py - range)
                    && (mouseX <= px + range) && (mouseY <= py + range);

            if (mouseOver) {
                for (int j = 0; j < particlesSize; j++) {
                    if (j == i) continue;
                    final Particle other = particles.get(j);
                    final float ox = other.getX();
                    final float oy = other.getY();

                    final boolean xInRange;
                    if (ox > px) {
                        xInRange = ox - px < range;
                    } else {
                        xInRange = px - ox < range;
                    }
                    if (!xInRange) continue;

                    final boolean yInRange;
                    if (oy > py) {
                        yInRange = oy - py < range;
                    } else {
                        yInRange = py - oy < range;
                    }
                    if (!yInRange) continue;

                    particle.connect(ox, oy);
                }
            }

            drawCircle(px, py, particle.size, 0xffFFFFFF);
        }
    }

    private void create() {
        for (int i = 0; i < amount; i++)
            particles.add(new Particle(random.nextInt(mc.displayWidth), random.nextInt(mc.displayHeight)));
    }
}
