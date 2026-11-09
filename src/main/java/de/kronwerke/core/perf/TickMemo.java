package de.kronwerke.core.perf;

import java.util.IdentityHashMap;
import java.util.Map;

import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/** Yes or no answers about an object pair, kept for one server tick, on the server thread only. */
public final class TickMemo {

    private static final Map<Object, Map<Object, Boolean>> MEMO = new IdentityHashMap<>();
    private static int tick = -1;

    private TickMemo() {
    }

    private static boolean usable() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || !server.isSameThread()) {
            return false;
        }
        if (tick != server.getTickCount()) {
            MEMO.clear();
            tick = server.getTickCount();
        }
        return true;
    }

    public static Boolean get(Object a, Object b) {
        if (!usable()) {
            return null;
        }
        Map<Object, Boolean> inner = MEMO.get(a);
        return inner == null ? null : inner.get(b);
    }

    public static void put(Object a, Object b, boolean value) {
        if (usable()) {
            MEMO.computeIfAbsent(a, k -> new IdentityHashMap<>()).put(b, value);
        }
    }
}
