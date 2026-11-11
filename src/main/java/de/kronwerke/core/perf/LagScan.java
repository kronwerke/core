package de.kronwerke.core.perf;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

import de.kronwerke.core.KronwerkeCore;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * /kw admin lag: for a few seconds the server times every entity tick, every machine tick
 * and every chunk tick (spawning, random ticks) and adds it up per chunk. Then it names the
 * chunks that cost the most, what in them, which team claimed them and whether they are
 * force loaded. Outside a scan nothing is timed.
 */
public final class LagScan {

    /** True while a scan runs; the mixins only time then. */
    public static volatile boolean on;

    private static final Map<ResourceKey<Level>, Map<Long, Stat>> STATS = new HashMap<>();
    private static int ticks, ticksLeft;
    private static long tickNanos, tickStart;
    private static Consumer<List<Component>> report;
    private static boolean listening;

    private LagScan() {}

    static final class Stat {
        long entities, machines, chunk;
        final Map<String, long[]> what = new HashMap<>(); // id -> {nanos, count}

        long total() {
            return entities + machines + chunk;
        }

        void add(String id, long nanos) {
            long[] w = what.computeIfAbsent(id, k -> new long[2]);
            w[0] += nanos;
            w[1]++;
        }
    }

    /** Starts a scan of the given length; the report goes to the consumer when it is done. */
    public static boolean start(MinecraftServer server, int seconds, Consumer<List<Component>> to) {
        if (on) return false;
        STATS.clear();
        ticks = 0;
        tickNanos = 0;
        tickStart = 0; // the tick that is running now is not timed
        ticksLeft = seconds * 20;
        report = to;
        listen();
        on = true;
        return true;
    }

    private static void listen() {
        if (listening) return;
        listening = true;
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Pre e) -> {
            if (on) tickStart = System.nanoTime();
        });
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> {
            if (!on || tickStart == 0) return;
            tickNanos += System.nanoTime() - tickStart;
            ticks++;
            if (--ticksLeft <= 0) finish(e.getServer());
        });
    }

    public static void entity(Entity e, long nanos) {
        Stat s = stat(e.level(), e.chunkPosition().toLong());
        s.entities += nanos;
        s.add(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString(), nanos);
    }

    public static void machine(BlockEntity be, long nanos) {
        if (be.getLevel() == null) return;
        Stat s = stat(be.getLevel(), ChunkPos.asLong(be.getBlockPos()));
        s.machines += nanos;
        s.add(BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType()) + "", nanos);
    }

    public static void chunk(Level level, ChunkPos pos, long nanos) {
        stat(level, pos.toLong()).chunk += nanos;
    }

    private static Stat stat(Level level, long chunk) {
        return STATS.computeIfAbsent(level.dimension(), k -> new HashMap<>()).computeIfAbsent(chunk, k -> new Stat());
    }

    private static void finish(MinecraftServer server) {
        on = false;
        int n = Math.max(1, ticks);
        record Hot(ServerLevel level, long chunk, Stat stat) {}
        List<Hot> all = new ArrayList<>();
        long measured = 0;
        for (var dim : STATS.entrySet()) {
            ServerLevel level = server.getLevel(dim.getKey());
            if (level == null) continue;
            for (var c : dim.getValue().entrySet()) {
                all.add(new Hot(level, c.getKey(), c.getValue()));
                measured += c.getValue().total();
            }
        }
        all.sort(Comparator.comparingLong((Hot h) -> h.stat.total()).reversed());
        List<Component> out = new ArrayList<>();
        out.add(Component.literal(String.format(Locale.GERMANY, "Lag-Scan über %d Ticks: im Schnitt %.1f ms pro Tick, davon %.1f ms in %d Chunks gemessen.",
                ticks, tickNanos / 1e6 / n, measured / 1e6 / n, all.size())).withStyle(ChatFormatting.GOLD));
        StringBuilder log = new StringBuilder(out.get(0).getString());
        for (int i = 0; i < Math.min(10, all.size()); i++) {
            Hot h = all.get(i);
            ChunkPos cp = new ChunkPos(h.chunk);
            BlockPos at = new BlockPos(cp.getMiddleBlockX(), h.level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, cp.getMiddleBlockX(), cp.getMiddleBlockZ()) + 1, cp.getMiddleBlockZ());
            String dim = h.level.dimension().location().toString();
            String owner = owner(h.level, cp);
            String line = String.format(Locale.GERMANY, "%d. %s %d, %d: %.2f ms/Tick (Entities %.2f, Maschinen %.2f, Chunk %.2f)%s",
                    i + 1, shortDim(dim), at.getX(), at.getZ(), h.stat.total() / 1e6 / n, h.stat.entities / 1e6 / n,
                    h.stat.machines / 1e6 / n, h.stat.chunk / 1e6 / n, owner == null ? ", nicht geclaimt" : ", " + owner);
            String top = top(h.stat, n);
            MutableComponent c = Component.literal(line).withStyle(i < 3 ? ChatFormatting.RED : ChatFormatting.YELLOW);
            c.withStyle(st -> st.withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND,
                            "/execute in " + dim + " run tp @s " + at.getX() + " " + at.getY() + " " + at.getZ()))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Klick: Teleport dorthin\n" + top))));
            out.add(c);
            out.add(Component.literal("   " + top).withStyle(ChatFormatting.GRAY));
            log.append('\n').append(line).append("\n   ").append(top);
        }
        if (all.isEmpty()) out.add(Component.literal("Nichts gemessen.").withStyle(ChatFormatting.GRAY));
        KronwerkeCore.LOGGER.info("{}", log);
        STATS.clear();
        Consumer<List<Component>> to = report;
        report = null;
        if (to != null) to.accept(out);
    }

    private static String top(Stat s, int ticks) {
        List<Map.Entry<String, long[]>> w = new ArrayList<>(s.what.entrySet());
        w.sort(Comparator.comparingLong((Map.Entry<String, long[]> e) -> e.getValue()[0]).reversed());
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < Math.min(4, w.size()); i++) {
            var e = w.get(i);
            long count = Math.max(1, e.getValue()[1] / ticks);
            parts.add(String.format(Locale.GERMANY, "%s ×%d %.2f ms", e.getKey().replace("minecraft:", ""), count, e.getValue()[0] / 1e6 / ticks));
        }
        return parts.isEmpty() ? "nur Chunk-Ticks (Spawnen, Wachstum)" : String.join(", ", parts);
    }

    private static String shortDim(String dim) {
        return switch (dim) {
            case "minecraft:overworld" -> "Overworld";
            case "minecraft:the_nether" -> "Nether";
            case "minecraft:the_end" -> "End";
            default -> dim;
        };
    }

    // ---- FTB Chunks, through reflection so Core runs without it ----

    private static Method api, manager, getChunk, getTeam, teamName, forced;
    private static java.lang.reflect.Constructor<?> dimPos;
    private static boolean looked, missing;

    /** "Team Name, Chunkloader an" for a claimed chunk, or null. */
    private static String owner(ServerLevel level, ChunkPos pos) {
        try {
            if (!looked) {
                looked = true;
                Class<?> apiClass = Class.forName("dev.ftb.mods.ftbchunks.api.FTBChunksAPI");
                api = apiClass.getMethod("api");
                manager = Class.forName("dev.ftb.mods.ftbchunks.api.FTBChunksAPI$API").getMethod("getManager");
                Class<?> dp = Class.forName("dev.ftb.mods.ftblibrary.math.ChunkDimPos");
                dimPos = dp.getConstructor(ResourceKey.class, int.class, int.class);
                getChunk = Class.forName("dev.ftb.mods.ftbchunks.api.ClaimedChunkManager").getMethod("getChunk", dp);
                Class<?> claimed = Class.forName("dev.ftb.mods.ftbchunks.api.ClaimedChunk");
                getTeam = claimed.getMethod("getTeamData");
                forced = claimed.getMethod("isActuallyForceLoaded");
                teamName = Class.forName("dev.ftb.mods.ftbchunks.api.ChunkTeamData").getMethod("getTeam");
            }
            if (missing) return null;
            Object m = manager.invoke(api.invoke(null));
            Object c = getChunk.invoke(m, dimPos.newInstance(level.dimension(), pos.x, pos.z));
            if (c == null) return null;
            Object team = teamName.invoke(getTeam.invoke(c));
            String name = team == null ? "?" : ((Component) team.getClass().getMethod("getName").invoke(team)).getString();
            return "Team " + name + ((Boolean) forced.invoke(c) ? ", Chunkloader an" : "");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            missing = true;
            return null;
        }
    }
}
