package de.kronwerke.core.net;

/**
 * Slow clients were thrown out while joining. With 250 mods the server is done with a join long
 * before the client is: the client still builds its recipe lists, tags, quest book and first
 * chunks, and while it does, it answers no keep-alive. Vanilla drops a player after 15 seconds
 * without an answer and closes a connection after 30 seconds without any packet, so on a slower
 * PC the join timed out every time. A fresh connection now gets two minutes, an established one 45
 * seconds, and the network read timeout is two minutes on both sides.
 */
public final class JoinGrace {

    /** How long a connection counts as fresh. */
    public static final long FRESH_MS = 180_000L;
    /** Silence a fresh connection may have before it is dropped. */
    public static final long FRESH_SILENCE_MS = 120_000L;
    /** Silence an established connection may have. */
    public static final long SILENCE_MS = 45_000L;
    /** Seconds without any packet before netty closes the connection. */
    public static final int READ_TIMEOUT_S = 120;

    private JoinGrace() {
    }

    public static boolean stillAllowed(long createdAt, long lastKeepAliveSent, long now) {
        long allowed = now - createdAt < FRESH_MS ? FRESH_SILENCE_MS : SILENCE_MS;
        return now - lastKeepAliveSent < allowed;
    }
}
