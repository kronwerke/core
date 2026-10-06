package de.kronwerke.core.obelisk;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The obelisk as a build, nineteen blocks tall on a thirteen by thirteen footprint:
 *
 * <pre>
 *   y 17-18  crystal, the beam above it
 *   y 16     the tip
 *   y 15     3x3 cap
 *   y  3-14  3x3 trunk, rune bands glowing at y 5 and y 12
 *   y  0-2   stepped plinth, 9x9, 7x7, 5x5, with the core in the middle of the bottom layer
 * </pre>
 *
 * Four pedestals stand at the corners, six blocks out: one for each pillar of the goals
 * (stone, tech, magic) and one for the last deposit of any kind. build() places everything
 * around a core that is already there; clear() removes it again.
 */
public final class ObeliskStructure {
    public static final int HEIGHT = 19;
    public static final int PEDESTAL = 6;
    /** The pedestal corners, in pillar order: north west, north east, south west, south east. */
    public static final int[][] CORNERS = {{-PEDESTAL, -PEDESTAL}, {PEDESTAL, -PEDESTAL}, {-PEDESTAL, PEDESTAL}, {PEDESTAL, PEDESTAL}};

    private ObeliskStructure() {
    }

    public static void build(ServerLevel level, BlockPos core) {
        BlockState plinth = ObeliskBlocks.OBELISK_PLINTH.get().defaultBlockState();
        BlockState trunk = ObeliskBlocks.OBELISK_TRUNK.get().defaultBlockState();
        BlockState runes = ObeliskBlocks.OBELISK_RUNES.get().defaultBlockState();
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                int r = Math.max(Math.abs(dx), Math.abs(dz));
                if (r > 0) set(level, core.offset(dx, 0, dz), plinth);
                if (r <= 3) set(level, core.offset(dx, 1, dz), plinth);
                if (r <= 2) set(level, core.offset(dx, 2, dz), plinth);
                if (r <= 1) {
                    for (int y = 3; y <= 14; y++) set(level, core.offset(dx, y, dz), (y == 5 || y == 12) && r == 1 ? runes : trunk);
                    set(level, core.offset(dx, 15, dz), plinth);
                }
            }
        }
        set(level, core.above(16), ObeliskBlocks.OBELISK_SHAFT.get().defaultBlockState());
        set(level, core.above(17), ObeliskBlocks.OBELISK_TOP.get().defaultBlockState());
        for (int[] c : CORNERS) {
            set(level, core.offset(c[0], 0, c[1]), plinth);
            set(level, core.offset(c[0], 1, c[1]), ObeliskBlocks.OBELISK_PEDESTAL.get().defaultBlockState());
        }
    }

    public static BlockPos pedestal(BlockPos core, int index) {
        return core.offset(CORNERS[index][0], 1, CORNERS[index][1]);
    }

    public static void clear(ServerLevel level, BlockPos core) {
        ObeliskTiers.clear(level, Obelisk.get().data());
        for (int dx = -PEDESTAL; dx <= PEDESTAL; dx++) {
            for (int dz = -PEDESTAL; dz <= PEDESTAL; dz++) {
                for (int dy = 0; dy <= HEIGHT; dy++) {
                    BlockPos p = core.offset(dx, dy, dz);
                    if (isPart(level.getBlockState(p))) level.removeBlock(p, false);
                }
            }
        }
    }

    /** Whether a block at pos belongs to the obelisk whose core is at core. */
    public static boolean contains(BlockPos core, BlockPos pos, BlockState state) {
        int dx = Math.abs(pos.getX() - core.getX()), dz = Math.abs(pos.getZ() - core.getZ()), dy = pos.getY() - core.getY();
        return dx <= PEDESTAL && dz <= PEDESTAL && dy >= 0 && dy <= HEIGHT && (isPart(state) || state.is(ObeliskBlocks.OBELISK.get()));
    }

    public static boolean isPart(BlockState state) {
        return state.is(ObeliskBlocks.OBELISK_PLINTH.get()) || state.is(ObeliskBlocks.OBELISK_SHAFT.get()) || state.is(ObeliskBlocks.OBELISK_TOP.get())
                || state.is(ObeliskBlocks.OBELISK_TRUNK.get()) || state.is(ObeliskBlocks.OBELISK_RUNES.get()) || state.is(ObeliskBlocks.OBELISK_PEDESTAL.get())
                || state.is(ObeliskBlocks.OBELISK_BRASS.get()) || state.is(ObeliskBlocks.OBELISK_PYLON.get()) || state.is(ObeliskBlocks.OBELISK_LANTERN.get())
                || state.is(ObeliskBlocks.OBELISK_EMBER.get()) || state.is(ObeliskBlocks.OBELISK_ARCH.get()) || state.is(ObeliskBlocks.OBELISK_CROWN.get());
    }

    private static void set(ServerLevel level, BlockPos pos, BlockState state) {
        if (!level.getBlockState(pos).is(state.getBlock())) level.setBlock(pos, state, Block.UPDATE_ALL);
    }
}
