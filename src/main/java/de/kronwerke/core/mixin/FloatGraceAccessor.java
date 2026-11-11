package de.kronwerke.core.mixin;

import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The counter behind vanilla's "kicked for floating too long". A player who just arrived from
 * another server may hang in the air while the client still loads the world (a teleport to
 * a spot high above the ground, a slow PC); Crossing keeps the counter at zero for a while.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public interface FloatGraceAccessor {
    @Accessor("aboveGroundTickCount")
    void kronwerke$setAboveGroundTicks(int ticks);
}
