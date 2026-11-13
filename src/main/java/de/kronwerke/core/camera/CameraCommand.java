package de.kronwerke.core.camera;

import java.io.Reader;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * Operators drive the camera:
 *
 *   /kw cam                         whether the camera is online, and the queue
 *   /kw cam watch                   spectate through the camera's eyes (sneak to leave)
 *   /kw cam shot <name> <pos> <yaw> <pitch> [fov] [width] [height]
 *                                   one picture from there, saved as kronwerke/shots/<name>.png
 *   /kw cam run [prefix]            every shot of data/kronwerke/shots/shots.json (or those
 *                                   whose name starts with prefix), one after the other
 *   /kw cam stop                    empties the queue
 *   /kw cam bundle [prefix]         the small copies as pieces for the console's files API
 *   /kw cam sheet [prefix]          the same as contact sheets, eight pictures each, to look through
 */
public final class CameraCommand {
    private static final ResourceLocation LIST = ResourceLocation.fromNamespaceAndPath("kronwerke", "shots/shots.json");

    private record Job(String name, ResourceLocation dim, Vec3 pos, float yaw, float pitch, int fov, int w, int h) {
    }

    private static final Deque<Job> queue = new ArrayDeque<>();
    private static boolean busy;
    private static int done, failed;
    private static CommandSourceStack reportTo;

    private CameraCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("kw").then(Commands.literal("cam").requires(s -> s.hasPermission(2))
                .executes(CameraCommand::status)
                .then(Commands.literal("watch").executes(CameraCommand::watch))
                .then(Commands.literal("stop").executes(c -> {
                    int n = queue.size();
                    queue.clear();
                    ok(c, n + " Bilder aus der Warteschlange genommen.");
                    return n;
                }))
                .then(Commands.literal("run").executes(c -> run(c, ""))
                        .then(Commands.argument("prefix", StringArgumentType.word()).executes(c -> run(c, StringArgumentType.getString(c, "prefix")))))
                .then(Commands.literal("clear").executes(c -> background(c, "Aufräumen",
                        () -> ShotFiles.clear(Camera.shotsDir()) + " Dateien gelöscht.")))
                .then(Commands.literal("reopt").executes(c -> reopt(c, ""))
                        .then(Commands.argument("prefix", StringArgumentType.word()).executes(c -> reopt(c, StringArgumentType.getString(c, "prefix")))))
                .then(Commands.literal("bundle").executes(c -> bundle(c, ""))
                        .then(Commands.argument("prefix", StringArgumentType.word()).executes(c -> bundle(c, StringArgumentType.getString(c, "prefix")))))
                .then(Commands.literal("sheet").executes(c -> sheet(c, ""))
                        .then(Commands.argument("prefix", StringArgumentType.word()).executes(c -> sheet(c, StringArgumentType.getString(c, "prefix")))))
                .then(shotNode())));
    }

    /** Makes the small copies again from the full pictures, after the size or palette changed. */
    private static int reopt(com.mojang.brigadier.context.CommandContext<CommandSourceStack> c, String prefix) {
        return background(c, "Kleine Kopien", () -> ShotFiles.reoptimise(Camera.shotsDir(), prefix) + " kleine Kopien neu erstellt.");
    }

    /** Packs the small copies in shots/opt into pieces for the console (shots/bundle). */
    private static int bundle(com.mojang.brigadier.context.CommandContext<CommandSourceStack> c, String prefix) {
        return background(c, "Bündel", () -> ShotFiles.bundle(Camera.shotsDir(), prefix) + " Bilder in kronwerke/shots/bundle gepackt.");
    }

    /** Packs contact sheets of the small copies into shots/bundle, for looking through a run. */
    private static int sheet(com.mojang.brigadier.context.CommandContext<CommandSourceStack> c, String prefix) {
        return background(c, "Kontaktbögen", () -> ShotFiles.sheets(Camera.shotsDir(), prefix) + " Bilder auf Kontaktbögen in kronwerke/shots/bundle gepackt.");
    }

    /** Image work takes seconds to minutes: off the server thread, answered when done. */
    private static int background(com.mojang.brigadier.context.CommandContext<CommandSourceStack> c, String what,
                                  java.util.concurrent.Callable<String> job) {
        CommandSourceStack src = c.getSource();
        MinecraftServer server = src.getServer();
        ShotFiles.WORKER.execute(() -> {
            String msg;
            boolean good;
            try {
                msg = job.call();
                good = true;
            } catch (Exception e) {
                msg = what + " fehlgeschlagen: " + e.getMessage();
                good = false;
            }
            de.kronwerke.core.KronwerkeCore.LOGGER.info("Camera: {}", msg);
            final String m = msg;
            final boolean ok = good;
            server.execute(() -> {
                if (ok) src.sendSuccess(() -> Component.literal(m).withStyle(net.minecraft.ChatFormatting.GREEN), true);
                else src.sendFailure(Component.literal(m));
            });
        });
        ok(c, what + " laufen im Hintergrund, die Antwort kommt hier und im Log.");
        return 1;
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> shotNode() {
        var size = Commands.argument("width", IntegerArgumentType.integer(64, 1920))
                .then(Commands.argument("height", IntegerArgumentType.integer(64, 1080))
                        .executes(c -> shot(c, IntegerArgumentType.getInteger(c, "fov"),
                                IntegerArgumentType.getInteger(c, "width"), IntegerArgumentType.getInteger(c, "height"))));
        var fov = Commands.argument("fov", IntegerArgumentType.integer(30, 110))
                .executes(c -> shot(c, IntegerArgumentType.getInteger(c, "fov"), 1280, 720))
                .then(size);
        var pitch = Commands.argument("pitch", FloatArgumentType.floatArg(-90, 90))
                .executes(c -> shot(c, 60, 1280, 720))
                .then(fov);
        return Commands.literal("shot").then(Commands.argument("name", StringArgumentType.word())
                .then(Commands.argument("pos", Vec3Argument.vec3())
                        .then(Commands.argument("yaw", FloatArgumentType.floatArg(-360, 360)).then(pitch))));
    }

    private static int status(CommandContext<CommandSourceStack> c) {
        ServerPlayer cam = Camera.find(c.getSource().getServer());
        ok(c, (cam == null ? "Die Kamera ist nicht online." : "Die Kamera ist online in " + cam.level().dimension().location() + ".")
                + " Warteschlange: " + queue.size() + (busy ? ", ein Bild läuft." : "."));
        return 1;
    }

    private static int watch(CommandContext<CommandSourceStack> c) {
        ServerPlayer cam = Camera.find(c.getSource().getServer());
        if (cam == null || !(c.getSource().getEntity() instanceof ServerPlayer me)) {
            c.getSource().sendFailure(Component.literal("Die Kamera ist nicht online."));
            return 0;
        }
        me.setGameMode(GameType.SPECTATOR);
        me.teleportTo(cam.serverLevel(), cam.getX(), cam.getY(), cam.getZ(), cam.getYRot(), cam.getXRot());
        me.setCamera(cam);
        ok(c, "Du schaust durch die Kamera. Schleichen beendet das.");
        return 1;
    }

    private static int shot(CommandContext<CommandSourceStack> c, int fov, int w, int h) {
        Vec3 pos = Vec3Argument.getVec3(c, "pos");
        queue.add(new Job(StringArgumentType.getString(c, "name"), c.getSource().getLevel().dimension().location(), pos,
                FloatArgumentType.getFloat(c, "yaw"), FloatArgumentType.getFloat(c, "pitch"), fov, w, h));
        reportTo = c.getSource();
        ok(c, "Bild eingereiht (" + queue.size() + " in der Warteschlange).");
        return 1;
    }

    private static int run(CommandContext<CommandSourceStack> c, String prefix) {
        MinecraftServer s = c.getSource().getServer();
        List<Job> jobs = new ArrayList<>();
        // kronwerke/shots/shots.json in the server folder wins over the pack's list, so poses can
        // be tried out without a pack update
        java.nio.file.Path local = Camera.shotsDir().resolve("shots.json");
        try (Reader r = java.nio.file.Files.exists(local) ? java.nio.file.Files.newBufferedReader(local)
                : s.getResourceManager().getResourceOrThrow(LIST).openAsReader()) {
            JsonElement root = JsonParser.parseReader(r);
            JsonArray list = root.isJsonArray() ? root.getAsJsonArray() : root.getAsJsonObject().getAsJsonArray("shots");
            for (JsonElement e : list) {
                JsonObject o = e.getAsJsonObject();
                String name = o.get("name").getAsString();
                if (!name.startsWith(prefix)) continue;
                JsonArray p = o.getAsJsonArray("pos");
                jobs.add(new Job(name, ResourceLocation.parse(o.has("dim") ? o.get("dim").getAsString() : "kronwerke:testworld"),
                        new Vec3(p.get(0).getAsDouble(), p.get(1).getAsDouble(), p.get(2).getAsDouble()),
                        o.get("yaw").getAsFloat(), o.get("pitch").getAsFloat(),
                        o.has("fov") ? o.get("fov").getAsInt() : 60,
                        o.has("width") ? o.get("width").getAsInt() : 1280, o.has("height") ? o.get("height").getAsInt() : 720));
            }
        } catch (Exception e) {
            c.getSource().sendFailure(Component.literal("Die Liste " + LIST + " ist nicht lesbar: " + e.getMessage()));
            return 0;
        }
        queue.addAll(jobs);
        done = failed = 0;
        reportTo = c.getSource();
        ok(c, jobs.size() + " Bilder eingereiht. /kw cam watch zeigt dir, was die Kamera sieht.");
        return jobs.size();
    }

    /** Every server tick: the next picture once the one before is back. */
    public static void tick(MinecraftServer s) {
        Camera.tick();
        if (busy || queue.isEmpty()) return;
        Job j = queue.poll();
        ServerLevel level = s.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, j.dim()));
        if (level == null) {
            report(j.name() + ": Dimension " + j.dim() + " gibt es nicht.", true);
            return;
        }
        busy = true;
        String err = Camera.shoot(s, level, j.pos().x, j.pos().y, j.pos().z, j.yaw(), j.pitch(), j.name(), j.w(), j.h(), j.fov(), 40, result -> {
            busy = false;
            if (result.isEmpty()) {
                done++;
                report(j.name() + " gespeichert" + (queue.isEmpty() ? ". Fertig: " + done + " Bilder, " + failed + " Fehler." : " (" + queue.size() + " übrig)."), false);
            } else {
                failed++;
                report(j.name() + ": " + result, true);
            }
        });
        if (err != null) {
            busy = false;
            queue.clear();
            report(err + " Warteschlange geleert.", true);
        }
    }

    private static void report(String text, boolean bad) {
        de.kronwerke.core.KronwerkeCore.LOGGER.info("Camera: {}", text);
        if (reportTo != null) reportTo.sendSystemMessage(Component.literal(text).withStyle(bad ? ChatFormatting.RED : ChatFormatting.GRAY));
    }

    private static void ok(CommandContext<CommandSourceStack> c, String text) {
        c.getSource().sendSuccess(() -> Component.literal(text).withStyle(ChatFormatting.GRAY), false);
    }
}
