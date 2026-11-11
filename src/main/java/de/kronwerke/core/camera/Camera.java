package de.kronwerke.core.camera;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import com.mojang.authlib.GameProfile;

import de.kronwerke.core.KronwerkeCore;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The camera on the server side: the player KronwerkeCam, a Minecraft client without a screen
 * that the launcher runs in the same container (see the camera bootstrap). It takes the
 * pictures for the quest book on the real server, so they show exactly what players see.
 * <p>
 * It logs in without a Mojang account, but only over loopback: a connection from outside
 * with that name goes through the normal online login and gets the real account's UUID, which
 * is not the camera's. The camera skips the whitelist, the start gate and the queue, does not
 * count as an active player, and is always in spectator mode.
 */
public final class Camera {
    public static final String NAME = "KronwerkeCam";
    public static final UUID ID = UUIDUtil.createOfflinePlayerUUID(NAME);
    private static final Path SHOTS = FMLPaths.GAMEDIR.get().resolve("kronwerke").resolve("shots");

    private record Pending(String name, Consumer<String> done, byte[][] parts, long since) {
    }

    private static final Map<Integer, Pending> pending = new HashMap<>();
    private static int nextId = 1;

    private Camera() {
    }

    public static boolean is(GameProfile p) {
        return p != null && NAME.equals(p.getName()) && ID.equals(p.getId());
    }

    public static boolean is(Player p) {
        return p != null && is(p.getGameProfile());
    }

    /** At the login: this name may skip Mojang's check only from inside the container. */
    public static boolean offlineLogin(SocketAddress from, String name) {
        if (!NAME.equals(name) || !(from instanceof InetSocketAddress a)) return false;
        boolean local = a.getAddress() != null && a.getAddress().isLoopbackAddress();
        if (local) KronwerkeCore.LOGGER.info("The camera logs in from {}", a);
        return local;
    }

    public static ServerPlayer find(MinecraftServer s) {
        return s.getPlayerList().getPlayer(ID);
    }

    public static void onJoin(ServerPlayer p) {
        if (!is(p)) return;
        p.setGameMode(GameType.SPECTATOR);
    }

    /** Moves the camera and asks it for a picture; done gets an error or "" when the file is written. */
    public static String shoot(MinecraftServer s, ServerLevel level, double x, double y, double z, float yaw, float pitch,
                               String name, int width, int height, int fov, int settle, Consumer<String> done) {
        ServerPlayer cam = find(s);
        if (cam == null) return "Die Kamera ist nicht online.";
        if (!name.matches("[a-z0-9_.-]{1,80}")) return "Name: Kleinbuchstaben, Ziffern, _ . -";
        cam.setGameMode(GameType.SPECTATOR);
        cam.teleportTo(level, x, y, z, yaw, pitch);
        int id = nextId++;
        pending.put(id, new Pending(name, done, null, System.currentTimeMillis()));
        PacketDistributor.sendToPlayer(cam, new ShotPayload(id, width, height, fov, settle));
        return null;
    }

    public static void onPart(ShotPartPayload part, ServerPlayer from) {
        if (!is(from)) return;
        Pending p = pending.get(part.id());
        if (p == null || part.total() <= 0 || part.total() > 400 || part.index() < 0 || part.index() >= part.total()) return;
        byte[][] parts = p.parts() == null ? new byte[part.total()][] : p.parts();
        if (p.parts() == null) {
            p = new Pending(p.name(), p.done(), parts, p.since());
            pending.put(part.id(), p);
        }
        parts[part.index()] = part.data();
        for (byte[] b : parts) if (b == null) return;
        pending.remove(part.id());
        int size = 0;
        for (byte[] b : parts) size += b.length;
        byte[] png = new byte[size];
        int at = 0;
        for (byte[] b : parts) {
            System.arraycopy(b, 0, png, at, b.length);
            at += b.length;
        }
        try {
            Files.createDirectories(SHOTS);
            Files.write(SHOTS.resolve(p.name() + ".png"), png);
            ShotFiles.writeOptimised(SHOTS, p.name(), png);
            p.done().accept("");
        } catch (IOException e) {
            p.done().accept("Nicht gespeichert: " + e.getMessage());
        }
    }

    /** Pictures that never came back (the camera left, the client hung): answered after a minute. */
    public static void tick() {
        long now = System.currentTimeMillis();
        pending.entrySet().removeIf(e -> {
            if (now - e.getValue().since() < 60_000) return false;
            e.getValue().done().accept("Die Kamera hat nicht geantwortet.");
            return true;
        });
    }

    public static Path shotsDir() {
        return SHOTS;
    }
}
