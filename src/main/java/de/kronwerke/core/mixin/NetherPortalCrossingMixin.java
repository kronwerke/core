package de.kronwerke.core.mixin;

import de.kronwerke.core.portal.Crossing;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.portal.DimensionTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A Nether portal into a dimension on another server moves the player there; no portal is built here. */
@Mixin(NetherPortalBlock.class)
public class NetherPortalCrossingMixin {
    @Inject(method = "getPortalDestination", at = @At("HEAD"), cancellable = true)
    private void kronwerke$cross(ServerLevel level, Entity entity, BlockPos pos, CallbackInfoReturnable<DimensionTransition> cir) {
        if (Crossing.portal(level, entity, pos, false)) cir.setReturnValue(null);
    }
}
