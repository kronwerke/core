package de.kronwerke.core.portal;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
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

    /** The frame blocks around the inside: below, above, at both sides, and the four corners (which may be anything). */
    public List<BlockPos> frame() {
        Direction right = axis == Direction.Axis.X ? Direction.EAST : Direction.SOUTH;
        List<BlockPos> out = new ArrayList<>();
        out.add(bottomLeft.relative(right.getOpposite()).below());
        out.add(bottomLeft.relative(right, width).below());
        out.add(bottomLeft.relative(right.getOpposite()).above(height));
        out.add(bottomLeft.relative(right, width).above(height));
        for (int x = 0; x < width; x++) {
            out.add(bottomLeft.relative(right, x).below());
            out.add(bottomLeft.relative(right, x).above(height));
        }
        for (int y = 0; y < height; y++) {
            out.add(bottomLeft.relative(right.getOpposite()).above(y));
            out.add(bottomLeft.relative(right, width).above(y));
        }
        return out;
    }

    /** The whole portal at once, frame burning: for a portal that the world builds itself. */
    public void fill(Level level) {
        BlockState state = PortalBlocks.PORTAL.get().defaultBlockState().setValue(MinePortalBlock.AXIS, axis);
        for (BlockPos p : positions()) level.setBlock(p, state, 2 | 16);
        for (BlockPos p : frame()) {
            BlockState f = level.getBlockState(p);
            if (frame(f)) level.setBlock(p, f.setValue(FrameBlock.LIT, true), 2 | 16);
        }
    }

    /**
     * The portal lit by a player at from: the light runs around the frame from there, block by
     * block, then the surface forms from the frame inwards, a veil first, then whole. The whole
     * surface is put in at once, as a veil, so that the portal is never half a portal to the
     * rules that end one; the rest are ticks of the blocks themselves, which outlive a restart.
     * Returns after how many ticks it stands.
     */
    public int ignite(ServerLevel level, BlockPos from) {
        BlockState veil = PortalBlocks.PORTAL.get().defaultBlockState().setValue(MinePortalBlock.AXIS, axis).setValue(MinePortalBlock.STAGE, 0);
        for (BlockPos p : positions()) level.setBlock(p, veil, 2 | 16);
        int step = Math.max(width, height) > 8 ? 1 : 2;
        int around = 0;
        for (BlockPos p : frame()) {
            if (!frame(level.getBlockState(p))) continue;
            int wait = 2 + p.distManhattan(from) * step;
            around = Math.max(around, wait);
            level.scheduleTick(p, PortalBlocks.FRAME.get(), wait);
        }
        Direction right = axis == Direction.Axis.X ? Direction.EAST : Direction.SOUTH;
        int deepest = 0;
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                int ring = Math.min(Math.min(x, width - 1 - x), Math.min(y, height - 1 - y));
                deepest = Math.max(deepest, ring);
                level.scheduleTick(bottomLeft.relative(right, x).above(y), PortalBlocks.PORTAL.get(), around + 3 + ring * 3);
            }
        }
        return around + 3 + deepest * 3 + 5;
    }

    /** The middle of the inside. */
    public net.minecraft.world.phys.Vec3 centre() {
        Direction right = axis == Direction.Axis.X ? Direction.EAST : Direction.SOUTH;
        return net.minecraft.world.phys.Vec3.atLowerCornerOf(bottomLeft).add(0.5, 0, 0.5)
                .add(right.getStepX() * (width - 1) / 2.0, height / 2.0, right.getStepZ() * (width - 1) / 2.0);
    }

    /** Still a whole frame, full of portal. */
    public static boolean intact(Level level, BlockPos portal, Direction.Axis axis) {
        PortalShape s = find(level, portal, axis);
        return s != null;
    }
}
