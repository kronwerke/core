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
            // a copy: discarding while walking the level's own list skips entities and hands out nulls
            java.util.List<Entity> all = new java.util.ArrayList<>();
            for (Entity e : level.getAllEntities()) if (e != null) all.add(e);
            for (Entity e : all) if (e.getTags().contains("kw_shot")) e.discard();
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

    // ---- scenes from the server folder -------------------------------------------------
    //
    // kronwerke/shots/world/<name>.mcfunction, plain commands. Trying a scene out this way
    // needs no pack update and no restart: write the file, /kw testworld apply <name>, look.
    // What a command answered when it failed is kept in <name>.log next to the file.

    private static final Deque<java.nio.file.Path> files = new ArrayDeque<>();
    private static Consumer<String> fileReport;
    private static int filesTotal, commands, failed;

    public static java.nio.file.Path worldDir() {
        return de.kronwerke.core.camera.Camera.shotsDir().resolve("world");
    }

    /** Queues one file, or every file of the folder for an empty name. Returns the count, -1 when there is none. */
    public static int apply(String name, Consumer<String> done) {
        java.nio.file.Path dir = worldDir();
        java.util.List<java.nio.file.Path> found = new java.util.ArrayList<>();
        try {
            if (name.isEmpty()) {
                if (java.nio.file.Files.isDirectory(dir)) {
                    try (var all = java.nio.file.Files.newDirectoryStream(dir, "*.mcfunction")) {
                        for (var f : all) found.add(f);
                    }
                }
            } else if (name.matches("[a-z0-9_.-]+") && java.nio.file.Files.isRegularFile(dir.resolve(name + ".mcfunction"))) {
                found.add(dir.resolve(name + ".mcfunction"));
            }
        } catch (java.io.IOException ex) {
            KronwerkeCore.LOGGER.warn("Could not list {}: {}", dir, ex.toString());
        }
        if (found.isEmpty()) return -1;
        found.sort(null);
        files.addAll(found);
        filesTotal = files.size();
        commands = failed = 0;
        fileReport = done;
        return found.size();
    }

    /** Collects what failed commands answer. */
    private static final class Answers implements net.minecraft.commands.CommandSource {
        final StringBuilder text = new StringBuilder();

        @Override
        public void sendSystemMessage(net.minecraft.network.chat.Component c) {
            if (text.length() > 0) text.append(" | ");
            text.append(c.getString());
        }

        @Override
        public boolean acceptsSuccess() {
            return false;
        }

        @Override
        public boolean acceptsFailure() {
            return true;
        }

        @Override
        public boolean shouldInformAdmins() {
            return false;
        }
    }

    private static void run(MinecraftServer server, ServerLevel level, java.nio.file.Path file) {
        java.util.List<String> lines;
        try {
            lines = java.nio.file.Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (java.io.IOException ex) {
            KronwerkeCore.LOGGER.warn("Could not read {}: {}", file, ex.toString());
            return;
        }
        hand = net.minecraft.world.item.ItemStack.EMPTY;
        Answers out = new Answers();
        var source = new net.minecraft.commands.CommandSourceStack(out, net.minecraft.world.phys.Vec3.ZERO,
                net.minecraft.world.phys.Vec2.ZERO, level, 4, "Testwelt", net.minecraft.network.chat.Component.literal("Testwelt"), server, null);
        java.util.List<String> log = new java.util.ArrayList<>();
        java.util.List<int[]> areas = new java.util.ArrayList<>();
        int n = 0;
        for (String raw : lines) {
            n++;
            String line = raw.strip();
            if (line.startsWith("#area ")) {
                // "#area x0 z0 x1 z1": load it, and take away what an earlier build summoned there
                String[] a = line.split("\\s+");
                try {
                    int x0 = Integer.parseInt(a[1]), z0 = Integer.parseInt(a[2]), x1 = Integer.parseInt(a[3]), z1 = Integer.parseInt(a[4]);
                    load(level, new Row(null, x0, z0, x1, z1));
                    areas.add(new int[] {x0, z0, x1, z1});
                    java.util.List<Entity> all = new java.util.ArrayList<>();
                    for (Entity e : level.getAllEntities()) if (e != null) all.add(e);
                    for (Entity e : all) {
                        boolean inside = e.getX() >= x0 && e.getX() <= x1 + 1 && e.getZ() >= z0 && e.getZ() <= z1 + 1;
                        // everything but players: a scene's own entities carry no tag (an assembled
                        // windmill, a spark a block spawned)
                        if (inside && !(e instanceof net.minecraft.world.entity.player.Player)) e.discard();
                    }
                } catch (RuntimeException ex) {
                    log.add(n + ": " + line + " -> " + ex);
                }
                continue;
            }
            if (line.isEmpty() || line.startsWith("#")) continue;
            out.text.setLength(0);
            commands++;
            try {
                server.getCommands().performPrefixedCommand(source, line);
            } catch (RuntimeException ex) {
                out.text.append(ex);
            }
            // a block that already was what it should become is no failure
            String answer = out.text.toString();
            if (!answer.isEmpty() && !answer.equals("No blocks were filled") && !answer.equals("Could not set the block")) {
                failed++;
                log.add(n + ": " + line + " -> " + out.text);
            }
        }
        int dried = 0;
        for (int[] a : areas) dried += dry(level, a[0], a[1], a[2], a[3]);
        if (dried > 0) log.add("note: " + dried + " blocks stood in water of their own and were dried");
        try {
            String base = file.getFileName().toString();
            java.nio.file.Files.write(file.resolveSibling(base.substring(0, base.length() - ".mcfunction".length()) + ".log"), log, StandardCharsets.UTF_8);
        } catch (java.io.IOException ex) {
            KronwerkeCore.LOGGER.warn("Could not write the log of {}: {}", file, ex.toString());
        }
    }

    /**
     * A block set by command without saying whether it holds water takes its block's default,
     * and some mods leave that at "it does" (Eidolon's altars and stone hands): every one of
     * them is then a spring, and three scenes stood under water. No scene wants a block in
     * water of its own, so each build ends with drying them. Returns how many there were.
     */
    private static int dry(ServerLevel level, int x0, int z0, int x1, int z1) {
        var water = net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED;
        var pos = new BlockPos.MutableBlockPos();
        int n = 0;
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                if (!level.hasChunk(x >> 4, z >> 4)) continue;
                for (int y = 56; y <= 160; y++) {
                    var state = level.getBlockState(pos.set(x, y, z));
                    if (state.hasProperty(water) && state.getValue(water)) {
                        level.setBlock(pos, state.setValue(water, false), 2 | 16);
                        n++;
                    }
                }
            }
        }
        return n;
    }

    // ---- structures and the whole world from outside the pack ------------------------------

    private static final java.util.Map<String, net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate> templates = new java.util.HashMap<>();

    /**
     * Places kronwerke/shots/world/structures/<name>.nbt, turned the way a structure block turns
     * it. What it summons is tagged like everything else of the test world.
     */
    public static String place(ServerLevel level, String name, BlockPos pos, net.minecraft.world.level.block.Rotation rotation) {
        var template = templates.get(name);
        if (template == null) {
            java.nio.file.Path root = worldDir().resolve("structures").normalize();
            java.nio.file.Path file = root.resolve(name + ".nbt").normalize();
            if (!file.startsWith(root) || !java.nio.file.Files.isRegularFile(file)) return null;
            try {
                var tag = net.minecraft.nbt.NbtIo.readCompressed(file, net.minecraft.nbt.NbtAccounter.unlimitedHeap());
                template = level.getServer().getStructureManager().readStructure(tag);
            } catch (java.io.IOException | RuntimeException ex) {
                return "Die Struktur " + name + " ist nicht lesbar: " + ex;
            }
            templates.put(name, template);
        }
        var settings = new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings()
                .setRotation(rotation).setIgnoreEntities(false);
        var box = template.getBoundingBox(settings, pos);
        template.placeInWorld(level, pos, pos, settings, level.getRandom(), 2);
        var area = new net.minecraft.world.phys.AABB(box.minX(), box.minY(), box.minZ(), box.maxX() + 1, box.maxY() + 1, box.maxZ() + 1);
        for (Entity e : level.getEntities((Entity) null, area, x -> !(x instanceof net.minecraft.world.entity.player.Player))) e.addTag("kw_shot");
        return "";
    }

    private static final java.util.regex.Pattern REF = java.util.regex.Pattern.compile("[0-9a-f]{7,40}|[a-z][a-z0-9_-]{0,40}");

    /**
     * Fetches the scenes: the branch or commit ref of the pack repository that holds them,
     * unpacked into kronwerke/shots/world. The pack itself stays free of the test world, and a
     * changed scene needs no pack update. False for a ref that is none.
     */
    public static boolean pull(MinecraftServer server, String ref, Consumer<String> done) {
        if (!REF.matcher(ref).matches()) return false;
        de.kronwerke.core.camera.ShotFiles.WORKER.execute(() -> {
            String msg;
            try {
                msg = fetch("https://codeload.github.com/kronwerke/pack/zip/" + ref);
            } catch (Exception ex) {
                msg = "Laden fehlgeschlagen: " + ex;
            }
            String say = msg;
            server.execute(() -> {
                templates.clear();
                done.accept(say);
            });
        });
        return true;
    }

    private static String fetch(String url) throws Exception {
        var client = java.net.http.HttpClient.newBuilder().followRedirects(java.net.http.HttpClient.Redirect.NORMAL)
                .connectTimeout(java.time.Duration.ofSeconds(20)).build();
        var response = client.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(url)).timeout(java.time.Duration.ofSeconds(180)).build(),
                java.net.http.HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            response.body().close();
            return "Laden fehlgeschlagen: HTTP " + response.statusCode();
        }
        java.nio.file.Path dir = worldDir(), fresh = dir.resolveSibling("world.new");
        deleteTree(fresh);
        java.nio.file.Files.createDirectories(fresh);
        int n = 0;
        try (var zip = new java.util.zip.ZipInputStream(response.body())) {
            for (java.util.zip.ZipEntry e; (e = zip.getNextEntry()) != null; ) {
                String name = e.getName();
                int cut = name.indexOf('/');       // the archive's own top folder
                if (cut < 0 || e.isDirectory()) continue;
                java.nio.file.Path out = fresh.resolve(name.substring(cut + 1)).normalize();
                if (!out.startsWith(fresh) || out.equals(fresh)) continue;
                java.nio.file.Files.createDirectories(out.getParent());
                java.nio.file.Files.copy(zip, out, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                n++;
            }
        }
        deleteTree(dir);
        java.nio.file.Files.move(fresh, dir);
        java.nio.file.Path shots = dir.resolve("shots.json");
        if (java.nio.file.Files.isRegularFile(shots)) {
            java.nio.file.Files.copy(shots, de.kronwerke.core.camera.Camera.shotsDir().resolve("shots.json"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        return "Testwelt geladen: " + n + " Dateien. /kw testworld apply baut sie.";
    }

    private static void deleteTree(java.nio.file.Path dir) throws java.io.IOException {
        if (!java.nio.file.Files.exists(dir)) return;
        try (var walk = java.nio.file.Files.walk(dir)) {
            for (java.nio.file.Path f : (Iterable<java.nio.file.Path>) walk.sorted(java.util.Comparator.reverseOrder())::iterator) {
                java.nio.file.Files.deleteIfExists(f);
            }
        }
    }

    private static boolean using;

    /** True while the test world's own hand uses a tool: the stage locks do not apply to it. */
    public static boolean building() {
        return using;
    }

    /**
     * A tool used on a block the way a player would: the hammer that forms a multiblock, a wrench,
     * a wand. The hand keeps the stack while the same item is used again, so two clicks that
     * belong together work (a belt between two shafts, a wire between two connectors). Returns
     * what the game answered.
     */
    public static String use(ServerLevel level, BlockPos pos, net.minecraft.core.Direction face, net.minecraft.world.item.Item item, boolean sneak) {
        var p = net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(level);
        if (hand.isEmpty() || !hand.is(item)) hand = new net.minecraft.world.item.ItemStack(item, item.getDefaultMaxStackSize());
        p.setPos(pos.getX() + 0.5 + face.getStepX() * 1.5, pos.getY() + face.getStepY() * 1.5, pos.getZ() + 0.5 + face.getStepZ() * 1.5);
        // head and body too: where a living thing looks is asked of its head, and a block that
        // turns to face whoever sets it (a drawer) looked at the old direction of the head
        float yaw = face.getAxis().isHorizontal() ? face.getOpposite().toYRot() : 180f;
        p.setYRot(yaw);
        p.setYHeadRot(yaw);
        p.setYBodyRot(yaw);
        p.setXRot(face == net.minecraft.core.Direction.UP ? 90 : face == net.minecraft.core.Direction.DOWN ? -90 : 0);
        p.yRotO = yaw;
        p.yHeadRotO = yaw;
        p.xRotO = p.getXRot();
        p.setShiftKeyDown(sneak);
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, hand);
        // a fake player never ticks, so a cooldown an item sets (Create's belt after the first
        // of its two clicks) would never run out
        p.getCooldowns().removeCooldown(item);
        var at = net.minecraft.world.phys.Vec3.atCenterOf(pos).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
        using = true;
        try {
            var result = p.gameMode.useItemOn(p, level, hand, net.minecraft.world.InteractionHand.MAIN_HAND,
                    new net.minecraft.world.phys.BlockHitResult(at, face, pos, false));
            return result.toString();
        } finally {
            using = false;
            hand = p.getItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND);
            p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.ItemStack.EMPTY);
            p.setShiftKeyDown(false);
        }
    }

    private static net.minecraft.world.item.ItemStack hand = net.minecraft.world.item.ItemStack.EMPTY;

    private static boolean holding;
    private static int wait, patience;
    private static final java.util.Set<Long> held = new java.util.HashSet<>();

    /**
     * Before the first file runs: every chunk of every file's "#area" is held loaded, and the
     * build waits two seconds. A chunk that was loaded in the very tick its file runs has no
     * entities yet (they are read from disk a few ticks later), so what an earlier build had
     * summoned there survived the clearing: an armour stand of a scene that had since moved
     * stood in another scene's picture (13.11.2026), and the items a cleared block dropped
     * were out of the reach of the command that removes them.
     */
    private static void hold(ServerLevel level) {
        holding = true;
        wait = 40;
        patience = 600;
        for (java.nio.file.Path file : files) {
            try {
                for (String raw : java.nio.file.Files.readAllLines(file, StandardCharsets.UTF_8)) {
                    if (!raw.startsWith("#area ")) continue;
                    String[] a = raw.strip().split("\\s+");
                    int x0 = Integer.parseInt(a[1]), z0 = Integer.parseInt(a[2]), x1 = Integer.parseInt(a[3]), z1 = Integer.parseInt(a[4]);
                    for (int cx = x0 >> 4; cx <= x1 >> 4; cx++) {
                        for (int cz = z0 >> 4; cz <= z1 >> 4; cz++) {
                            if (level.getForcedChunks().contains(net.minecraft.world.level.ChunkPos.asLong(cx, cz))) continue;
                            if (held.add(net.minecraft.world.level.ChunkPos.asLong(cx, cz))) level.setChunkForced(cx, cz, true);
                        }
                    }
                }
            } catch (java.io.IOException | RuntimeException ex) {
                KronwerkeCore.LOGGER.warn("Could not read the areas of {}: {}", file, ex.toString());
            }
        }
    }

    /** Two seconds are the least; a large world takes longer to come off the disk, and then the build waits for it (half a minute at most). */
    private static boolean entitiesThere(ServerLevel level) {
        for (long c : held) if (!level.areEntitiesLoaded(c)) return false;
        return true;
    }

    private static void release(ServerLevel level) {
        for (long c : held) level.setChunkForced(net.minecraft.world.level.ChunkPos.getX(c), net.minecraft.world.level.ChunkPos.getZ(c), false);
        held.clear();
        holding = false;
    }

    /** One row per tick, then one file of the server folder per tick. */
    public static void tick(MinecraftServer server) {
        if (queue.isEmpty() && !files.isEmpty()) {
            ServerLevel level = level(server);
            if (level == null) {
                files.clear();
                held.clear();
                holding = false;
                return;
            }
            if (!holding) {
                hold(level);
                return;
            }
            if (wait > 0) {
                wait--;
                return;
            }
            if (patience > 0 && !entitiesThere(level)) {
                patience--;
                return;
            }
            run(server, level, files.poll());
            if (files.isEmpty()) {
                release(level);
                if (fileReport != null) {
                    fileReport.accept("Testwelt: " + filesTotal + " Dateien, " + commands + " Befehle, " + failed + " fehlgeschlagen"
                            + (failed > 0 ? " (siehe die .log neben der Datei)." : "."));
                    fileReport = null;
                }
            }
            return;
        }
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
