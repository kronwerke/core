package de.kronwerke.core.portal;

import de.kronwerke.core.config.KronwerkeConfig;
import de.kronwerke.core.link.NetworkSync;
import de.kronwerke.core.link.Role;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Which server of the network a dimension lives on (network.dimensionServers). The Nether and
 * the End run on servers of their own; main keeps everything else.
 * <p>
 * On main a listed dimension is elsewhere once a server with its role is on the bus; without
 * one (a single server, a test world) it stays here. On a dimension server only its own
 * dimensions are here, everything else belongs to main (or to the dimension's own server).
 * Every other side world (the mining world) keeps all its dimensions, as before.
 */
public final class Dimensions {
    private static List<? extends String> cachedFrom;
    private static Map<String, String> cached = Map.of();

    private Dimensions() {
    }

    private static Map<String, String> table() {
        List<? extends String> list = KronwerkeConfig.SPEC.isLoaded() ? KronwerkeConfig.DIMENSION_SERVERS.get() : List.of();
        if (list != cachedFrom) {
            Map<String, String> m = new HashMap<>();
            for (String entry : list) {
                int i = entry.indexOf('=');
                if (i > 0) m.put(entry.substring(0, i).trim(), entry.substring(i + 1).trim());
            }
            cached = m;
            cachedFrom = list;
        }
        return cached;
    }

    /** Whether this server is one of the dimension servers (its role is in the table). */
    public static boolean dimensionServer() {
        return !Role.main() && table().containsValue(Role.role());
    }

    /** Whether the dimension is played on this server. */
    public static boolean here(ResourceKey<Level> dim) {
        String role = table().get(dim.location().toString());
        if (Role.main()) return role == null || NetworkSync.peerByRole(role) == null;
        if (!dimensionServer()) return true;
        return Role.role().equals(role);
    }

    /**
     * The server that has the dimension when it is not here, or null when it is here or its
     * server is not known on the bus right now.
     */
    public static NetworkSync.Peer host(ResourceKey<Level> dim) {
        if (here(dim)) return null;
        String role = table().get(dim.location().toString());
        if (role != null) {
            NetworkSync.Peer p = NetworkSync.peerByRole(role);
            if (p != null) return p;
        }
        return NetworkSync.peerByRole("main");
    }

    /** Whether a launcher role is one of the dimension servers. */
    public static boolean isDimensionRole(String role) {
        return table().containsValue(role);
    }

    /** Whether the server can take a player right now. */
    public static boolean up(NetworkSync.Peer p) {
        return p != null && p.bus() && p.running() && !p.host().isEmpty();
    }
}
