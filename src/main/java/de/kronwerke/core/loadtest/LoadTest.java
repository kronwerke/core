package de.kronwerke.core.loadtest;

import com.mojang.authlib.GameProfile;
import de.kronwerke.core.KronwerkeCore;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.GameType;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Comparator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Load test bots: real ServerPlayer objects on a connection that goes nowhere, so the server
 * does everything it does for a player (chunk loading and generation, entity tracking, mob
 * spawning around them, every mod's player tick) except the network write. Named Last01 and
 * up; stopping removes them with their player files, so the world keeps no trace of them.
 */
public final class LoadTest {
    public static final String PREFIX = "Last";
    private static final List<LoadBot> BOTS = new ArrayList<>();

    private LoadTest() {
    }

    public static int count() {
        BOTS.removeIf(LoadBot::isRemoved);
        return BOTS.size();
    }

    /** Ticks all bots spent waiting for chunks, since they started. */
    public static long waited() {
        return BOTS.stream().mapToLong(b -> b.waited).sum();
    }

    public static long travellers() {
        return BOTS.stream().filter(b -> b.traveller).count();
    }

    /**
     * Adds bots around the world spawn. Settlers stand on the ground at random spots within
     * the radius, travellers fly at the given speed (blocks per tick) at flyY.
     */
    public static int start(MinecraftServer server, int settlers, int travellers, int radius, double speed, int flyY) {
        ServerLevel level = home(server);
        BlockPos spawn = center(level);
        ChunkPos at = new ChunkPos(spawn);
        // the bots start above the spawn: keep the chunks around it loaded, and wait for them
        level.getChunkSource().addRegionTicket(TICKET, at, 4, at);
        if (!spawnReady(level, at)) {
            PENDING.add(() -> start(server, settlers, travellers, radius, speed, flyY));
            listen();
            return 0;
        }
        RandomSource random = RandomSource.create();
        int added = 0;
        for (int i = 0; i < settlers + travellers; i++) {
            String name = nextName(server);
            GameProfile profile = new GameProfile(UUIDUtil.createOfflinePlayerUUID(name), name);
            boolean traveller = i >= settlers;
            double angle = random.nextDouble() * Math.PI * 2, dist = Math.sqrt(random.nextDouble()) * radius;
            double tx = spawn.getX() + 0.5 + Math.cos(angle) * dist, tz = spawn.getZ() + 0.5 + Math.sin(angle) * dist;
            forget(server, profile.getId()); // a bot never brings a position from an earlier run
            LoadBot bot = new LoadBot(server, level, profile, traveller, spawn.getX(), spawn.getZ(), radius, speed, flyY, tx, tz);
            bot.moveTo(spawn.getX() + 0.5, flyY, spawn.getZ() + 0.5, 0, 0);
            // a player without saved data joins in the overworld whatever level it was made for;
            // on a Nether or End server that put the bots into an overworld nobody generated
            if (level.dimension() != net.minecraft.world.level.Level.OVERWORLD) arriveIn(server, bot);
            server.getPlayerList().placeNewPlayer(new BotConnection(), bot, CommonListenerCookie.createInitial(profile, false));
            if (bot.serverLevel() != level) {
                KronwerkeCore.LOGGER.warn("Load test: {} joined in {} instead of {}, moving it", name, bot.serverLevel().dimension().location(), level.dimension().location());
                bot.teleportTo(level, spawn.getX() + 0.5, flyY, spawn.getZ() + 0.5, 0, 0);
            }
            bot.setGameMode(GameType.CREATIVE);
            bot.getAbilities().flying = true;
            bot.setInvulnerable(true);
            // every bot starts above the spawn, where the chunks are loaded, and flies out
            bot.absMoveTo(spawn.getX() + 0.5, flyY, spawn.getZ() + 0.5);
            BOTS.add(bot);
            added++;
        }
        KronwerkeCore.LOGGER.info("Load test: {} bots added ({} travelling), {} in total", added, travellers, count());
        return added;
    }

    private static final TicketType<ChunkPos> TICKET = TicketType.create("kronwerke_loadtest", Comparator.comparingLong(ChunkPos::toLong));
    private static final List<Runnable> PENDING = new ArrayList<>();
    private static boolean listening;

    /**
     * Where the bots play: the overworld, or on a Nether or End server its own dimension, so
     * a load test there loads what fifty players in the Nether would.
     */
    public static ServerLevel home(MinecraftServer server) {
        if (de.kronwerke.core.portal.Dimensions.dimensionServer()) {
            for (ServerLevel l : server.getAllLevels()) {
                if (l.dimension() != net.minecraft.world.level.Level.OVERWORLD && de.kronwerke.core.portal.Dimensions.here(l.dimension())) return l;
            }
        }
        return server.overworld();
    }

    private static BlockPos center(ServerLevel level) {
        if (level.dimension() == net.minecraft.world.level.Level.END) return ServerLevel.END_SPAWN_POINT;
        return level.dimension() == net.minecraft.world.level.Level.OVERWORLD ? level.getSharedSpawnPos() : BlockPos.ZERO;
    }

    /** The height travellers fly at: above the overworld, inside the Nether's caves, over the End's islands. */
    public static int flyHeight(MinecraftServer server) {
        ServerLevel l = home(server);
        if (l.dimensionType().hasCeiling()) return 70;
        return l.dimension() == net.minecraft.world.level.Level.OVERWORLD ? 320 : 120;
    }

    private static boolean spawnReady(ServerLevel level, ChunkPos at) {
        for (int i = -3; i <= 3; i++) {
            for (int j = -3; j <= 3; j++) {
                if (level.getChunkSource().getChunkNow(at.x + i, at.z + j) == null) return false;
            }
        }
        return true;
    }

    /** Starts the waiting bots once the chunks around the spawn are there. */
    private static void listen() {
        if (listening) return;
        listening = true;
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> {
            if (PENDING.isEmpty() || e.getServer().getTickCount() % 10 != 0) return;
            ServerLevel level = home(e.getServer());
            if (!spawnReady(level, new ChunkPos(center(level)))) return;
            List<Runnable> run = new ArrayList<>(PENDING);
            PENDING.clear();
            run.forEach(Runnable::run);
        });
    }

    /** Whether bots are waiting for the spawn chunks. */
    public static boolean waiting() {
        return !PENDING.isEmpty();
    }

    /** Removes every bot and deletes its player files. */
    public static int stop(MinecraftServer server) {
        PENDING.clear();
        ServerLevel level = home(server);
        ChunkPos at = new ChunkPos(center(level));
        level.getChunkSource().removeRegionTicket(TICKET, at, 4, at);
        List<LoadBot> bots = new ArrayList<>(BOTS);
        BOTS.clear();
        for (LoadBot bot : bots) {
            if (!bot.isRemoved()) server.getPlayerList().remove(bot);
            forget(server, bot.getUUID());
        }
        KronwerkeCore.LOGGER.info("Load test: {} bots removed", bots.size());
        return bots.size();
    }

    /**
     * Writes the bot's player file before it joins, so the join reads its dimension and
     * position from there like it does for a real player arriving from another server.
     */
    private static void arriveIn(MinecraftServer server, LoadBot bot) {
        Path file = server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(bot.getUUID() + ".dat");
        try {
            net.minecraft.nbt.CompoundTag tag = net.minecraft.nbt.NbtUtils.addCurrentDataVersion(bot.saveWithoutId(new net.minecraft.nbt.CompoundTag()));
            Files.createDirectories(file.getParent());
            net.minecraft.nbt.NbtIo.writeCompressed(tag, file);
        } catch (IOException | RuntimeException e) {
            KronwerkeCore.LOGGER.warn("Load test: could not write the player file of {}: {}", bot.getGameProfile().getName(), e.toString());
        }
    }

    private static void forget(MinecraftServer server, UUID id) {
        Path root = server.getWorldPath(LevelResource.ROOT);
        for (String file : List.of("playerdata/" + id + ".dat", "playerdata/" + id + ".dat_old", "stats/" + id + ".json", "advancements/" + id + ".json")) {
            try {
                Files.deleteIfExists(root.resolve(file));
            } catch (IOException e) {
                KronwerkeCore.LOGGER.warn("Load test: could not delete {}: {}", file, e.toString());
            }
        }
    }

    private static String nextName(MinecraftServer server) {
        for (int n = 1; ; n++) {
            String name = String.format("%s%02d", PREFIX, n);
            if (server.getPlayerList().getPlayerByName(name) == null) return name;
        }
    }
}
