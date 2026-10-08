package de.kronwerke.core;

import com.mojang.logging.LogUtils;
import de.kronwerke.core.command.BypassCommand;
import de.kronwerke.core.command.KwCommand;
import de.kronwerke.core.config.KronwerkeConfig;
import de.kronwerke.core.goal.GoalManager;
import de.kronwerke.core.link.NetworkSync;
import de.kronwerke.core.link.Role;
import de.kronwerke.core.lock.LockedItems;
import de.kronwerke.core.lock.ClientHooks;
import de.kronwerke.core.obelisk.Obelisk;
import de.kronwerke.core.privacy.LogPruner;
import de.kronwerke.core.slot.SlotManager;
import de.kronwerke.core.spawn.SpawnGuard;
import de.kronwerke.core.tab.TabList;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModList;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
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
        if (FMLEnvironment.dist == Dist.CLIENT) {
            de.kronwerke.core.client.ClientSetup.register(container, modBus);
        }
        quietRcon();
        de.kronwerke.core.obelisk.ObeliskBlocks.register(modBus);
        de.kronwerke.core.portal.PortalBlocks.register(modBus);
        de.kronwerke.core.obelisk.KwParticles.register(modBus);
        de.kronwerke.core.obelisk.KwSounds.register(modBus);
        modBus.addListener(de.kronwerke.core.net.KwNetwork::register);

        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(this::onServerStarted);
        NeoForge.EVENT_BUS.addListener(this::onServerStopping);
        NeoForge.EVENT_BUS.addListener(this::onPlayerLogin);
        NeoForge.EVENT_BUS.addListener(this::onPlayerLogout);
        if (Role.main()) {
            // the obelisk and the test world belong to main; side worlds only show what main says
            NeoForge.EVENT_BUS.addListener(Obelisk.get()::onRightClick);
            NeoForge.EVENT_BUS.addListener(Obelisk.get()::onPlace);
            NeoForge.EVENT_BUS.addListener(Obelisk.get()::onBreak);
            NeoForge.EVENT_BUS.addListener(Obelisk.get()::onServerTick);
            NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.tick.ServerTickEvent.Post e) -> de.kronwerke.core.world.TestWorld.tick(e.getServer()));
        }
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, NetworkSync::onChat);
        NeoForge.EVENT_BUS.addListener(NetworkSync::onTick);
        // last in the tick, after the mods' own networks (Flux) have moved their energy
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, de.kronwerke.core.share.Share::onTick);
        NeoForge.EVENT_BUS.addListener(de.kronwerke.core.portal.Travel::onTick);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, de.kronwerke.core.portal.Ignite::onRightClick);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, SpawnGuard::onBreak);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, SpawnGuard::onPlace);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, SpawnGuard::onMultiPlace);
        NeoForge.EVENT_BUS.addListener(SpawnGuard::onExplosion);
        NeoForge.EVENT_BUS.addListener(TabList::onTick);
        NeoForge.EVENT_BUS.addListener(de.kronwerke.core.boss.BossScaling::onTick);
        NeoForge.EVENT_BUS.addListener(de.kronwerke.core.boss.BossScaling::onDamage);
        NeoForge.EVENT_BUS.addListener(de.kronwerke.core.mobs.MobStages::onSpawn);
        NeoForge.EVENT_BUS.addListener(de.kronwerke.core.mobs.MobStages::onJoin);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.tick.PlayerTickEvent.Post e) -> {
            if (e.getEntity() instanceof net.minecraft.server.level.ServerPlayer sp && sp.tickCount % 60 == 0) de.kronwerke.core.boss.BossScaling.chaosHint(sp);
        });
        NeoForge.EVENT_BUS.addListener(SpawnGuard::onMobGriefing);
        NeoForge.EVENT_BUS.addListener(SpawnGuard::onSpawn);
        NeoForge.EVENT_BUS.addListener(SpawnGuard::onDamage);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, SpawnGuard::onRightClick);

        if (ModList.get().isLoaded("chapters")) {
            // after Chapters, which refuses the pickup of every locked item
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, LockedItems::onPickup);
            NeoForge.EVENT_BUS.addListener(LockedItems::onTick);
            NeoForge.EVENT_BUS.addListener(de.kronwerke.core.lock.LockedDimensions::onTravel);
            NeoForge.EVENT_BUS.addListener(PlayerInteractEvent.RightClickItem.class, LockedItems::onInteract);
            NeoForge.EVENT_BUS.addListener(PlayerInteractEvent.RightClickBlock.class, LockedItems::onInteract);
            NeoForge.EVENT_BUS.addListener(PlayerInteractEvent.LeftClickBlock.class, LockedItems::onInteract);
            NeoForge.EVENT_BUS.addListener(PlayerInteractEvent.EntityInteract.class, LockedItems::onInteract);
            if (FMLEnvironment.dist == Dist.CLIENT) {
                ClientHooks.register(modBus);
            }
        }
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
        if (Role.main()) KwCommand.register(event.getDispatcher());
        else KwCommand.registerSide(event.getDispatcher());
        BypassCommand.register(event.getDispatcher());
        de.kronwerke.core.share.ShareCommand.register(event.getDispatcher());
        de.kronwerke.core.portal.MoveCommand.register(event.getDispatcher());
    }

    private void onServerStarted(ServerStartedEvent event) {
        TabList.init(event.getServer());
        LogPruner.prune(FMLPaths.GAMEDIR.get().resolve("logs"), KronwerkeConfig.LOG_DAYS.get());
        de.kronwerke.core.portal.Travel.init(event.getServer());
        de.kronwerke.core.share.Share.start(event.getServer());
        NetworkSync.start(event.getServer());
        if (!Role.main()) {
            LOGGER.info("Kronwerke Core ready as {} (role {}): season, goals, slots and the obelisk stay on main.", Role.server(), Role.role());
            return;
        }
        SlotManager.get().init(event.getServer());
        de.kronwerke.core.season.Season.init(event.getServer());
        GoalManager.get().init(event.getServer());
        Obelisk.get().init(event.getServer());
        LOGGER.info("Kronwerke Core ready. {} goals loaded, {} streamers with slots.",
                GoalManager.get().goalCount(), SlotManager.get().streamerCount());
    }

    private void onServerStopping(ServerStoppingEvent event) {
        de.kronwerke.core.portal.Travel.onStopping();
        de.kronwerke.core.share.Share.stop();
        NetworkSync.stop();
        if (Role.main()) GoalManager.get().shutdown();
    }

    private void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer sp)) return;
        if (NetworkSync.refuse(sp)) return;
        if (Role.main()) {
            de.kronwerke.core.season.Season.get().catchUp(sp);
            GoalManager.get().onPlayerJoin(sp);
            Obelisk.get().onJoin(sp);
        }
        de.kronwerke.core.portal.Travel.onLogin(sp);
        TabList.onJoin(sp);
        NetworkSync.onJoin(sp);
    }

    private void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (Role.main()) GoalManager.get().onPlayerLeave(event.getEntity());
        de.kronwerke.core.boss.BossScaling.forget(event.getEntity().getUUID());
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer sp) {
            NetworkSync.onLeave(sp);
            de.kronwerke.core.portal.Travel.onLogout(sp);
        }
    }
}
