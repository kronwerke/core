package de.kronwerke.core.perf;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * JourneyMap rebuilds a polygon outline of every loaded chunk of a dimension every 60 ticks, in
 * one go on the server thread, only to show it to operators who switched the loaded chunk overlay
 * on. With 50 players that was a spike every three seconds and 0.8 % of all tick time, while
 * nobody looks at it. The rebuild now only runs when an operator in that dimension has one of the
 * overlay views on, plus one last time after someone switches it off so their map clears.
 */
public final class JourneyMapOverlay {

    private static final Set<UUID> SHOWN = new HashSet<>();
    private static MethodHandle playerData;
    private static MethodHandle nullable;
    private static MethodHandle[] views;
    private static boolean looked;

    private JourneyMapOverlay() {
    }

    public static boolean wanted(ServerLevel level) {
        boolean wanted = false;
        for (ServerPlayer player : level.players()) {
            UUID id = player.getUUID();
            boolean viewing = level.getServer().getPlayerList().isOp(player.getGameProfile()) && viewing(id);
            if (viewing) {
                SHOWN.add(id);
                wanted = true;
            } else if (SHOWN.remove(id)) {
                wanted = true; // one more push so the overlay disappears on their map
            }
        }
        return wanted;
    }

    private static boolean viewing(UUID id) {
        if (!looked) lookup();
        if (views == null) return true;
        try {
            Object data = playerData.invoke();
            Object dto = nullable.invoke(data, id);
            if (dto == null) return false;
            for (MethodHandle view : views) {
                if ((boolean) view.invoke(dto)) return true;
            }
            return false;
        } catch (Throwable t) {
            return true;
        }
    }

    private static void lookup() {
        looked = true;
        try {
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            Class<?> data = Class.forName("journeymap.common.nbt.PlayerData");
            Class<?> dto = Class.forName("journeymap.common.nbt.model.PlayerDTO");
            playerData = lookup.findStatic(data, "getPlayerData", MethodType.methodType(data));
            nullable = lookup.findVirtual(data, "getNullable", MethodType.methodType(dto, UUID.class));
            String[] names = {"viewChunkFull", "viewChunkEntity", "viewChunkBlock", "viewChunkInaccessible"};
            MethodHandle[] found = new MethodHandle[names.length];
            for (int i = 0; i < names.length; i++) {
                found[i] = lookup.findVirtual(dto, names[i], MethodType.methodType(boolean.class));
            }
            views = found;
        } catch (Throwable t) {
            views = null; // unknown JourneyMap version: fall back to "any operator in the dimension"
        }
    }
}
