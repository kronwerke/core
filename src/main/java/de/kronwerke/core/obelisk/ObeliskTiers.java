package de.kronwerke.core.obelisk;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EndRodBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * What the obelisk grows with every completed stage. The tier is the number of goals done;
 * each tier adds something to the build that can be seen from across spawn, in the order a
 * player would notice: the ground first, then light, then heat, then the sky, then the crown.
 *
 * <ul>
 * <li>Tier 1: the ground around the plinth is paved, a ring of deepslate tiles with brass
 * inlay lines towards the four directions.</li>
 * <li>Tier 2: four pylons with a cyan core and a lantern at the edges of the pavement.</li>
 * <li>Tier 3: an outer ring of blackstone with ember stones whose veins breathe, embers climb
 * the pylons.</li>
 * <li>Tier 4: four buttresses of arch stone with amethyst veins climb from the corners to the
 * trunk, amethyst at their tops and an end rod at their feet.</li>
 * <li>Tier 5: a crown of gilded stone under the cap with eight end rods.</li>
 * </ul>
 *
 * Everything placed is remembered in {@link ObeliskData#extras()}, so a rebuild takes it
 * away again. Only the pavement replaces what is there; everything else is put into air.
 */
public final class ObeliskTiers {
    public static final int MAX = 5;

    /** One block to place, in the order the rite places them. */
    public record Placement(BlockPos pos, BlockState state, boolean replace) {
    }

    private ObeliskTiers() {
    }

    /** The blocks a tier adds on top of the tier before it. */
    public static List<Placement> added(BlockPos core, int tier) {
        List<Placement> out = new ArrayList<>();
        switch (tier) {
            case 1 -> {
                for (int r = 5; r <= 6; r++) {
                    boolean outer = r == 6;
                    ring(core, r, (dx, dz) -> {
                        boolean line = dx == 0 || dz == 0;
                        boolean corner = Math.abs(dx) == Math.abs(dz);
                        BlockState s = line ? ObeliskBlocks.OBELISK_BRASS.get().defaultBlockState()
                                : corner || outer ? Blocks.POLISHED_DEEPSLATE.defaultBlockState() : Blocks.DEEPSLATE_TILES.defaultBlockState();
                        out.add(new Placement(core.offset(dx, -1, dz), s, true));
                    });
                }
            }
            case 2 -> {
                for (Direction d : Direction.Plane.HORIZONTAL) {
                    BlockPos foot = core.relative(d, 6);
                    out.add(new Placement(foot, ObeliskBlocks.OBELISK_PYLON.get().defaultBlockState(), false));
                    out.add(new Placement(foot.above(), ObeliskBlocks.OBELISK_PYLON.get().defaultBlockState(), false));
                    out.add(new Placement(foot.above(2), ObeliskBlocks.OBELISK_LANTERN.get().defaultBlockState(), false));
                }
            }
            case 3 -> {
                ring(core, 7, (dx, dz) -> {
                    boolean ember = Math.abs(dx) == Math.abs(dz) || dx == 0 || dz == 0;
                    BlockState s = ember ? ObeliskBlocks.OBELISK_EMBER.get().defaultBlockState() : Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
                    out.add(new Placement(core.offset(dx, -1, dz), s, true));
                });
                // embers climb the pylons
                for (Direction d : Direction.Plane.HORIZONTAL) {
                    out.add(new Placement(core.relative(d, 6).above(), ObeliskBlocks.OBELISK_EMBER.get().defaultBlockState(), true));
                }
            }
            case 4 -> {
                int[][] arc = {{7, 0}, {6, 1}, {5, 2}, {4, 3}, {3, 4}, {3, 5}, {2, 6}, {2, 7}};
                for (int[] c : ObeliskStructure.CORNERS) {
                    int sx = Integer.signum(c[0]), sz = Integer.signum(c[1]);
                    for (int i = 0; i < arc.length; i++) {
                        BlockState s = i >= arc.length - 2 ? Blocks.AMETHYST_BLOCK.defaultBlockState() : ObeliskBlocks.OBELISK_ARCH.get().defaultBlockState();
                        out.add(new Placement(core.offset(sx * arc[i][0], arc[i][1], sz * arc[i][0]), s, false));
                    }
                    out.add(new Placement(core.offset(sx * 7, 1, sz * 7), Blocks.END_ROD.defaultBlockState().setValue(EndRodBlock.FACING, Direction.UP), false));
                }
            }
            case 5 -> {
                ring(core, 2, (dx, dz) -> {
                    out.add(new Placement(core.offset(dx, 15, dz), ObeliskBlocks.OBELISK_CROWN.get().defaultBlockState(), false));
                });
                ring(core, 2, (dx, dz) -> {
                    if (dx == 0 || dz == 0 || Math.abs(dx) == Math.abs(dz)) {
                        out.add(new Placement(core.offset(dx, 16, dz), Blocks.END_ROD.defaultBlockState().setValue(EndRodBlock.FACING, Direction.UP), false));
                    }
                });
            }
            default -> {
            }
        }
        return out;
    }

    /** Everything from tier 1 up to tier, in order. */
    public static List<Placement> upTo(BlockPos core, int tier) {
        List<Placement> out = new ArrayList<>();
        for (int t = 1; t <= Math.min(MAX, tier); t++) out.addAll(added(core, t));
        return out;
    }

    private interface RingVisitor {
        void at(int dx, int dz);
    }

    /** The square ring at Chebyshev distance r, walked around clockwise from the north west corner. */
    private static void ring(BlockPos core, int r, RingVisitor v) {
        for (int dx = -r; dx <= r; dx++) v.at(dx, -r);
        for (int dz = -r + 1; dz <= r; dz++) v.at(r, dz);
        for (int dx = r - 1; dx >= -r; dx--) v.at(dx, r);
        for (int dz = r - 1; dz > -r; dz--) v.at(-r, dz);
    }

    /** Places one block if the spot allows it; answers whether something was placed. */
    public static boolean place(ServerLevel level, ObeliskData data, Placement p) {
        BlockState there = level.getBlockState(p.pos());
        if (!p.replace() && !there.isAir() && !there.canBeReplaced()) return false;
        if (there.is(p.state().getBlock())) return false;
        data.addExtra(p.pos(), there);
        level.setBlock(p.pos(), p.state(), Block.UPDATE_ALL);
        return true;
    }

    /** Puts back what stood where the tiers built. */
    public static void clear(ServerLevel level, ObeliskData data) {
        data.extras().forEach((p, before) -> {
            if (level.isLoaded(p)) level.setBlock(p, before, Block.UPDATE_ALL);
        });
        data.clearExtras();
    }
}
