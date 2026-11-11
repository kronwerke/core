package de.kronwerke.core.mixin;

import java.util.stream.Stream;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * Every second Malum looks for a weeping well within 8 blocks of each living entity, through a
 * Lodestone helper that loads the chunks it looks into. When such a chunk is still on its way
 * (the loading front around a moving player, or right after a join or a teleport) the server
 * thread waits for it; with 50 players in the Nether that stalled ticks for up to a minute.
 * Now it only looks when every chunk it would touch is already loaded; a moment later they
 * are, and the well is found as before.
 *
 * Two things the first version (0.25.0) got wrong:
 * - the helper steps through the box in steps of 16 and goes on while x is below the box's
 *   end plus 16, so it touches chunks up to range + 18 blocks away on the positive side;
 * - {@code Level.hasChunksAt} asks the chunk cache whether a ticket will keep the chunk at
 *   full status, not whether it is loaded, so a chunk still loading from disk passed.
 *   {@code getChunkNow} answers only with a chunk that is there.
 */
@Pseudo
@Mixin(targets = "com.sammy.malum.common.data.attachment.WeepingWellData", remap = false)
public abstract class MalumWellLoadedMixin {

    @WrapOperation(method = "checkForWeepingWell", require = 0,
            at = @At(value = "INVOKE", target = "Lteam/lodestar/lodestone/helpers/block/BlockEntityHelper;getBlockEntitiesStream(Ljava/lang/Class;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;I)Ljava/util/stream/Stream;"))
    private static Stream<?> kronwerke$onlyLoaded(Class<?> type, Level level, BlockPos pos, int range, Operation<Stream<?>> original) {
        if (!kronwerke$loaded(level, pos.getX() - range - 2, pos.getZ() - range - 2, pos.getX() + range + 18, pos.getZ() + range + 18)) {
            return Stream.empty();
        }
        return original.call(type, level, pos, range);
    }

    private static boolean kronwerke$loaded(Level level, int minX, int minZ, int maxX, int maxZ) {
        var chunks = level.getChunkSource();
        for (int cx = minX >> 4; cx <= maxX >> 4; cx++) {
            for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++) {
                if (chunks.getChunkNow(cx, cz) == null) return false;
            }
        }
        return true;
    }
}
