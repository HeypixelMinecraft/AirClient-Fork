/*
 * Air Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 */
package net.ccbluex.liquidbounce.injection.forge.mixins.world;

import de.florianmichael.viamcp.ViaMCP;
import de.florianmichael.viamcp.fixes.FixedSoundEngine;
import de.florianmichael.vialoadingbase.ViaLoadingBase;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(World.class)
public abstract class MixinWorld {
    @Shadow
    public abstract IBlockState getBlockState(BlockPos pos);

    @Inject(method = "destroyBlock", at = @At("HEAD"), cancellable = true)
    private void destroyBlock(BlockPos pos, boolean dropBlock, CallbackInfoReturnable<Boolean> cir) {
        if (ViaLoadingBase.getInstance().getTargetVersion().getOriginalVersion() != ViaMCP.NATIVE_VERSION) {
            cir.setReturnValue(FixedSoundEngine.destroyBlock((World) (Object) this, pos, dropBlock));
        }
    }
}