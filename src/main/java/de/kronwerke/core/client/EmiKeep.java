package de.kronwerke.core.client;

import de.kronwerke.core.KronwerkeCore;
import dev.emi.emi.runtime.EmiReloadManager;
import net.minecraft.client.Minecraft;

/**
 * EMI stays up while a player moves between the servers of the network, like JEI (JeiKeep).
 * EMI builds its lists again whenever a server's tags and recipes arrive: 38 seconds of work
 * in the background with this pack, during which it shows nothing. With the same pack on every
 * server the result is the same, so after a move the rebuild is skipped when the new server
 * has the same recipes, within a handful.
 * <p>
 * On a move EMI does not forget its lists by itself (it does that when the game goes back to
 * the menu, which a move does not), so there is nothing to keep, only a reload to leave out.
 * Should a version of EMI clear on a move after all, that is skipped too.
 * <p>
 * The stacks EMI holds then carry the data-driven objects of the old connection
 * (enchantments), as in JEI; JeiKeep's list of them and StaleHolderIdMixin cover both.
 */
public final class EmiKeep {
    /** The recipe count of the connection EMI's lists were built on, or -1. */
    private static int loaded = -1;
    /** The move (JeiKeep.movedAt) whose reload was already left out. */
    private static long handled;

    private EmiKeep() {
    }

    private static boolean moved() {
        long at = JeiKeep.movedAt();
        return at != handled && System.currentTimeMillis() - at < 90_000;
    }

    /** EMI is about to clear its lists: true when this is a move and they should stay. */
    public static boolean keep() {
        if (!TravelOverlay.moving() || !EmiReloadManager.isLoaded() || loaded < 0) return false;
        KronwerkeCore.LOGGER.info("EMI stays up across the move");
        return true;
    }

    /** EMI wants to reload: true when this is the arrival of a move and the recipes are the same. */
    public static boolean same() {
        var con = Minecraft.getInstance().getConnection();
        int now = con == null ? -2 : con.getRecipeManager().getRecipes().size();
        if (moved() && EmiReloadManager.isLoaded()) {
            handled = JeiKeep.movedAt();
            if (JeiKeep.close(now, loaded)) {
                KronwerkeCore.LOGGER.info("EMI keeps its lists across the move ({} recipes here, {} where they were built)", now, loaded);
                return true;
            }
            KronwerkeCore.LOGGER.info("EMI reloads: this server has {} recipes, its lists were built on {}", now, loaded);
        }
        loaded = now;
        return false;
    }
}
