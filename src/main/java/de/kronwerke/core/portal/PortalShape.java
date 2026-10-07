package de.kronwerke.core.portal;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * A portal frame like the Nether's: an upright rectangle of Grubenrahmen around an empty
 * inside of 2 to 21 wide and 3 to 21 high; the corners may be anything.
 */
public record PortalShape(Direction.Axis axis, BlockPos bottomLeft, int width, int height) {
    static final int MIN_W = 2, MIN_H = 3, MAX = 21;

    /** The frame that holds the empty space at pos, along either axis, or null. */
    public static PortalShape find(Level level, BlockPos pos) {
        PortalShape s = find(level, pos, Direction.Axis.X);
        return s != null ? s : find(level, pos, Direction.Axis.Z);
    }

    static PortalShape find(Level level, BlockPos pos, Direction.Axis axis) {
        if (!inside(level.getBlockState(pos))) return null;
        Direction right = axis == Direction.Axis.X ? Direction.EAST : Direction.SOUTH;
        BlockPos p = pos;
        int n = 0;
        while (inside(level.getBlockState(p.below())) && n++ < MAX) p = p.below();
        if (!frame(level.getBlockState(p.below()))) return null;
        n = 0;
        while (inside(level.getBlockState(p.relative(right.getOpposite()))) && n++ < MAX) p = p.relative(right.getOpposite());
        if (!frame(level.getBlockState(p.relative(right.getOpposite())))) return null;
        BlockPos bottomLeft = p;
        int w = 0;
        while (w <= MAX && inside(level.getBlockState(bottomLeft.relative(right, w)))) w++;
        if (w < MIN_W || w > MAX || !frame(level.getBlockState(bottomLeft.relative(right, w)))) return null;
        int h = 0;
        while (h <= MAX && inside(level.getBlockState(bottomLeft.above(h)))) h++;
        if (h < MIN_H || h > MAX) return null;
        for (int x = 0; x < w; x++) {
            if (!frame(level.getBlockState(bottomLeft.relative(right, x).below()))) return null;
            if (!frame(level.getBlockState(bottomLeft.relative(right, x).above(h)))) return null;
            for (int y = 0; y < h; y++) if (!inside(level.getBlockState(bottomLeft.relative(right, x).above(y)))) return null;
        }
        for (int y = 0; y < h; y++) {
            if (!frame(level.getBlockState(bottomLeft.relative(right.getOpposite()).above(y)))) return null;
            if (!frame(level.getBlockState(bottomLeft.relative(right, w).above(y)))) return null;
        }
        return new PortalShape(axis, bottomLeft, w, h);
    }

    static boolean frame(BlockState s) {
        return s.is(PortalBlocks.FRAME.get());
    }

    static boolean inside(BlockState s) {
        return s.isAir() || s.is(PortalBlocks.PORTAL.get()) || s.canBeReplaced() && s.getFluidState().isEmpty();
    }

    public List<BlockPos> positions() {
        Direction right = axis == Direction.Axis.X ? Direction.EAST : Direction.SOUTH;
        List<BlockPos> out = new ArrayList<>();
        for (int x = 0; x < width; x++) for (int y = 0; y < height; y++) out.add(bottomLeft.relative(right, x).above(y));
        return out;
    }

    public void fill(Level level) {
        BlockState state = PortalBlocks.PORTAL.get().defaultBlockState().setValue(MinePortalBlock.AXIS, axis);
        for (BlockPos p : positions()) level.setBlock(p, state, 2 | 16);
    }

    /** Still a whole frame, full of portal. */
    public static boolean intact(Level level, BlockPos portal, Direction.Axis axis) {
        PortalShape s = find(level, portal, axis);
        return s != null;
    }
}
