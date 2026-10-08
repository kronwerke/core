package de.kronwerke.core.mixin;

import net.p3pp3rf1y.sophisticatedcore.linkedstorage.ILinkedStorageVirtualHost;
import net.p3pp3rf1y.sophisticatedcore.linkedstorage.LinkedStorageGroupManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;
import java.util.UUID;

@Mixin(value = LinkedStorageGroupManager.class, remap = false)
public interface SophManagerAccessor {
    @Accessor("virtualHosts")
    Map<UUID, ILinkedStorageVirtualHost> kronwerke$virtualHosts();
}
