package de.kronwerke.core.perf;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Collection;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.levelgen.structure.Structure;

/**
 * Lootr gives every loot chest, barrel and pot in every structure a block entity ticker on
 * the server. That tick only counts down decay and refresh, which this pack does not use, so
 * thousands of chests ticked for nothing (1.1 % of the tick with 50 players). While neither
 * decay nor refresh is configured, these blocks get no server ticker at all. Shulker boxes keep
 * theirs (their lid pushes entities), and clients keep the lid animation.
 *
 * Lootr's own isAnythingDecaying and isAnythingRefreshing answer yes as soon as its structure
 * tags exist, and it ships them empty, so they always said yes. The same settings are checked
 * here, with the tags counted as set only when they list a structure.
 */
public final class LootrTicks {

    private static final String[] EMPTY_WHEN_UNUSED = {
            "getDecayDimensions", "getDecayWhitelist", "getModidDecayWhitelist",
            "getRefreshDimensions", "getRefreshWhitelist", "getRefreshModids"
    };

    private static Boolean needed;
    private static boolean warned;

    private LootrTicks() {
    }

    public static boolean serverTickerNeeded() {
        if (needed == null) {
            try {
                Class<?> api = Class.forName("noobanidus.mods.lootr.common.api.LootrAPI");
                MethodHandles.Lookup lookup = MethodHandles.publicLookup();
                MinecraftServer server = (MinecraftServer) lookup.findStatic(api, "getServer", MethodType.methodType(MinecraftServer.class)).invoke();
                if (server == null) return true; // tags not known yet, ask again once the server runs
                boolean any = (boolean) lookup.findStatic(api, "shouldDecayAll", MethodType.methodType(boolean.class)).invoke()
                        || (boolean) lookup.findStatic(api, "shouldRefreshAll", MethodType.methodType(boolean.class)).invoke();
                for (String getter : EMPTY_WHEN_UNUSED) {
                    if (any) break;
                    MethodHandle handle = lookup.findStatic(api, getter, MethodType.methodType(java.util.Set.class));
                    any = !((Collection<?>) handle.invoke()).isEmpty();
                }
                if (!any) any = listsStructures(server, "decay") || listsStructures(server, "refresh");
                needed = any;
                de.kronwerke.core.KronwerkeCore.LOGGER.info("Lootr containers {}", any ? "keep their server ticker (decay or refresh is set)" : "get no server ticker (nothing decays or refreshes)");
            } catch (Throwable t) {
                if (!warned) {
                    warned = true;
                    de.kronwerke.core.KronwerkeCore.LOGGER.warn("Lootr ticker check failed, keeping tickers for now: {}", t.toString());
                }
                return true; // config not ready yet: keep the ticker, ask again next time
            }
        }
        return needed;
    }

    private static boolean listsStructures(MinecraftServer server, String tag) {
        TagKey<Structure> key = TagKey.create(Registries.STRUCTURE, ResourceLocation.fromNamespaceAndPath("lootr", tag));
        return server.registryAccess().registryOrThrow(Registries.STRUCTURE).getTag(key).map(set -> set.size() > 0).orElse(false);
    }
}
