package de.kronwerke.core.mixin;

import de.kronwerke.core.perf.DirtyChunks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Counts block changes that mark a chunk changed while /kw admin dirty runs, see DirtyChunks. */
@Mixin(LevelChunk.class)
public abstract class ChunkBlockDirtyMixin {

    @Inject(method = "setBlockState", at = @At(value = "FIELD", target = "Lnet/minecraft/world/level/chunk/LevelChunk;unsaved:Z", opcode = Opcodes.PUTFIELD), require = 0)
    private void kronwerke$countBlock(BlockPos pos, BlockState state, boolean moved, CallbackInfoReturnable<BlockState> cir) {
        if (DirtyChunks.on && !((ChunkAccess) (Object) this).isUnsaved()) {
            DirtyChunks.record("block");
        }
    }
}
