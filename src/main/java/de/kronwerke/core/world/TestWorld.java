package de.kronwerke.core.world;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.kronwerke.core.KronwerkeCore;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Consumer;

/**
 * The screenshot world. The pack generates the scenes as functions, one per row of boxes,
 * and lists the rows with their area in data/kronwerke/shots/rows.json. Building runs one
 * row per server tick, with the row's chunks loaded first, so even the live server only
 * stutters briefly instead of freezing for the whole world.
 */
public final class TestWorld {
    public static final ResourceKey<Level> KEY = ResourceKey.create(Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath("kronwerke", "testworld"));
    private static final ResourceLocation ROWS = ResourceLocation.fromNamespaceAndPath("kronwerke", "shots/rows.json");

    private record Row(ResourceLocation function, int x0, int z0, int x1, int z1) {
    }

    private static final Deque<Row> queue = new ArrayDeque<>();
    private static Consumer<String> report;
    private static int total;

    private TestWorld() {
    }

    public static ServerLevel level(MinecraftServer server) {
        return server.getLevel(KEY);
    }

    /** True once the landing pad exists, which the first row puts down. */
    public static boolean built(ServerLevel level) {
        BlockPos pad = new BlockPos(-4, 63, -4);
        level.getChunk(pad);
        return level.getBlockState(pad).is(Blocks.SMOOTH_STONE);
    }

    public static boolean busy() {
        return !queue.isEmpty();
    }

    /** Queues every row; done reports progress to whoever asked. Returns the row count, or -1 without rows.json. */
    public static int build(MinecraftServer server, boolean clearMobs, Consumer<String> done) {
        var res = server.getResourceManager().getResource(ROWS);
        if (res.isEmpty()) return -1;
        queue.clear();
        try (var reader = new InputStreamReader(res.get().open(), StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            for (var e : root.getAsJsonArray("rows")) {
                JsonObject r = e.getAsJsonObject();
                queue.add(new Row(ResourceLocation.parse(r.get("function").getAsString()),
                        r.get("x0").getAsInt(), r.get("z0").getAsInt(), r.get("x1").getAsInt(), r.get("z1").getAsInt()));
            }
        } catch (Exception ex) {
            KronwerkeCore.LOGGER.warn("Could not read {}: {}", ROWS, ex.toString());
            return -1;
        }
        ServerLevel level = level(server);
        if (clearMobs && level != null) {
            for (Row r : queue) load(level, r);
            for (Entity e : level.getAllEntities()) {
                if (e.getTags().contains("kw_shot")) e.discard();
            }
        }
        total = queue.size();
        report = done;
        return total;
    }

    private static void load(ServerLevel level, Row r) {
        for (int cx = r.x0 >> 4; cx <= r.x1 >> 4; cx++) {
            for (int cz = r.z0 >> 4; cz <= r.z1 >> 4; cz++) level.getChunk(cx, cz);
        }
    }

    /** One row per tick. */
    public static void tick(MinecraftServer server) {
        if (queue.isEmpty()) return;
        ServerLevel level = level(server);
        if (level == null) {
            queue.clear();
            return;
        }
        Row r = queue.poll();
        load(level, r);
        var fn = server.getFunctions().get(r.function);
        if (fn.isPresent()) {
            var source = server.createCommandSourceStack().withSuppressedOutput().withLevel(level).withPermission(4);
            server.getFunctions().execute(fn.get(), source);
        } else {
            KronwerkeCore.LOGGER.warn("Test world row {} is missing", r.function);
        }
        int doneRows = total - queue.size();
        if (report != null && (queue.isEmpty() || doneRows % 6 == 0)) {
            report.accept(queue.isEmpty() ? "Testwelt fertig: " + total + " Reihen gebaut." : "Testwelt: " + doneRows + " von " + total + " Reihen.");
        }
        if (queue.isEmpty()) report = null;
    }
}
