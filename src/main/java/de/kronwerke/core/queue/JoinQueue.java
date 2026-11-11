package de.kronwerke.core.queue;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import com.mojang.authlib.GameProfile;

import de.kronwerke.core.KronwerkeCore;
import de.kronwerke.core.config.KronwerkeConfig;
import de.kronwerke.core.link.NetworkSync;
import de.kronwerke.core.link.Role;
import de.kronwerke.core.slot.SlotData;
import de.kronwerke.core.slot.SlotManager;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.configuration.ServerConfigurationPacketListener;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ConfigurationTask;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.configuration.ICustomConfigurationTask;
import net.neoforged.neoforge.network.event.RegisterConfigurationTasksEvent;

/**
 * The join queue. When the network is full, a joining player is not turned away: the
 * connection stays in its configuration phase (registries already synced, the client showing
 * the queue screen) until a place is free, then the join simply goes on. Nobody reconnects.
 * <p>
 * Full means the players on every server of the network, plus those let in a moment ago who
 * are still loading, reach queueLimit. Operators, owners, streamers and players coming back
 * from a side world never wait. Waiting players get their place every two seconds; a client
 * that leaves the queue is dropped from it at once. Vanilla's own player limit is bypassed
 * while the queue is on (see PlayerListQueueMixin), so it is the only limit.
 */
public final class JoinQueue {

    public static final ConfigurationTask.Type TYPE = new ConfigurationTask.Type(KronwerkeCore.MOD_ID + ":queue");

    private record Waiting(ServerConfigurationPacketListenerImpl listener, GameProfile profile, long since) {
    }

    private static final Deque<Waiting> waiting = new ArrayDeque<>();
    /** Let in and still between the end of the queue and the login, with when. */
    private static final Map<UUID, Long> admitted = new HashMap<>();
    /** When the last players were let in, for the estimate on the queue screen. */
    private static final java.util.ArrayDeque<Long> admits = new java.util.ArrayDeque<>();

    /** Seconds until this place's turn, from how fast places freed up lately; 0 when unknown. */
    static int eta(int position) {
        if (admits.size() < 3 || position <= 0) return 0;
        long span = admits.peekLast() - admits.peekFirst();
        if (span <= 0 || System.currentTimeMillis() - admits.peekLast() > 30 * 60_000L) return 0;
        double each = span / 1000.0 / (admits.size() - 1);
        return (int) Math.min(6 * 3600, Math.round(each * position));
    }
    /** Who left lately (a crash, a timeout, a quick restart of the game): back in without waiting for three minutes. */
    private static final Map<UUID, Long> leftAt = new HashMap<>();

    public static void onLeave(UUID id) {
        if (on()) leftAt.put(id, System.currentTimeMillis());
    }
    private static MinecraftServer server;
    private static int ticks;

    private JoinQueue() {
    }

    public static boolean on() {
        return Role.main() && KronwerkeConfig.SPEC.isLoaded() && KronwerkeConfig.QUEUE.get();
    }

    /**
     * The limit set in game, kept in kronwerke/queue-limit.txt next to the server: the config
     * file comes with the pack and every pack update would put it back. Without one, the config's.
     */
    private static final java.nio.file.Path LIMIT_FILE = net.neoforged.fml.loading.FMLPaths.GAMEDIR.get().resolve("kronwerke").resolve("queue-limit.txt");
    private static Integer setLimit;

    public static int limit() {
        if (setLimit == null) {
            setLimit = 0;
            try {
                if (java.nio.file.Files.exists(LIMIT_FILE)) setLimit = Integer.parseInt(java.nio.file.Files.readString(LIMIT_FILE).trim());
            } catch (java.io.IOException | NumberFormatException e) {
                KronwerkeCore.LOGGER.warn("{} could not be read: {}", LIMIT_FILE, e.toString());
            }
        }
        return setLimit > 0 ? setLimit : KronwerkeConfig.QUEUE_LIMIT.get();
    }

    /** Everybody on the network, plus those let in who are still on their way. */
    public static int online() {
        if (server == null) return 0;
        int n = server.getPlayerCount() + NetworkSync.remoteCount();
        for (UUID id : admitted.keySet()) if (server.getPlayerList().getPlayer(id) == null) n++;
        return n;
    }

    public static int waitingCount() {
        return waiting.size();
    }

    public static void start(MinecraftServer s) {
        server = s;
        waiting.clear();
        admitted.clear();
    }

    /** NeoForge asks each new connection's configuration for its tasks; the queue is one of them. */
    public static void onTasks(RegisterConfigurationTasksEvent e) {
        if (!on()) return;
        ServerConfigurationPacketListener l = e.getListener();
        if (l instanceof ServerConfigurationPacketListenerImpl impl) e.register(new Task(impl));
    }

    private record Task(ServerConfigurationPacketListenerImpl listener) implements ICustomConfigurationTask {
        @Override
        public void run(Consumer<CustomPacketPayload> sender) {
            GameProfile p = listener.getOwner();
            if (server == null || !on() || bypass(p) || (waiting.isEmpty() && online() < limit())) {
                if (server != null) admitted.put(p.getId(), System.currentTimeMillis());
                listener.finishCurrentTask(TYPE);
                return;
            }
            waiting.removeIf(w -> w.profile().getId().equals(p.getId())); // a reconnect takes the old place's end
            waiting.addLast(new Waiting(listener, p, System.currentTimeMillis()));
            KronwerkeCore.LOGGER.info("{} waits in the join queue at {} ({} of {} on the network)", p.getName(), waiting.size(), online(), limit());
            sender.accept(new QueuePayload(waiting.size(), waiting.size(), online(), limit(), eta(waiting.size())));
        }

        @Override
        public ConfigurationTask.Type type() {
            return TYPE;
        }
    }

    /** Who never waits: operators, owners, streamers, and players the network already counts. */
    static boolean bypass(GameProfile p) {
        if (de.kronwerke.core.camera.Camera.is(p) || de.kronwerke.core.camera.Camera.isTester(p)) return true;
        Long left = leftAt.get(p.getId());
        if (left != null && System.currentTimeMillis() - left < 180_000) return true;
        if (server.getPlayerList().isOp(p)) return true;
        for (String owner : KronwerkeConfig.OWNERS.get()) if (owner.equalsIgnoreCase(p.getName()) || owner.equalsIgnoreCase(p.getId().toString())) return true;
        if (SlotManager.get().isStreamer(p.getId())) return true;
        // coming back from a side world: already one of the network's players
        return de.kronwerke.core.portal.AwayData.get(server).where(p.getId()) != null;
    }

    public static void onTick(ServerTickEvent.Post e) {
        if (server == null) return;
        ticks++;
        long now = System.currentTimeMillis();
        admitted.entrySet().removeIf(en -> now - en.getValue() > 120_000 || server.getPlayerList().getPlayer(en.getKey()) != null);
        leftAt.values().removeIf(t -> now - t > 180_000);
        if (waiting.isEmpty()) return;
        boolean moved = false;
        for (Iterator<Waiting> it = waiting.iterator(); it.hasNext(); ) {
            Waiting w = it.next();
            if (!w.listener().isAcceptingMessages()) {
                it.remove();
                moved = true;
                KronwerkeCore.LOGGER.info("{} left the join queue", w.profile().getName());
            }
        }
        while (!waiting.isEmpty() && (!on() || online() < limit())) {
            Waiting w = waiting.pollFirst();
            if (!w.listener().isAcceptingMessages()) continue;
            admitted.put(w.profile().getId(), now);
            admits.addLast(now);
            while (admits.size() > 20) admits.removeFirst();
            send(w, 0);
            KronwerkeCore.LOGGER.info("{} leaves the join queue after {} s", w.profile().getName(), (now - w.since()) / 1000);
            try {
                w.listener().finishCurrentTask(TYPE);
            } catch (IllegalStateException ex) {
                KronwerkeCore.LOGGER.warn("Join queue: {} could not go on: {}", w.profile().getName(), ex.getMessage());
            }
            moved = true;
        }
        if (moved || ticks % 40 == 0) {
            int i = 1;
            for (Waiting w : waiting) send(w, i++);
        }
    }

    private static void send(Waiting w, int position) {
        w.listener().send(new ClientboundCustomPayloadPacket(new QueuePayload(position, waiting.size(), online(), limit(), eta(position))));
    }

    /** /kw admin queue limit: the new limit, kept next to the server (see {@link #limit()}). */
    public static void setLimit(int n) {
        setLimit = n;
        try {
            java.nio.file.Files.createDirectories(LIMIT_FILE.getParent());
            java.nio.file.Files.writeString(LIMIT_FILE, Integer.toString(n));
        } catch (java.io.IOException e) {
            KronwerkeCore.LOGGER.warn("{} could not be written: {}", LIMIT_FILE, e.toString());
        }
    }

    public static String status() {
        StringBuilder b = new StringBuilder();
        b.append(on() ? "Warteschlange an" : "Warteschlange aus").append(", ").append(online()).append(" von ").append(limit()).append(" im Netzwerk, ")
                .append(waiting.size()).append(" warten");
        int i = 1;
        for (Waiting w : waiting) {
            b.append(i == 1 ? ": " : ", ").append(i++).append(". ").append(w.profile().getName());
            if (i > 10) {
                b.append(", ...");
                break;
            }
        }
        return b.toString();
    }
}
