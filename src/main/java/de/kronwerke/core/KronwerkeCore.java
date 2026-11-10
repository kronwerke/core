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
        // the join queue holds new connections in their configuration phase while the network is full
        modBus.addListener(de.kronwerke.core.queue.JoinQueue::onTasks);
        // settings changed on the server win over the pack's config file, also after a pack update
        modBus.addListener((net.neoforged.fml.event.config.ModConfigEvent.Loading e) -> {
            if (e.getConfig().getSpec() == KronwerkeConfig.SPEC) de.kronwerke.core.config.Overrides.apply();
        });
        modBus.addListener((net.neoforged.fml.event.config.ModConfigEvent.Reloading e) -> {
            if (e.getConfig().getSpec() == KronwerkeConfig.SPEC) de.kronwerke.core.config.Overrides.apply();
        });
        NeoForge.EVENT_BUS.addListener(de.kronwerke.core.queue.JoinQueue::onTick);
        // a stage opened (or main told a side world): the recipes load again with the stage's recipes
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.tick.ServerTickEvent.Post e) -> {
            if (e.getServer().getTickCount() % 100 == 0 && de.kronwerke.core.mobs.MobStages.stageKnown()) {
                de.kronwerke.core.lock.MachineLocks.tick(e.getServer(), de.kronwerke.core.mobs.MobStages.stage());
            }
            if (Role.main() && e.getServer().getTickCount() % 1200 == 600) GoalManager.get().minute();
            if (Role.main() && e.getServer().getTickCount() % 20 == 7) GoalManager.get().flushBatches();
        });

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
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, de.kronwerke.core.portal.Travel::onFrozenDamage);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, de.kronwerke.core.portal.Travel::onFrozenPickup);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, de.kronwerke.core.portal.Ignite::onRightClick);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, SpawnGuard::onBreak);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, SpawnGuard::onPlace);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, SpawnGuard::onMultiPlace);
        NeoForge.EVENT_BUS.addListener(SpawnGuard::onExplosion);
        NeoForge.EVENT_BUS.addListener(TabList::onTick);
        NeoForge.EVENT_BUS.addListener(de.kronwerke.core.perf.RadiationScan::onTags);
        NeoForge.EVENT_BUS.addListener(de.kronwerke.core.perf.StaleNukes::onJoin);
        NeoForge.EVENT_BUS.addListener(de.kronwerke.core.boss.BossScaling::onTick);
        NeoForge.EVENT_BUS.addListener(de.kronwerke.core.boss.BossScaling::onDamage);
        NeoForge.EVENT_BUS.addListener(de.kronwerke.core.boss.BossScaling::onBossHurt);
        NeoForge.EVENT_BUS.addListener(de.kronwerke.core.mobs.MobStages::onSpawn);
        NeoForge.EVENT_BUS.addListener(de.kronwerke.core.mobs.MobStages::onJoin);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.tick.PlayerTickEvent.Post e) -> {
            if (e.getEntity() instanceof net.minecraft.server.level.ServerPlayer sp && sp.tickCount % 60 == 0) de.kronwerke.core.boss.BossScaling.chaosHint(sp);
        });
        NeoForge.EVENT_BUS.addListener(SpawnGuard::onMobGriefing);
        NeoForge.EVENT_BUS.addListener(SpawnGuard::onSpawn);
        NeoForge.EVENT_BUS.addListener(SpawnGuard::onDamage);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, SpawnGuard::onAttack);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, SpawnGuard::onEntityInteract);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, SpawnGuard::onEntityInteractPlain);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, SpawnGuard::onRightClick);

        if (ModList.get().isLoaded("chapters")) {
            // after Chapters, which refuses the pickup of every locked item
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, LockedItems::onPickup);
            NeoForge.EVENT_BUS.addListener(LockedItems::onTick);
            NeoForge.EVENT_BUS.addListener(LockedItems::onAttack);
            de.kronwerke.core.lock.LockedAccessories.register();
            NeoForge.EVENT_BUS.addListener(de.kronwerke.core.lock.LockedDimensions::onTravel);
            NeoForge.EVENT_BUS.addListener(de.kronwerke.core.lock.LockedDimensions::onPortalLit);
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
        de.kronwerke.core.queue.JoinQueue.start(event.getServer());
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
        if (sp instanceof de.kronwerke.core.loadtest.LoadBot) {
            // a load test's bot leaves no trace in the season: no kit, no activity, no join message
            de.kronwerke.core.portal.Travel.onLogin(sp);
            return;
        }
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
            // Travel first: it must still see whether this login was refused
            if (de.kronwerke.core.portal.Travel.leavingTo(sp.getUUID()) == null) de.kronwerke.core.queue.JoinQueue.onLeave(sp.getUUID());
            de.kronwerke.core.portal.Travel.onLogout(sp);
            NetworkSync.onLeave(sp);
        }
    }
}
