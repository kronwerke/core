package de.kronwerke.core.portal;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * A side world's arrival place, built once per world at its spawn: a platform of deepslate
 * tiles with a portal of Grubenrahmen home on its north edge. Players arrive in its middle,
 * facing away from the portal.
 */
public final class Hub extends SavedData {
    public static final String NAME = "kronwerke_hub";
    public static final Factory<Hub> FACTORY = new Factory<>(Hub::new, Hub::load, null);

    private BlockPos center;

    static Hub data(MinecraftServer s) {
        return s.overworld().getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    public static void ensure(MinecraftServer s) {
        Hub h = data(s);
        if (h.center != null) return;
        ServerLevel level = s.overworld();
        BlockPos spawn = level.getSharedSpawnPos();
        level.getChunk(spawn);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn.getX(), spawn.getZ());
        y = Math.max(y, level.getSeaLevel() + 1);
        BlockPos c = new BlockPos(spawn.getX(), y, spawn.getZ());
        build(level, c);
        level.setDefaultSpawnPos(c, 0);
        h.center = c;
        h.setDirty();
    }

    private static void build(ServerLevel level, BlockPos c) {
        BlockState floor = Blocks.DEEPSLATE_TILES.defaultBlockState(), edge = Blocks.POLISHED_DEEPSLATE.defaultBlockState();
        BlockState frame = PortalBlocks.FRAME.get().defaultBlockState();
        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
                BlockPos f = c.offset(dx, -1, dz);
                level.setBlock(f, Math.abs(dx) == 5 || Math.abs(dz) == 5 ? edge : floor, 3);
                for (int up = 0; up < 3; up++) level.setBlock(f.below(1 + up), Blocks.DEEPSLATE.defaultBlockState(), 3);
                for (int up = 0; up < 7; up++) level.setBlock(c.offset(dx, up, dz), Blocks.AIR.defaultBlockState(), 3);
            }
        }
        // the portal home: inside 2 wide and 3 high, on the north edge
        BlockPos bl = c.offset(-1, 0, -4);
        for (int x = -1; x <= 2; x++) {
            level.setBlock(bl.offset(x, -1, 0), frame, 3);
            level.setBlock(bl.offset(x, 3, 0), frame, 3);
        }
        for (int y = 0; y < 3; y++) {
            level.setBlock(bl.offset(-1, y, 0), frame, 3);
            level.setBlock(bl.offset(2, y, 0), frame, 3);
        }
        new PortalShape(Direction.Axis.X, bl, 2, 3).fill(level);
        // lanterns at the corners
        for (int[] q : new int[][] {{-4, -4}, {4, -4}, {-4, 4}, {4, 4}}) {
            level.setBlock(c.offset(q[0], 0, q[1]), Blocks.SOUL_LANTERN.defaultBlockState(), 3);
        }
    }

    public static AwayData.Back arrival(MinecraftServer s) {
        Hub h = data(s);
        if (h.center == null) ensure(s);
        BlockPos c = data(s).center;
        return new AwayData.Back(Level.OVERWORLD.location().toString(), c.getX() + 0.5, c.getY(), c.getZ() + 0.5, 0);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        if (center != null) {
            tag.putInt("x", center.getX());
            tag.putInt("y", center.getY());
            tag.putInt("z", center.getZ());
        }
        return tag;
    }

    public static Hub load(CompoundTag tag, HolderLookup.Provider provider) {
        Hub h = new Hub();
        if (tag.contains("x")) h.center = new BlockPos(tag.getInt("x"), tag.getInt("y"), tag.getInt("z"));
        return h;
    }
}
