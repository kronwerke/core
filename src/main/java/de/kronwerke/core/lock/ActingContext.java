package de.kronwerke.core.lock;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

/**
 * Who or what looks for a recipe right now on the server thread, for the machine lock
 * messages: a player using a crafting grid, clicking in a menu or using an item on a block
 * (rituals, altars), or a block entity ticking. Set and cleared around those calls only.
 */
public final class ActingContext {

    private static ServerPlayer player;
    private static boolean grid;
    private static BlockPos blockEntity;

    private ActingContext() {
    }

    public static ServerPlayer player() {
        return player;
    }

    public static boolean inGrid() {
        return grid;
    }

    public static BlockPos blockEntity() {
        return blockEntity;
    }

    /** Sets the player and answers the one before, to put back. */
    public static ServerPlayer player(ServerPlayer p, boolean inGrid) {
        ServerPlayer before = player;
        player = p;
        grid = inGrid;
        return before;
    }

    public static void restorePlayer(ServerPlayer before) {
        player = before;
        if (before == null) grid = false;
    }

    public static BlockPos blockEntity(BlockPos pos) {
        BlockPos before = blockEntity;
        blockEntity = pos;
        return before;
    }
}
