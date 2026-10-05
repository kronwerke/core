package de.kronwerke.core.obelisk;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The obelisk as a build: the core in the middle of a 5x5 plinth, a 3x3 step around the
 * first shaft segment, four shaft segments and the crystal on top, eight blocks tall.
 * build() places everything around a core that is already there; clear() removes all
 * parts again.
 */
public final class ObeliskStructure {
    public static final int SHAFT = 4;

    private ObeliskStructure() {
    }

    public static void build(ServerLevel level, BlockPos core) {
        BlockState plinth = ObeliskBlocks.OBELISK_PLINTH.get().defaultBlockState();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (dx == 0 && dz == 0) continue;
                set(level, core.offset(dx, 0, dz), plinth);
                if (Math.abs(dx) <= 1 && Math.abs(dz) <= 1) set(level, core.offset(dx, 1, dz), plinth);
            }
        }
        for (int i = 1; i <= SHAFT; i++) set(level, core.above(i), ObeliskBlocks.OBELISK_SHAFT.get().defaultBlockState());
        set(level, core.above(SHAFT + 1), ObeliskBlocks.OBELISK_TOP.get().defaultBlockState());
    }

    public static void clear(ServerLevel level, BlockPos core) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = 0; dy <= SHAFT + 1; dy++) {
                    BlockPos p = core.offset(dx, dy, dz);
                    if (isPart(level.getBlockState(p))) level.removeBlock(p, false);
                }
            }
        }
    }

    /** Whether a block at pos belongs to the obelisk whose core is at core. */
    public static boolean contains(BlockPos core, BlockPos pos, BlockState state) {
        int dx = Math.abs(pos.getX() - core.getX()), dz = Math.abs(pos.getZ() - core.getZ()), dy = pos.getY() - core.getY();
        return dx <= 2 && dz <= 2 && dy >= 0 && dy <= SHAFT + 2 && (isPart(state) || state.is(ObeliskBlocks.OBELISK.get()));
    }

    public static boolean isPart(BlockState state) {
        return state.is(ObeliskBlocks.OBELISK_PLINTH.get()) || state.is(ObeliskBlocks.OBELISK_SHAFT.get()) || state.is(ObeliskBlocks.OBELISK_TOP.get());
    }

    private static void set(ServerLevel level, BlockPos pos, BlockState state) {
        if (!level.getBlockState(pos).is(state.getBlock())) level.setBlock(pos, state, Block.UPDATE_ALL);
    }
}
