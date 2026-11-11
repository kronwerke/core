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
 * Every second Malum looks for a weeping well within 8 blocks of each player, through a helper
 * that loads the chunks it looks into. Right after a join or a teleport these chunks are not
 * there yet, and the server thread then loaded them on the spot (with many players arriving
 * at once that hung the server long enough for the watchdog). Now it only looks into chunks
 * that are loaded; a moment later they are, and the well is found as before.
 *
 * The helper does not stop at the range: it steps through the box in steps of 16 and goes on
 * while x is below the box's end plus 16, so it touches chunks up to range + 18 blocks away on
 * the positive side. The check covers all of them (with 50 bots flying through new Nether
 * terrain, a chunk just past the old check was still being generated and the tick waited 60 s).
 */
@Pseudo
@Mixin(targets = "com.sammy.malum.common.data.attachment.WeepingWellData", remap = false)
public abstract class MalumWellLoadedMixin {

    @WrapOperation(method = "checkForWeepingWell", require = 0,
            at = @At(value = "INVOKE", target = "Lteam/lodestar/lodestone/helpers/block/BlockEntityHelper;getBlockEntitiesStream(Ljava/lang/Class;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;I)Ljava/util/stream/Stream;"))
    private static Stream<?> kronwerke$onlyLoaded(Class<?> type, Level level, BlockPos pos, int range, Operation<Stream<?>> original) {
        if (!level.hasChunksAt(pos.offset(-range - 2, 0, -range - 2), pos.offset(range + 18, 0, range + 18))) return Stream.empty();
        return original.call(type, level, pos, range);
    }
}
