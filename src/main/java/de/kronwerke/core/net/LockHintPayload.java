package de.kronwerke.core.net;

import de.kronwerke.core.KronwerkeCore;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * A machine, altar or ritual did not craft because the stage is not open yet. The client
 * shows it where the player looks: as a banner over the machine's own window when that is
 * open, otherwise as text floating over the block for a few seconds. screen: the player's
 * own crafting or menu click, shown over whatever window is open (pos unused).
 */
public record LockHintPayload(BlockPos pos, boolean screen, Component text) implements CustomPacketPayload {
    public static final Type<LockHintPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(KronwerkeCore.MOD_ID, "lock_hint"));
    public static final StreamCodec<RegistryFriendlyByteBuf, LockHintPayload> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, LockHintPayload::pos,
            ByteBufCodecs.BOOL, LockHintPayload::screen,
            ComponentSerialization.TRUSTED_STREAM_CODEC, LockHintPayload::text,
            LockHintPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
