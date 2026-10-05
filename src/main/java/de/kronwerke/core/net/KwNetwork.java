package de.kronwerke.core.net;

import de.kronwerke.core.KronwerkeCore;
import de.kronwerke.core.slot.SlotData;
import de.kronwerke.core.slot.SlotManager;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The two packets of the streamer menu. The server sends the streamer's slots and the
 * players in them; the client answers with an invite or a revoke by name and gets the
 * fresh list back, plus a line that says what happened.
 */
public final class KwNetwork {
    private KwNetwork() {
    }

    public record SlotsPayload(int used, int total, List<Entry> entries, boolean open, String message, boolean error) implements CustomPacketPayload {
        public static final Type<SlotsPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(KronwerkeCore.MOD_ID, "slots"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SlotsPayload> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, SlotsPayload::used,
                ByteBufCodecs.VAR_INT, SlotsPayload::total,
                Entry.CODEC.apply(ByteBufCodecs.list()), SlotsPayload::entries,
                ByteBufCodecs.BOOL, SlotsPayload::open,
                ByteBufCodecs.STRING_UTF8, SlotsPayload::message,
                ByteBufCodecs.BOOL, SlotsPayload::error,
                SlotsPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record Entry(UUID id, String name, boolean online) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Entry> CODEC = StreamCodec.composite(
                net.minecraft.core.UUIDUtil.STREAM_CODEC, Entry::id,
                ByteBufCodecs.STRING_UTF8, Entry::name,
                ByteBufCodecs.BOOL, Entry::online,
                Entry::new);
    }

    /** action: "invite", "revoke" or "refresh". */
    public record ActionPayload(String action, String name) implements CustomPacketPayload {
        public static final Type<ActionPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(KronwerkeCore.MOD_ID, "slot_action"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ActionPayload> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, ActionPayload::action,
                ByteBufCodecs.STRING_UTF8, ActionPayload::name,
                ActionPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("1").optional();
        r.playToClient(SlotsPayload.TYPE, SlotsPayload.CODEC, KwNetwork::onSlots);
        r.playToServer(ActionPayload.TYPE, ActionPayload.CODEC, KwNetwork::onAction);
    }

    private static void onSlots(SlotsPayload payload, IPayloadContext ctx) {
        if (FMLEnvironment.dist.isClient()) {
            ctx.enqueueWork(() -> de.kronwerke.core.client.InviteScreen.receive(payload));
        }
    }

    private static void onAction(ActionPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer p)) return;
            SlotManager sm = SlotManager.get();
            SlotData.StreamerEntry me = sm.entry(p.getUUID(), p.getGameProfile().getName());
            String name = payload.name().trim();
            String msg = "";
            boolean error = false;
            switch (payload.action()) {
                case "invite" -> {
                    SlotManager.Result res = sm.invite(me, name);
                    error = res != SlotManager.Result.OK;
                    msg = switch (res) {
                        case OK -> name + " steht auf der Whitelist.";
                        case NO_SLOTS_LEFT -> "Du hast keinen freien Platz mehr.";
                        case ALREADY_INVITED -> name + " hat schon einen Platz.";
                        case ALREADY_WHITELISTED -> name + " ist schon auf der Whitelist.";
                        case UNKNOWN_PLAYER -> "Kein Minecraft-Konto mit dem Namen " + name + ". Schreibweise prüfen.";
                        default -> "Das hat nicht geklappt.";
                    };
                }
                case "revoke" -> {
                    SlotManager.Result res = sm.revoke(me, name);
                    error = res != SlotManager.Result.OK;
                    msg = switch (res) {
                        case OK -> name + " ist von der Whitelist runter, der Platz ist wieder frei.";
                        case NOT_INVITED_BY_YOU -> name + " hat seinen Platz nicht von dir.";
                        case UNKNOWN_PLAYER -> "Kein Minecraft-Konto mit dem Namen " + name + ".";
                        default -> "Das hat nicht geklappt.";
                    };
                }
                default -> {
                }
            }
            send(p, false, msg, error);
        });
    }

    /** Sends the streamer's slots to their client; open makes the client show the menu. */
    public static void send(ServerPlayer p, boolean open, String message, boolean error) {
        SlotManager sm = SlotManager.get();
        SlotData.StreamerEntry me = sm.entry(p.getUUID(), p.getGameProfile().getName());
        List<Entry> entries = new ArrayList<>();
        for (Map.Entry<UUID, String> e : me.invited.entrySet()) {
            boolean online = p.getServer().getPlayerList().getPlayer(e.getKey()) != null;
            entries.add(new Entry(e.getKey(), e.getValue(), online));
        }
        PacketDistributor.sendToPlayer(p, new SlotsPayload(me.used(), sm.allowance(me), entries, open, message, error));
    }
}
