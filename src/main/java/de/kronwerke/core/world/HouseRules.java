package de.kronwerke.core.world;

import de.kronwerke.core.KronwerkeCore;
import de.kronwerke.core.config.KronwerkeConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.GameRules;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * Small rules of the pack that are no game rule of their own or have to hold in every world,
 * also in one that was just reset: the night is skipped when a part of the players sleeps,
 * no phantoms, and farmland is not trampled. Each has its switch in the config (rules).
 */
public final class HouseRules {
    private HouseRules() {
    }

    /** Game rules are kept in the world; a new world starts with the game's values, so set them at every start. */
    public static void onServerStarted(MinecraftServer server) {
        GameRules rules = server.getGameRules();
        int sleep = KronwerkeConfig.SLEEP_PERCENT.get();
        if (sleep > 0 && rules.getInt(GameRules.RULE_PLAYERS_SLEEPING_PERCENTAGE) != sleep) {
            rules.getRule(GameRules.RULE_PLAYERS_SLEEPING_PERCENTAGE).set(sleep, server);
            KronwerkeCore.LOGGER.info("Game rule playersSleepingPercentage set to {}", sleep);
        }
        if (!KronwerkeConfig.PHANTOMS.get() && rules.getBoolean(GameRules.RULE_DOINSOMNIA)) {
            rules.getRule(GameRules.RULE_DOINSOMNIA).set(false, server);
            KronwerkeCore.LOGGER.info("Game rule doInsomnia set to false");
        }
    }

    public static void onTrample(BlockEvent.FarmlandTrampleEvent event) {
        if (!KronwerkeConfig.TRAMPLE.get()) event.setCanceled(true);
    }
}
