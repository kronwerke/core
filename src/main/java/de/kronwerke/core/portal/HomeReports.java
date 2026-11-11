package de.kronwerke.core.portal;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.kronwerke.core.KronwerkeCore;
import de.kronwerke.core.link.NetworkSync;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A side world's reports of a player's state to main (player.home when they log out there or
 * the server stops, player.checkpoint every 30 seconds), delivered until main confirms it
 * wrote them. Only the newest report per player matters, so a newer one replaces an older
 * one that is still waiting. The waiting reports are kept in a file next to the world, so a
 * report that went out while main was down, or right before this server stopped, reaches
 * main when both are up again (review R3-01: they used to be fire and forget).
 * <p>
 * Server thread only.
 */
final class HomeReports {
    private static final long RESEND_MS = 10_000;

    private record Report(long seq, String topic, JsonObject data, long sentAt) {}

    private static final Map<UUID, Report> waiting = new HashMap<>();
    private static MinecraftServer server;
    private static long lastSeq;

    private HomeReports() {
    }

    static void start(MinecraftServer s) {
        server = s;
        waiting.clear();
        Path f = file();
        if (f == null || !Files.exists(f)) return;
        try {
            JsonObject all = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
            for (var e : all.entrySet()) {
                JsonObject r = e.getValue().getAsJsonObject();
                long seq = r.get("seq").getAsLong();
                waiting.put(UUID.fromString(e.getKey()), new Report(seq, r.get("topic").getAsString(), r.getAsJsonObject("data"), 0));
                lastSeq = Math.max(lastSeq, seq);
            }
            if (!waiting.isEmpty()) KronwerkeCore.LOGGER.info("{} player reports still wait for main", waiting.size());
        } catch (IOException | RuntimeException e) {
            KronwerkeCore.LOGGER.error("Could not read the waiting player reports from {}: {}", f, e.toString());
        }
    }

    static void stop() {
        save();
        server = null;
    }

    /** Sends a report and keeps it until main answers. A final home is never replaced by a later checkpoint. */
    static void report(UUID id, String topic, JsonObject data) {
        Report old = waiting.get(id);
        if (old != null && old.topic().equals("player.home") && topic.equals("player.checkpoint")) return;
        long seq = Math.max(lastSeq + 1, System.currentTimeMillis());
        lastSeq = seq;
        data.addProperty("rseq", seq);
        Report r = new Report(seq, topic, data, 0);
        waiting.put(id, r);
        save();
        send(id, r);
    }

    /** Whether a report for the player still waits for main. */
    static boolean waits(UUID id) {
        return waiting.containsKey(id);
    }

    /** Main asks for a player now (they try to log in there): the waiting report again, or "none". */
    static void pull(UUID id) {
        Report r = waiting.get(id);
        if (r != null) {
            send(id, r);
            return;
        }
        JsonObject d = new JsonObject();
        d.addProperty("uuid", id.toString());
        NetworkSync.Peer home = NetworkSync.portalTarget();
        if (home != null) NetworkSync.send(home.name(), "player.none", d, null);
    }

    /** Main wrote it. */
    static void acked(UUID id, long seq) {
        Report r = waiting.get(id);
        if (r != null && r.seq() == seq) {
            waiting.remove(id);
            save();
        }
    }

    /** Main has the player itself now: nothing from here counts for them any more. */
    static void claimed(UUID id) {
        if (waiting.remove(id) != null) save();
    }

    static void tick() {
        if (waiting.isEmpty()) return;
        long now = System.currentTimeMillis();
        for (var e : waiting.entrySet()) {
            if (now - e.getValue().sentAt() >= RESEND_MS) send(e.getKey(), e.getValue());
        }
    }

    private static void send(UUID id, Report r) {
        NetworkSync.Peer home = NetworkSync.portalTarget();
        if (home == null || !home.bus()) return;
        NetworkSync.send(home.name(), r.topic(), r.data(), null);
        waiting.put(id, new Report(r.seq(), r.topic(), r.data(), System.currentTimeMillis()));
    }

    private static Path file() {
        return server == null ? null : server.getWorldPath(LevelResource.ROOT).resolve("kronwerke").resolve("home_reports.json");
    }

    /** Small, and written on every change: a report must survive this server crashing a second later. */
    private static void save() {
        Path f = file();
        if (f == null) return;
        JsonObject all = new JsonObject();
        for (var e : waiting.entrySet()) {
            JsonObject r = new JsonObject();
            r.addProperty("seq", e.getValue().seq());
            r.addProperty("topic", e.getValue().topic());
            r.add("data", e.getValue().data());
            all.add(e.getKey().toString(), r);
        }
        try {
            Files.createDirectories(f.getParent());
            Path tmp = f.resolveSibling(f.getFileName() + ".tmp");
            Files.writeString(tmp, all.toString(), StandardCharsets.UTF_8);
            Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            KronwerkeCore.LOGGER.error("Could not save the waiting player reports: {}", e.toString());
        }
    }
}
