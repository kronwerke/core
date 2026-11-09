package de.kronwerke.core.perf;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Lootr gives every loot chest, barrel and pot in every structure a block entity ticker on
 * the server. That tick only counts down decay and refresh, which this pack does not use, so
 * thousands of chests ticked for nothing (1.4 % of the tick with 50 players). While neither
 * decay nor refresh is configured, these blocks get no server ticker at all. Shulker boxes keep
 * theirs (their lid pushes entities), and clients keep the lid animation.
 */
public final class LootrTicks {

    private static Boolean needed;

    private LootrTicks() {
    }

    public static boolean serverTickerNeeded() {
        if (needed == null) {
            try {
                Class<?> api = Class.forName("noobanidus.mods.lootr.common.api.LootrAPI");
                MethodHandle decaying = MethodHandles.publicLookup().findStatic(api, "isAnythingDecaying", MethodType.methodType(boolean.class));
                MethodHandle refreshing = MethodHandles.publicLookup().findStatic(api, "isAnythingRefreshing", MethodType.methodType(boolean.class));
                needed = (boolean) decaying.invokeExact() || (boolean) refreshing.invokeExact();
            } catch (Throwable t) {
                return true; // config not ready yet: keep the ticker, ask again next time
            }
        }
        return needed;
    }
}
