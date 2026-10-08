package de.kronwerke.core.mixin;

import de.kronwerke.core.share.soph.Gate;
import net.p3pp3rf1y.sophisticatedcore.linkedstorage.LinkedStorageEndpointRecord;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;
import java.util.UUID;

/**
 * An endpoint of a group that the record here does not know was linked on another server (a
 * backpack linked in the mining world and carried home). Sophisticated would refuse it and fail
 * hard; it is taken into the group instead, which is what the other server already did.
 */
@Mixin(targets = "net.p3pp3rf1y.sophisticatedcore.linkedstorage.LinkedStorageGroupRecord", remap = false)
public abstract class SophMembershipMixin {
    @Shadow @Final private Map<UUID, LinkedStorageEndpointRecord> endpoints;

    @Inject(method = "hasEndpoint", at = @At("HEAD"))
    private void kronwerke$takeIn(UUID endpointId, CallbackInfoReturnable<Boolean> cir) {
        if (endpointId != null && Gate.lenient() && !endpoints.containsKey(endpointId))
            endpoints.put(endpointId, new LinkedStorageEndpointRecord(endpointId, null, -1L));
    }
}
