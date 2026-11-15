package de.kronwerke.core.client;

import de.kronwerke.core.KronwerkeCore;
import dev.emi.emi.runtime.EmiReloadManager;
import net.minecraft.client.Minecraft;

/**
 * EMI stays up while a player moves between the servers of the network, like JEI (JeiKeep).
 * EMI forgets everything at a logout and builds its lists again when the next server's tags
 * and recipes arrive: 38 seconds of work in the background with this pack, during which it
 * shows nothing. With the same pack on every server the result is the same, so on a move the
 * forgetting is skipped, and the rebuild too when the new server has the same recipes.
 * <p>
 * The stacks EMI holds then carry the data-driven objects of the old connection
 * (enchantments), as in JEI; JeiKeep's list of them and StaleHolderIdMixin cover both.
 */
public final class EmiKeep {
    private static long keptUntil;
    private static int recipes = -1;

    private EmiKeep() {
    }

    /** EMI is about to clear its lists: true when this is a move and they should stay. */
    public static boolean keep() {
        if (!TravelOverlay.moving() || !EmiReloadManager.isLoaded()) return false;
        var con = Minecraft.getInstance().getConnection();
        try {
            recipes = con != null ? con.getRecipeManager().getRecipes().size() : JeiKeep.lastRecipes();
        } catch (RuntimeException e) {
            KronwerkeCore.LOGGER.warn("EMI is not kept across this move: {}", e.toString());
            return false;
        }
        if (recipes < 0) return false;
        keptUntil = System.currentTimeMillis() + 90_000;
        KronwerkeCore.LOGGER.info("EMI stays up across the move ({} recipes)", recipes);
        return true;
    }

    /** The new server's tags and recipes are here and EMI wants to reload: true when it was kept and they are the same. */
    public static boolean same() {
        if (System.currentTimeMillis() > keptUntil) return false;
        keptUntil = 0;
        var con = Minecraft.getInstance().getConnection();
        int now = con == null ? -2 : con.getRecipeManager().getRecipes().size();
        if (!JeiKeep.close(now, recipes)) {
            KronwerkeCore.LOGGER.info("EMI reloads: this server has {} recipes, the last one had {}", now, recipes);
            return false;
        }
        KronwerkeCore.LOGGER.info("EMI keeps its lists ({} recipes here, {} on the last server)", now, recipes);
        return true;
    }
}
