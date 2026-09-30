package de.kronwerke.core;

import com.mojang.logging.LogUtils;
import de.kronwerke.core.command.BypassCommand;
import de.kronwerke.core.command.KwCommand;
import de.kronwerke.core.config.KronwerkeConfig;
import de.kronwerke.core.goal.GoalManager;
import de.kronwerke.core.obelisk.Obelisk;
import de.kronwerke.core.privacy.LogPruner;
import de.kronwerke.core.slot.SlotManager;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;
import org.slf4j.Logger;

import java.util.Map;

@Mod(KronwerkeCore.MOD_ID)
public class KronwerkeCore {
    public static final String MOD_ID = "kronwerke";
    public static final Logger LOGGER = LogUtils.getLogger();

    public KronwerkeCore(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, KronwerkeConfig.SPEC);
        quietRcon();

        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(this::onServerStarted);
        NeoForge.EVENT_BUS.addListener(this::onServerStopping);
        NeoForge.EVENT_BUS.addListener(this::onPlayerLogin);
        NeoForge.EVENT_BUS.addListener(this::onPlayerLogout);
        NeoForge.EVENT_BUS.addListener(Obelisk.get()::onRightClick);
        NeoForge.EVENT_BUS.addListener(Obelisk.get()::onPlace);
        NeoForge.EVENT_BUS.addListener(Obelisk.get()::onBreak);
        NeoForge.EVENT_BUS.addListener(Obelisk.get()::onServerTick);
    }

    /**
     * Every RCON connection logs two lines ("Thread RCON Client ... started" and "... shutting
     * down"), and the launcher and the bot connect often. Warnings and errors still show.
     */
    private static void quietRcon() {
        try {
            Configurator.setLevel(Map.of(
                    "net.minecraft.server.rcon.thread.RconClient", Level.WARN,
                    "net.minecraft.server.rcon.thread.GenericThread", Level.WARN));
        } catch (RuntimeException | LinkageError e) {
            LOGGER.warn("Could not quiet the RCON log lines: {}", e.toString());
        }
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        KwCommand.register(event.getDispatcher());
        BypassCommand.register(event.getDispatcher());
    }

    private void onServerStarted(ServerStartedEvent event) {
        SlotManager.get().init(event.getServer());
        GoalManager.get().init(event.getServer());
        Obelisk.get().init(event.getServer());
        LogPruner.prune(FMLPaths.GAMEDIR.get().resolve("logs"), KronwerkeConfig.LOG_DAYS.get());
        LOGGER.info("Kronwerke Core ready. {} goals loaded, {} streamers with slots.",
                GoalManager.get().goalCount(), SlotManager.get().streamerCount());
    }

    private void onServerStopping(ServerStoppingEvent event) {
        GoalManager.get().shutdown();
    }

    private void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        GoalManager.get().onPlayerJoin(event.getEntity());
    }

    private void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        GoalManager.get().onPlayerLeave(event.getEntity());
    }
}
