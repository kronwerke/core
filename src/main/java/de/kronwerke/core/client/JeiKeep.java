package de.kronwerke.core.client;

import de.kronwerke.core.KronwerkeCore;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * JEI stays up while a player moves between the servers of the network. JEI stops at every
 * logout and builds everything again at the next login, which stops the whole game for
 * seconds (every plugin of 250 mods lists its recipes again); with the same pack on every
 * server the result is the same, so on a move it is kept (JeiKeepMixin).
 * <p>
 * What JEI holds then comes from the connection it was started on. The game's own things
 * (items, blocks) are the same objects on every connection, but enchantments and the other
 * data-driven registries are sent anew by every server: an enchanted book in JEI's list
 * carries the old connection's enchantment. Such a stack shows and looks up as before, but it
 * could not be sent to the server (the old object has no number in the new registry). This
 * class remembers the old objects by name, and StaleHolderIdMixin answers with the number of
 * the object that has that name now.
 */
public final class JeiKeep {
    private static long keptUntil;
    private static int recipes = -1;
    /** Objects of the data-driven registries of the connection JEI was started on, by registry and name. */
    private static Map<Object, ResourceLocation> stale;

    private JeiKeep() {
    }

    /** JEI is about to stop: true when this is a move and it should stay. */
    public static boolean keep() {
        if (!TravelOverlay.moving()) return false;
        Minecraft mc = Minecraft.getInstance();
        var con = mc.getConnection();
        if (con == null) return false;
        try {
            recipes = con.getRecipeManager().getRecipes().size();
            if (stale == null) {
                Map<Object, ResourceLocation> m = new IdentityHashMap<>();
                con.registryAccess().registries().forEach(e -> {
                    if (BuiltInRegistries.REGISTRY.containsKey(e.key().location())) return; // the same objects everywhere
                    Registry<?> r = e.value();
                    for (var en : r.entrySet()) m.put(en.getValue(), en.getKey().location());
                });
                stale = m;
            }
        } catch (RuntimeException e) {
            KronwerkeCore.LOGGER.warn("JEI is not kept across this move: {}", e.toString());
            return false;
        }
        keptUntil = System.currentTimeMillis() + 90_000;
        KronwerkeCore.LOGGER.info("JEI stays up across the move ({} recipes)", recipes);
        return true;
    }

    /** The new server's recipes arrived and JEI wants to start again: true when it was kept and they are the same. */
    public static boolean same() {
        if (System.currentTimeMillis() > keptUntil) return false;
        keptUntil = 0;
        var con = Minecraft.getInstance().getConnection();
        int now = con == null ? -2 : con.getRecipeManager().getRecipes().size();
        // the same pack on every server, but not always the very same number: after a restart the
        // farm world had 45077 recipes and main 45078 (11.11.2026). A handful in 45000 is not
        // worth stopping the game for 20 seconds at every move; another pack version is.
        if (Math.abs(now - recipes) > Math.max(3, recipes / 2000)) {
            KronwerkeCore.LOGGER.info("JEI starts again: this server has {} recipes, the last one had {}", now, recipes);
            return false;
        }
        if (now != recipes) KronwerkeCore.LOGGER.info("JEI stays although this server has {} recipes and the last one had {}", now, recipes);
        return true;
    }

    /** JEI starts anew: what it holds from now on is this connection's. */
    public static void started() {
        stale = null;
        keptUntil = 0;
    }

    /** The name of an object of an earlier connection's registry, or null. */
    public static ResourceLocation staleName(Object value) {
        Map<Object, ResourceLocation> m = stale;
        return m == null ? null : m.get(value);
    }
}
