package de.kronwerke.core.mixin;

import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/** Lets the network put players of other servers into the tab list: entries without a ServerPlayer. */
@Mixin(ClientboundPlayerInfoUpdatePacket.class)
public interface PlayerInfoUpdateAccessor {
    @Mutable
    @Accessor("entries")
    void kronwerke$setEntries(List<ClientboundPlayerInfoUpdatePacket.Entry> entries);
}
