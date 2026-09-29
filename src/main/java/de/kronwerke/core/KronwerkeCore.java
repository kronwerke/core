package de.kronwerke.core;

import com.mojang.logging.LogUtils;
import de.kronwerke.core.command.KwCommand;
import de.kronwerke.core.config.KronwerkeConfig;
import de.kronwerke.core.goal.GoalManager;
import de.kronwerke.core.slot.SlotManager;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.slf4j.Logger;

@Mod(KronwerkeCore.MOD_ID)
public class KronwerkeCore {
    public static final String MOD_ID = "kronwerke";
    public static final Logger LOGGER = LogUtils.getLogger();

    public KronwerkeCore(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, KronwerkeConfig.SPEC);

        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(this::onServerStarted);
        NeoForge.EVENT_BUS.addListener(this::onServerStopping);
        NeoForge.EVENT_BUS.addListener(this::onPlayerLogin);
        NeoForge.EVENT_BUS.addListener(this::onPlayerLogout);
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        KwCommand.register(event.getDispatcher());
    }

    private void onServerStarted(ServerStartedEvent event) {
        SlotManager.get().init(event.getServer());
        GoalManager.get().init(event.getServer());
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
