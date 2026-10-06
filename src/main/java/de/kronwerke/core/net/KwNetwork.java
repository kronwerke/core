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

    /** The sky torn open for everyone: when it started (game time), how long it stays, which tier was reached. */
    public record SkyPayload(long start, int duration, int tier, double x, double y, double z) implements CustomPacketPayload {
        public static final Type<SkyPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(KronwerkeCore.MOD_ID, "sky"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SkyPayload> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_LONG, SkyPayload::start,
                ByteBufCodecs.VAR_INT, SkyPayload::duration,
                ByteBufCodecs.VAR_INT, SkyPayload::tier,
                ByteBufCodecs.DOUBLE, SkyPayload::x,
                ByteBufCodecs.DOUBLE, SkyPayload::y,
                ByteBufCodecs.DOUBLE, SkyPayload::z,
                SkyPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * Where the obelisk stands and what state it is in, for the effects that are seen from far
     * away, beyond the range in which its blocks are rendered: the signal into the sky, the
     * aurora, the ground rings. x, y, z is the point of the crystal, baseY the floor of the
     * plinth, crowd how many players stand within twelve blocks; tier below zero means there is
     * no obelisk.
     */
    public record StatePayload(String dimension, double x, double y, double z, int baseY, int tier, int mood, int percent, int crowd) implements CustomPacketPayload {
        public static final Type<StatePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(KronwerkeCore.MOD_ID, "state"));
        public static final StreamCodec<RegistryFriendlyByteBuf, StatePayload> CODEC = StreamCodec.of((buf, p) -> {
            buf.writeUtf(p.dimension());
            buf.writeDouble(p.x());
            buf.writeDouble(p.y());
            buf.writeDouble(p.z());
            buf.writeVarInt(p.baseY());
            buf.writeVarInt(p.tier());
            buf.writeVarInt(p.mood());
            buf.writeVarInt(p.percent());
            buf.writeVarInt(p.crowd());
        }, buf -> new StatePayload(buf.readUtf(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * A gift on its way: the item flies from where it was given to the crystal, and the
     * clients nearby draw it, the ripple on the pavement and the number that rises. size is
     * 0 to 3 as the server judged the gift, pillar the index of its pillar or -1.
     */
    public record GiftPayload(double x, double y, double z, net.minecraft.world.item.ItemStack stack, int size, int pillar, long amount) implements CustomPacketPayload {
        public static final Type<GiftPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(KronwerkeCore.MOD_ID, "gift"));
        public static final StreamCodec<RegistryFriendlyByteBuf, GiftPayload> CODEC = StreamCodec.of((buf, p) -> {
            buf.writeDouble(p.x());
            buf.writeDouble(p.y());
            buf.writeDouble(p.z());
            net.minecraft.world.item.ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, p.stack());
            buf.writeVarInt(p.size());
            buf.writeVarInt(p.pillar());
            buf.writeVarLong(p.amount());
        }, buf -> new GiftPayload(buf.readDouble(), buf.readDouble(), buf.readDouble(), net.minecraft.world.item.ItemStack.OPTIONAL_STREAM_CODEC.decode(buf), buf.readVarInt(), buf.readVarInt(), buf.readVarLong()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
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

    /** One goal as the hub shows it. */
    public record GoalView(String title, String description, String state, int percent, boolean active, List<ItemView> items) {
        public static final StreamCodec<RegistryFriendlyByteBuf, GoalView> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, GoalView::title,
                ByteBufCodecs.STRING_UTF8, GoalView::description,
                ByteBufCodecs.STRING_UTF8, GoalView::state,
                ByteBufCodecs.VAR_INT, GoalView::percent,
                ByteBufCodecs.BOOL, GoalView::active,
                ItemView.CODEC.apply(ByteBufCodecs.list()), GoalView::items,
                GoalView::new);
    }

    /** One item of a pillar: id is the item id or #tag the client draws the icon from. */
    public record ItemView(String pillar, String id, String name, long have, long need) {
        public static final StreamCodec<RegistryFriendlyByteBuf, ItemView> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, ItemView::pillar,
                ByteBufCodecs.STRING_UTF8, ItemView::id,
                ByteBufCodecs.STRING_UTF8, ItemView::name,
                ByteBufCodecs.VAR_LONG, ItemView::have,
                ByteBufCodecs.VAR_LONG, ItemView::need,
                ItemView::new);
    }

    /** The /kw hub: goals, what the player may do, and a line about the last action. */
    public record HubPayload(List<GoalView> goals, boolean streamer, boolean admin, String message, boolean error) implements CustomPacketPayload {
        public static final Type<HubPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(KronwerkeCore.MOD_ID, "hub"));
        public static final StreamCodec<RegistryFriendlyByteBuf, HubPayload> CODEC = StreamCodec.composite(
                GoalView.CODEC.apply(ByteBufCodecs.list()), HubPayload::goals,
                ByteBufCodecs.BOOL, HubPayload::streamer,
                ByteBufCodecs.BOOL, HubPayload::admin,
                ByteBufCodecs.STRING_UTF8, HubPayload::message,
                ByteBufCodecs.BOOL, HubPayload::error,
                HubPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record StreamerView(String name, int used, int total, boolean granted, List<String> invited) {
        public static final StreamCodec<RegistryFriendlyByteBuf, StreamerView> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, StreamerView::name,
                ByteBufCodecs.VAR_INT, StreamerView::used,
                ByteBufCodecs.VAR_INT, StreamerView::total,
                ByteBufCodecs.BOOL, StreamerView::granted,
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), StreamerView::invited,
                StreamerView::new);
    }

    public record GoalRow(String id, String title, String state, int percent) {
        static void write(RegistryFriendlyByteBuf b, GoalRow g) {
            b.writeUtf(g.id);
            b.writeUtf(g.title);
            b.writeUtf(g.state);
            b.writeVarInt(g.percent);
        }

        static GoalRow read(RegistryFriendlyByteBuf b) {
            return new GoalRow(b.readUtf(), b.readUtf(), b.readUtf(), b.readVarInt());
        }
    }

    public record PlayerRow(String name, String where, boolean bypass, boolean creative) {
        static void write(RegistryFriendlyByteBuf b, PlayerRow p) {
            b.writeUtf(p.name);
            b.writeUtf(p.where);
            b.writeBoolean(p.bypass);
            b.writeBoolean(p.creative);
        }

        static PlayerRow read(RegistryFriendlyByteBuf b) {
            return new PlayerRow(b.readUtf(), b.readUtf(), b.readBoolean(), b.readBoolean());
        }
    }

    /**
     * The admin panel: season, goals, streamers with their slots, the players online, the
     * obelisk and the test world, plus the tab to show and a line about the last action.
     */
    public record AdminPayload(int tab, boolean seasonRunning, int seasonNumber, List<GoalRow> goals,
                               List<StreamerView> streamers, List<PlayerRow> players, String obelisk,
                               boolean testWorldBuilt, String message, boolean error) implements CustomPacketPayload {
        public static final Type<AdminPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(KronwerkeCore.MOD_ID, "admin"));
        public static final StreamCodec<RegistryFriendlyByteBuf, AdminPayload> CODEC = StreamCodec.of((b, a) -> {
            b.writeVarInt(a.tab);
            b.writeBoolean(a.seasonRunning);
            b.writeVarInt(a.seasonNumber);
            b.writeCollection(a.goals, (bb, g) -> GoalRow.write((RegistryFriendlyByteBuf) bb, g));
            b.writeVarInt(a.streamers.size());
            for (StreamerView sv : a.streamers) StreamerView.CODEC.encode(b, sv);
            b.writeCollection(a.players, (bb, p) -> PlayerRow.write((RegistryFriendlyByteBuf) bb, p));
            b.writeUtf(a.obelisk);
            b.writeBoolean(a.testWorldBuilt);
            b.writeUtf(a.message);
            b.writeBoolean(a.error);
        }, b -> {
            int tab = b.readVarInt();
            boolean running = b.readBoolean();
            int number = b.readVarInt();
            List<GoalRow> goals = b.readList(bb -> GoalRow.read((RegistryFriendlyByteBuf) bb));
            int n = b.readVarInt();
            List<StreamerView> streamers = new ArrayList<>();
            for (int i = 0; i < n; i++) streamers.add(StreamerView.CODEC.decode(b));
            List<PlayerRow> players = b.readList(bb -> PlayerRow.read((RegistryFriendlyByteBuf) bb));
            return new AdminPayload(tab, running, number, goals, streamers, players, b.readUtf(), b.readBoolean(), b.readUtf(), b.readBoolean());
        });

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("3").optional();
        r.playToClient(SlotsPayload.TYPE, SlotsPayload.CODEC, KwNetwork::onSlots);
        r.playToClient(HubPayload.TYPE, HubPayload.CODEC, (payload, ctx) -> {
            if (FMLEnvironment.dist.isClient()) ctx.enqueueWork(() -> de.kronwerke.core.client.KwHubScreen.receive(payload));
        });
        r.playToClient(AdminPayload.TYPE, AdminPayload.CODEC, (payload, ctx) -> {
            if (FMLEnvironment.dist.isClient()) ctx.enqueueWork(() -> de.kronwerke.core.client.AdminScreen.receive(payload));
        });
        r.playToClient(SkyPayload.TYPE, SkyPayload.CODEC, (payload, ctx) -> {
            if (FMLEnvironment.dist.isClient()) ctx.enqueueWork(() -> de.kronwerke.core.client.ObeliskEffects.sky(payload));
        });
        r.playToClient(StatePayload.TYPE, StatePayload.CODEC, (payload, ctx) -> {
            if (FMLEnvironment.dist.isClient()) ctx.enqueueWork(() -> de.kronwerke.core.client.ObeliskEffects.state(payload));
        });
        r.playToClient(GiftPayload.TYPE, GiftPayload.CODEC, (payload, ctx) -> {
            if (FMLEnvironment.dist.isClient()) ctx.enqueueWork(() -> de.kronwerke.core.client.ObeliskEffects.gift(payload));
        });
        r.playToServer(ActionPayload.TYPE, ActionPayload.CODEC, KwNetwork::onAction);
    }

    public static void sendHub(ServerPlayer p, String message, boolean error) {
        de.kronwerke.core.goal.GoalManager gm = de.kronwerke.core.goal.GoalManager.get();
        de.kronwerke.core.goal.GoalData d = gm.progressData();
        List<GoalView> goals = new ArrayList<>();
        for (de.kronwerke.core.goal.Goal g : gm.allGoals()) {
            boolean done = d.isCompleted(g.id());
            boolean active = gm.isActive(g);
            String state = done ? "geschafft" : active ? (gm.isHeld(g) ? "wartet auf das Event" : "läuft") : "gesperrt";
            int percent = done ? 100 : active ? (int) Math.round(gm.fraction(g) * 100) : 0;
            List<ItemView> items = new ArrayList<>();
            if (active) {
                for (de.kronwerke.core.goal.Goal.Pillar pillar : g.pillars()) {
                    for (de.kronwerke.core.goal.Goal.PillarItem it : pillar.items()) {
                        items.add(new ItemView(pillar.title(), it.item(), de.kronwerke.core.Text.item(it.item()).getString(), d.progress(g.id(), it.item()), d.target(g.id(), it.item())));
                    }
                }
            }
            goals.add(new GoalView(g.title(), g.description() == null ? "" : g.description(), state, percent, active, items));
        }
        boolean admin = p.hasPermissions(2);
        boolean streamer = admin || de.kronwerke.core.config.KronwerkeConfig.STREAMERS_MANAGE_OWN_SLOTS.get();
        PacketDistributor.sendToPlayer(p, new HubPayload(goals, streamer, admin, message, error));
    }

    public static void sendAdmin(ServerPlayer p, String message, boolean error) {
        sendAdmin(p, -1, message, error);
    }

    /** tab -1 keeps whatever tab the client shows. */
    public static void sendAdmin(ServerPlayer p, int tab, String message, boolean error) {
        if (!p.hasPermissions(2)) return;
        SlotManager sm = SlotManager.get();
        List<StreamerView> list = new ArrayList<>();
        for (SlotData.StreamerEntry e : sm.allStreamers()) {
            list.add(new StreamerView(e.name, e.used(), sm.allowance(e), e.granted, new ArrayList<>(e.invited.values())));
        }
        list.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
        de.kronwerke.core.goal.GoalManager gm = de.kronwerke.core.goal.GoalManager.get();
        de.kronwerke.core.goal.GoalData d = gm.progressData();
        List<GoalRow> goals = new ArrayList<>();
        for (de.kronwerke.core.goal.Goal g : gm.allGoals()) {
            boolean done = d.isCompleted(g.id());
            boolean active = gm.isActive(g);
            String state = done ? "geschafft" : active ? (gm.isHeld(g) ? "wartet" : "läuft") : "gesperrt";
            goals.add(new GoalRow(g.id(), g.title(), state, done ? 100 : active ? (int) Math.floor(gm.fraction(g) * 100) : 0));
        }
        List<PlayerRow> players = new ArrayList<>();
        for (ServerPlayer o : p.getServer().getPlayerList().getPlayers()) {
            String where = o.level().dimension().location().getPath().replace('_', ' ');
            players.add(new PlayerRow(o.getGameProfile().getName(), where, d.hasBypass(o.getUUID()), o.isCreative()));
        }
        players.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
        de.kronwerke.core.obelisk.ObeliskData od = de.kronwerke.core.obelisk.Obelisk.get().data();
        String obelisk = od.isSet() ? od.pos().getX() + " " + od.pos().getY() + " " + od.pos().getZ()
                + (od.dimension().equals("minecraft:overworld") ? "" : " (" + od.dimension() + ")") : "";
        var tw = de.kronwerke.core.world.TestWorld.level(p.getServer());
        boolean built = tw != null && de.kronwerke.core.world.TestWorld.built(tw);
        de.kronwerke.core.season.Season season = de.kronwerke.core.season.Season.get();
        PacketDistributor.sendToPlayer(p, new AdminPayload(tab, season.running(), season.number(), goals, list, players, obelisk, built, message, error));
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
                case "menu" -> {
                    send(p, true, "", false);
                    return;
                }
                case "hub" -> {
                    sendHub(p, "", false);
                    return;
                }
                case "deposit", "deposit_all" -> {
                    long taken = payload.action().equals("deposit_all") ? de.kronwerke.core.goal.GoalManager.get().depositAll(p)
                            : de.kronwerke.core.goal.GoalManager.get().deposit(p, p.getMainHandItem());
                    sendHub(p, taken > 0 ? "Abgegeben: " + de.kronwerke.core.Text.number(taken) + "." : "Nichts davon passt zum aktuellen Ziel.", taken <= 0);
                    return;
                }
                case "admin" -> {
                    sendAdmin(p, 0, "", false);
                    return;
                }
                case "adm" -> {
                    de.kronwerke.core.admin.AdminActions.run(p, name);
                    return;
                }
                case "admin_slots" -> {
                    if (!p.hasPermissions(2)) return;
                    String[] parts = name.split("\\|");
                    SlotData.StreamerEntry e = sm.entryByName(parts[0]);
                    if (e == null || parts.length < 2) { sendAdmin(p, "Kein Streamer mit dem Namen " + parts[0] + ".", true); return; }
                    int n;
                    try { n = Integer.parseInt(parts[1]); } catch (NumberFormatException ex) { sendAdmin(p, "Das ist keine Zahl.", true); return; }
                    sm.setSlots(e, Math.max(0, n));
                    sendAdmin(p, e.name + " hat jetzt " + sm.allowance(e) + " Plätze.", false);
                    return;
                }
                case "admin_move" -> {
                    if (!p.hasPermissions(2)) return;
                    String[] parts = name.split("\\|");
                    SlotData.StreamerEntry to = parts.length < 2 ? null : sm.entryByName(parts[1]);
                    if (to == null) { sendAdmin(p, "Kein Streamer mit dem Namen " + (parts.length < 2 ? "" : parts[1]) + ".", true); return; }
                    SlotManager.Result res = sm.move(parts[0], to);
                    sendAdmin(p, switch (res) {
                        case OK -> parts[0] + " gehört jetzt zu " + to.name + ".";
                        case NO_SLOTS_LEFT -> to.name + " hat keinen freien Platz.";
                        case NOT_INVITED_BY_YOU -> parts[0] + " hat von niemandem einen Platz.";
                        case ALREADY_INVITED -> parts[0] + " ist schon bei " + to.name + ".";
                        case UNKNOWN_PLAYER -> "Kein Minecraft-Konto mit dem Namen " + parts[0] + ".";
                        default -> "Das hat nicht geklappt.";
                    }, res != SlotManager.Result.OK);
                    return;
                }
                case "admin_revoke" -> {
                    if (!p.hasPermissions(2)) return;
                    java.util.Optional<com.mojang.authlib.GameProfile> profile = sm.lookup(name);
                    SlotData.StreamerEntry from = profile.map(pr -> sm.inviterOf(pr.getId())).orElse(null);
                    if (from == null) { sendAdmin(p, name + " hat von niemandem einen Platz.", true); return; }
                    SlotManager.Result res = sm.revoke(from, name);
                    sendAdmin(p, res == SlotManager.Result.OK ? name + " ist von der Whitelist runter." : "Das hat nicht geklappt.", res != SlotManager.Result.OK);
                    return;
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
