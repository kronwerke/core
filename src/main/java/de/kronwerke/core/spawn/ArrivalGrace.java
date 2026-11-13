package de.kronwerke.core.spawn;

import de.kronwerke.core.KronwerkeCore;
import de.kronwerke.core.config.KronwerkeConfig;
import net.minecraft.network.protocol.common.ClientboundPingPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Nobody dies while their game still loads. With 250 mods a client that joins a server, or
 * moves to another one, stands in the world for the server long before it can do anything: it
 * builds its recipe lists first and neither draws nor reacts for that time, twenty seconds on
 * a fast PC and far more on a slow one. A zombie that finds the player in that time kills them
 * without them ever seeing it (the test player died that way twice at the farm world's portal).
 * <p>
 * So an arrival takes no damage and no mob picks them as a target until their client answers.
 * The server sends a ping behind everything else of the login; the client handles its packets
 * in order on its main thread, so the answer only comes once the loading is through. Three
 * seconds after that the protection ends. It also ends when the arrival hurts someone, and
 * after {@code arrival.graceSeconds} at the latest, whatever the client does. The void and
 * /kill still kill.
 */
public final class ArrivalGrace {
    private ArrivalGrace() {
    }

    private static final long TAIL_MS = 3_000;
    private static final int PING_BASE = 0x4B570000;

    /** since, the ping's id, the end once the answer is there (0 while it is not). */
    private static final Map<UUID, long[]> arriving = new ConcurrentHashMap<>();
    private static final AtomicInteger pings = new AtomicInteger();

    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || p.connection == null) return;
        if (KronwerkeConfig.ARRIVAL_GRACE.get() <= 0) return;
        int id = PING_BASE | (pings.incrementAndGet() & 0xFFFF);
        arriving.put(p.getUUID(), new long[]{System.currentTimeMillis(), id, 0});
        p.connection.send(new ClientboundPingPacket(id));
    }

    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        arriving.remove(event.getEntity().getUUID());
    }

    /** ArrivalPongMixin, on the server thread: the client answered a ping. */
    public static void pong(ServerPlayer p, int id) {
        long[] a = arriving.get(p.getUUID());
        if (a == null || a[1] != id || a[2] != 0) return;
        long now = System.currentTimeMillis();
        a[2] = now + TAIL_MS;
        KronwerkeCore.LOGGER.info("{} is through loading after {} s, protected for {} s more",
                p.getGameProfile().getName(), (now - a[0]) / 1000, TAIL_MS / 1000);
    }

    public static boolean active(ServerPlayer p) {
        long[] a = arriving.get(p.getUUID());
        if (a == null) return false;
        long now = System.currentTimeMillis();
        if ((a[2] != 0 && now >= a[2]) || now - a[0] >= KronwerkeConfig.ARRIVAL_GRACE.get() * 1000L) {
            if (a[2] == 0) KronwerkeCore.LOGGER.info("{} did not answer within {} s of arriving, the protection ends",
                    p.getGameProfile().getName(), KronwerkeConfig.ARRIVAL_GRACE.get());
            arriving.remove(p.getUUID());
            return false;
        }
        return true;
    }

    public static void onDamage(LivingIncomingDamageEvent event) {
        if (arriving.isEmpty()) return;
        var src = event.getSource();
        // who fights is here: an arrival that hurts someone gives the protection up
        if (src.getEntity() instanceof ServerPlayer attacker && attacker != event.getEntity()) arriving.remove(attacker.getUUID());
        if (!(event.getEntity() instanceof ServerPlayer p) || !active(p)) return;
        if (src.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        event.setCanceled(true);
    }

    public static void onTarget(LivingChangeTargetEvent event) {
        if (arriving.isEmpty()) return;
        if (event.getNewAboutToBeSetTarget() instanceof ServerPlayer p && active(p)) event.setCanceled(true);
    }
}
