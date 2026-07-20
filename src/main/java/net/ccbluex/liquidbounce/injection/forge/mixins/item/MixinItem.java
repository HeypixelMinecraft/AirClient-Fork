/*
 * Air Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 */
package net.ccbluex.liquidbounce.injection.forge.mixins.item;

import net.ccbluex.liquidbounce.utils.rotation.Rotation;
import net.ccbluex.liquidbounce.utils.rotation.RotationUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Item.class)
public class MixinItem {

    /**
     * Rotation modification injections. Replaces actual rotation with the current rotation to synchronize placements client-side.
     */
    @Redirect(method = "getMovingObjectPositionFromPlayer", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/player/EntityPlayer;rotationYaw:F"))
    private float hookCurrentRotationYaw(EntityPlayer instance) {
        Rotation rotation = RotationUtils.INSTANCE.getCurrentRotation();

        if (!RotationUtils.INSTANCE.isSamePlayer(instance, Minecraft.getMinecraft().thePlayer) || rotation == null) {
            return RotationUtils.INSTANCE.getPlayerYaw(instance);
        }

        return rotation.getYaw();
    }

    @Redirect(method = "getMovingObjectPositionFromPlayer", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/player/EntityPlayer;rotationPitch:F"))
    private float hookCurrentRotationPitch(EntityPlayer instance) {
        Rotation rotation = RotationUtils.INSTANCE.getCurrentRotation();

        if (!RotationUtils.INSTANCE.isSamePlayer(instance, Minecraft.getMinecraft().thePlayer) || rotation == null) {
            return RotationUtils.INSTANCE.getPlayerPitch(instance);
        }

        return rotation.getPitch();
    }
}
