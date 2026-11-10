package de.kronwerke.core.client;

import java.util.ArrayList;
import java.util.List;

import de.kronwerke.core.mixin.CreativeTabsAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.CreativeModeTabSearchRegistry;

/**
 * The creative menu froze the game the first time it opened: Minecraft fills every tab of 250
 * mods at once on the main thread (over three seconds on a fast server CPU, so "not responding"
 * on many PCs; Silent Gear, AE2 facades and Theurgy alone take a second). After joining, the
 * tabs are now filled in the background of normal play, a few milliseconds per tick, the same
 * way the creative screen would fill them. When the menu opens, everything is already there.
 * Search indexes are started exactly as the creative screen starts them (built off-thread).
 */
public final class CreativePrewarm {

    private static final long BUDGET_NS = 6_000_000L;
    private static final int START_AFTER_TICKS = 20;

    private static int ticksInWorld;
    private static List<CreativeModeTab> todo;
    private static CreativeModeTab.ItemDisplayParameters params;
    private static boolean done;
    private static long spentNs;
    private static int spentTicks;

    private CreativePrewarm() {
    }

    public static void reset() {
        ticksInWorld = 0;
        spentNs = 0;
        spentTicks = 0;
        todo = null;
        params = null;
        done = false;
    }

    public static void tick() {
        try {
            step();
        } catch (Throwable t) {
            // never take the game down for a warm-up: the creative screen does the work itself then
            done = true;
            de.kronwerke.core.KronwerkeCore.LOGGER.warn("Creative tab warm-up stopped", t);
        }
    }

    private static void step() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientPacketListener conn = mc.getConnection();
        if (player == null || conn == null) {
            reset();
            return;
        }
        if (done || ++ticksInWorld < START_AFTER_TICKS) return;
        if (todo == null) {
            boolean ops = player.canUseGameMasterBlocks() && mc.options.operatorItemsTab().get();
            var registries = player.level().registryAccess();
            CreativeModeTab.ItemDisplayParameters cached = CreativeTabsAccessor.kronwerke$cached();
            if (cached != null && !cached.needsUpdate(conn.enabledFeatures(), ops, registries)) {
                done = true; // the player opened the menu already
                return;
            }
            params = new CreativeModeTab.ItemDisplayParameters(conn.enabledFeatures(), ops, registries);
            todo = new ArrayList<>();
            // the same order as vanilla: categories first, then search and the rest that collect from them
            CreativeTabsAccessor.kronwerke$allTabs().filter(t -> t.getType() == CreativeModeTab.Type.CATEGORY).forEach(todo::add);
            CreativeTabsAccessor.kronwerke$allTabs().filter(t -> t.getType() != CreativeModeTab.Type.CATEGORY).forEach(todo::add);
        }
        long start = System.nanoTime();
        spentTicks++;
        while (!todo.isEmpty() && System.nanoTime() - start < BUDGET_NS) {
            CreativeModeTab tab = todo.remove(0);
            try {
                tab.buildContents(params);
            } catch (RuntimeException e) {
                // a broken tab: leave the whole job to the creative screen
                done = true;
                return;
            }
        }
        spentNs += System.nanoTime() - start;
        if (!todo.isEmpty()) return;
        CreativeModeTab.ItemDisplayParameters cached = CreativeTabsAccessor.kronwerke$cached();
        if (cached == null || cached.needsUpdate(params.enabledFeatures(), params.hasPermissions(), params.holders())) {
            CreativeTabsAccessor.kronwerke$setCached(params);
            CreativeTabsAccessor.kronwerke$allTabs().filter(CreativeModeTab::hasSearchBar).forEach(tab -> {
                List<ItemStack> list = List.copyOf(tab.getDisplayItems());
                conn.searchTrees().updateCreativeTooltips(params.holders(), list, CreativeModeTabSearchRegistry.getNameSearchKey(tab));
                conn.searchTrees().updateCreativeTags(list, CreativeModeTabSearchRegistry.getTagSearchKey(tab));
            });
        }
        done = true;
        de.kronwerke.core.KronwerkeCore.LOGGER.info("Creative tabs filled in {} ms over {} ticks", spentNs / 1_000_000, spentTicks);
    }
}
