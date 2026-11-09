package de.kronwerke.core.mixin;

import de.kronwerke.core.perf.LootrTicks;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** No server ticker for Lootr containers while nothing decays or refreshes, see LootrTicks. */
@Pseudo
@Mixin(targets = {
        "noobanidus.mods.lootr.common.block.LootrChestBlock",
        "noobanidus.mods.lootr.common.block.LootrTrappedChestBlock",
        "noobanidus.mods.lootr.common.block.LootrBarrelBlock",
        "noobanidus.mods.lootr.common.block.LootrInventoryBlock",
        "noobanidus.mods.lootr.common.block.LootrDecoratedPotBlock",
        "noobanidus.mods.lootr.common.block.LootrBrushableBlock"
}, remap = false)
public abstract class LootrTickerMixin {

    @Inject(method = "getTicker(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/entity/BlockEntityType;)Lnet/minecraft/world/level/block/entity/BlockEntityTicker;",
            at = @At("HEAD"), cancellable = true, require = 0)
    private <T extends net.minecraft.world.level.block.entity.BlockEntity> void kronwerke$noServerTicker(Level level, BlockState state, BlockEntityType<T> type,
                                                                                                         CallbackInfoReturnable<BlockEntityTicker<T>> cir) {
        if (!level.isClientSide() && !LootrTicks.serverTickerNeeded()) {
            cir.setReturnValue(null);
        }
    }
}
