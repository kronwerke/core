package de.kronwerke.core.share.soph;

import de.kronwerke.core.share.soph.SophWrapperAccess;
import net.p3pp3rf1y.sophisticatedcore.api.IStorageWrapper;
import net.p3pp3rf1y.sophisticatedcore.linkedstorage.ILinkedStorageVirtualHost;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Which linked storage groups are on another server right now. Their record here holds nothing
 * and every inventory and upgrade slot of them refuses to take or give (the Soph*GateMixins),
 * so nothing can go in here that would be overwritten when the group comes back.
 */
public final class Gate {
    static final Set<UUID> AWAY = ConcurrentHashMap.newKeySet();
    /** An endpoint the record does not know yet is taken in (it was linked on another server). */
    static volatile boolean lenient;

    private Gate() {
    }

    public static boolean lenient() {
        return lenient;
    }

    public static boolean away(UUID group) {
        return group != null && AWAY.contains(group);
    }

    /** The group a storage wrapper shows, when it is a linked one. */
    public static Optional<UUID> groupOf(IStorageWrapper w) {
        if (w == null) return Optional.empty();
        if (w instanceof ILinkedStorageVirtualHost) return w.getContentsUuid();
        try {
            if (w.getInventoryHandler() instanceof SophWrapperAccess a && a.kronwerke$wrapper() instanceof ILinkedStorageVirtualHost h)
                return ((IStorageWrapper) h).getContentsUuid();
        } catch (RuntimeException e) {
            // a wrapper that cannot build its handler right now is not linked storage we know of
        }
        return Optional.empty();
    }

    /** For the gate mixins: this handler belongs to a group that is elsewhere. */
    public static boolean blocked(IStorageWrapper w) {
        if (AWAY.isEmpty() || !(w instanceof ILinkedStorageVirtualHost)) return false;
        return w.getContentsUuid().map(AWAY::contains).orElse(false);
    }
}
