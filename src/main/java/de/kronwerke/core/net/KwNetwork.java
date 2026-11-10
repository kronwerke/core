package de.kronwerke.core.net;

import de.kronwerke.core.config.KronwerkeConfig;

import de.kronwerke.core.KronwerkeCore;
import de.kronwerke.core.slot.SlotData;
import de.kronwerke.core.slot.SlotManager;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
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

    /** canInvite: whether the menu invites in game; confirm: the account a name resolved to, to be confirmed. */
    public record SlotsExtra(boolean error, boolean canInvite, List<Entry> confirm) {
        public static final StreamCodec<RegistryFriendlyByteBuf, SlotsExtra> CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, SlotsExtra::error,
                ByteBufCodecs.BOOL, SlotsExtra::canInvite,
                Entry.CODEC.apply(ByteBufCodecs.list()), SlotsExtra::confirm,
                SlotsExtra::new);
    }

    public record SlotsPayload(int used, int total, List<Entry> entries, boolean open, Component message, SlotsExtra extra) implements CustomPacketPayload {
        public boolean error() {
            return extra.error();
        }

        public static final Type<SlotsPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(KronwerkeCore.MOD_ID, "slots"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SlotsPayload> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, SlotsPayload::used,
                ByteBufCodecs.VAR_INT, SlotsPayload::total,
                Entry.CODEC.apply(ByteBufCodecs.list()), SlotsPayload::entries,
                ByteBufCodecs.BOOL, SlotsPayload::open,
                ComponentSerialization.TRUSTED_STREAM_CODEC, SlotsPayload::message,
                SlotsExtra.CODEC, SlotsPayload::extra,
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
    /**
     * active: the goal runs; estimate: its items are the base amounts of a goal that has not
     * opened yet (the real ones depend on the players then); top: a finished goal's most
     * diligent hands as "name|points"; id: the goal id, for the pack's translations of its
     * title, description and pillars ("pillarId|title"); event: when the held goal's event is.
     */
    public record GoalMore(boolean active, boolean estimate, List<String> top, String id, List<String> pillars, String event) {
        public static final StreamCodec<RegistryFriendlyByteBuf, GoalMore> CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, GoalMore::active,
                ByteBufCodecs.BOOL, GoalMore::estimate,
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), GoalMore::top,
                ByteBufCodecs.STRING_UTF8, GoalMore::id,
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), GoalMore::pillars,
                ByteBufCodecs.STRING_UTF8, GoalMore::event,
                GoalMore::new);
    }

    public record GoalView(String title, String description, String state, int percent, GoalMore more, List<ItemView> items) {
        public static final StreamCodec<RegistryFriendlyByteBuf, GoalView> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, GoalView::title,
                ByteBufCodecs.STRING_UTF8, GoalView::description,
                ByteBufCodecs.STRING_UTF8, GoalView::state,
                ByteBufCodecs.VAR_INT, GoalView::percent,
                GoalMore.CODEC, GoalView::more,
                ItemView.CODEC.apply(ByteBufCodecs.list()), GoalView::items,
                GoalView::new);

        public boolean active() {
            return more.active();
        }
    }

    /** One item of a pillar (by pillar id): id is the item id or #tag the client draws the icon and name from; weight is its points per piece. */
    public record ItemView(String pillar, String id, String name, long have, long need, long weight) {
        public static final StreamCodec<RegistryFriendlyByteBuf, ItemView> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, ItemView::pillar,
                ByteBufCodecs.STRING_UTF8, ItemView::id,
                ByteBufCodecs.STRING_UTF8, ItemView::name,
                ByteBufCodecs.VAR_LONG, ItemView::have,
                ByteBufCodecs.VAR_LONG, ItemView::need,
                ByteBufCodecs.VAR_LONG, ItemView::weight,
                ItemView::new);

        public long missingPoints() {
            return Math.max(0, need - have) * weight;
        }
    }

    /** The player's own part and the season's name, for the hub. */
    public record HubMine(String season, long points, int rank, int hands, int streak) {
        public static final StreamCodec<RegistryFriendlyByteBuf, HubMine> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, HubMine::season,
                ByteBufCodecs.VAR_LONG, HubMine::points,
                ByteBufCodecs.VAR_INT, HubMine::rank,
                ByteBufCodecs.VAR_INT, HubMine::hands,
                ByteBufCodecs.VAR_INT, HubMine::streak,
                HubMine::new);
    }

    /** The /kw hub: goals, what the player may do, and a line about the last action. */
    public record HubPayload(List<GoalView> goals, boolean streamer, boolean admin, Component message, boolean error, HubMine mine) implements CustomPacketPayload {
        public static final Type<HubPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(KronwerkeCore.MOD_ID, "hub"));
        public static final StreamCodec<RegistryFriendlyByteBuf, HubPayload> CODEC = StreamCodec.composite(
                GoalView.CODEC.apply(ByteBufCodecs.list()), HubPayload::goals,
                ByteBufCodecs.BOOL, HubPayload::streamer,
                ByteBufCodecs.BOOL, HubPayload::admin,
                ComponentSerialization.TRUSTED_STREAM_CODEC, HubPayload::message,
                ByteBufCodecs.BOOL, HubPayload::error,
                HubMine.CODEC, HubPayload::mine,
                HubPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** kind: streamer, season1, team, or empty for an entry without a place of its own. */
    public record StreamerView(String name, int used, int total, String kind, List<String> invited) {
        public static final StreamCodec<RegistryFriendlyByteBuf, StreamerView> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, StreamerView::name,
                ByteBufCodecs.VAR_INT, StreamerView::used,
                ByteBufCodecs.VAR_INT, StreamerView::total,
                ByteBufCodecs.STRING_UTF8, StreamerView::kind,
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), StreamerView::invited,
                StreamerView::new);

        public boolean granted() {
            return !kind.isEmpty();
        }
    }

    /** The server tab: the start gate, the queue, the machine locks, the scaling, the network. */
    public record ServerView(boolean gateOn, boolean beta, String gateList, boolean queueOn, String queue, int queueLimit,
                             boolean locksOn, String locks, String scaling, String network) {
        static void write(RegistryFriendlyByteBuf b, ServerView v) {
            b.writeBoolean(v.gateOn);
            b.writeBoolean(v.beta);
            b.writeUtf(v.gateList);
            b.writeBoolean(v.queueOn);
            b.writeUtf(v.queue);
            b.writeVarInt(v.queueLimit);
            b.writeBoolean(v.locksOn);
            b.writeUtf(v.locks);
            b.writeUtf(v.scaling);
            b.writeUtf(v.network);
        }

        static ServerView read(RegistryFriendlyByteBuf b) {
            return new ServerView(b.readBoolean(), b.readBoolean(), b.readUtf(), b.readBoolean(), b.readUtf(), b.readVarInt(),
                    b.readBoolean(), b.readUtf(), b.readUtf(), b.readUtf());
        }
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

    /** place: how the player is on the whitelist ("Platz von X", "Streamer", "Team", "von Hand"). */
    public record PlayerRow(String name, String where, boolean bypass, boolean creative, String place) {
        static void write(RegistryFriendlyByteBuf b, PlayerRow p) {
            b.writeUtf(p.name);
            b.writeUtf(p.where);
            b.writeBoolean(p.bypass);
            b.writeBoolean(p.creative);
            b.writeUtf(p.place);
        }

        static PlayerRow read(RegistryFriendlyByteBuf b) {
            return new PlayerRow(b.readUtf(), b.readUtf(), b.readBoolean(), b.readBoolean(), b.readUtf());
        }
    }

    /**
     * The admin panel: season, goals, streamers with their slots, the players online, the
     * obelisk and the test world, plus the tab to show and a line about the last action.
     */
    public record AdminPayload(int tab, boolean seasonRunning, int seasonNumber, List<GoalRow> goals,
                               List<StreamerView> streamers, List<PlayerRow> players, String obelisk,
                               boolean testWorldBuilt, Component message, boolean error, ServerView server) implements CustomPacketPayload {
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
            ComponentSerialization.TRUSTED_STREAM_CODEC.encode(b, a.message);
            b.writeBoolean(a.error);
            ServerView.write(b, a.server);
        }, b -> {
            int tab = b.readVarInt();
            boolean running = b.readBoolean();
            int number = b.readVarInt();
            List<GoalRow> goals = b.readList(bb -> GoalRow.read((RegistryFriendlyByteBuf) bb));
            int n = b.readVarInt();
            List<StreamerView> streamers = new ArrayList<>();
            for (int i = 0; i < n; i++) streamers.add(StreamerView.CODEC.decode(b));
            List<PlayerRow> players = b.readList(bb -> PlayerRow.read((RegistryFriendlyByteBuf) bb));
            String obelisk = b.readUtf();
            boolean built = b.readBoolean();
            Component message = ComponentSerialization.TRUSTED_STREAM_CODEC.decode(b);
            boolean error = b.readBoolean();
            return new AdminPayload(tab, running, number, goals, streamers, players, obelisk, built, message, error, ServerView.read(b));
        });

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("3").optional();
        // the join queue's place, while the connection is still in its configuration phase
        r.configurationToClient(de.kronwerke.core.queue.QueuePayload.TYPE, de.kronwerke.core.queue.QueuePayload.CODEC, (payload, ctx) -> {
            if (net.neoforged.fml.loading.FMLEnvironment.dist.isClient()) {
                ctx.enqueueWork(() -> de.kronwerke.core.client.QueueScreen.show(payload, ctx.connection()));
            }
        });
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
        r.playToClient(de.kronwerke.core.portal.TravelPayload.TYPE, de.kronwerke.core.portal.TravelPayload.CODEC, (payload, ctx) -> {
            if (FMLEnvironment.dist.isClient()) ctx.enqueueWork(() -> de.kronwerke.core.client.TravelOverlay.receive(payload));
        });
    }

    public static void sendHub(ServerPlayer p, String message, boolean error) {
        sendHub(p, Component.literal(message), error);
    }

    public static void sendHub(ServerPlayer p, Component message, boolean error) {
        de.kronwerke.core.goal.GoalManager gm = de.kronwerke.core.goal.GoalManager.get();
        de.kronwerke.core.goal.GoalData d = gm.progressData();
        List<GoalView> goals = new ArrayList<>();
        boolean nextShown = false;
        de.kronwerke.core.goal.Goal running = null;
        for (de.kronwerke.core.goal.Goal g : gm.allGoals()) {
            boolean done = d.isCompleted(g.id());
            boolean active = gm.isActive(g);
            if (active && running == null) running = g;
            String state = done ? "geschafft" : active ? (gm.isHeld(g) ? "wartet auf das Event" : "läuft") : "gesperrt";
            int percent = done ? 100 : active ? (int) Math.round(gm.fraction(g) * 100) : 0;
            List<ItemView> items = new ArrayList<>();
            // the running goal with its real numbers; the next one with its base amounts, to stock up for
            boolean estimate = !done && !active && !nextShown;
            if (active || estimate) {
                for (de.kronwerke.core.goal.Goal.Pillar pillar : g.pillars()) {
                    for (de.kronwerke.core.goal.Goal.PillarItem it : pillar.items()) {
                        long need = active ? d.target(g.id(), it.item()) : it.base();
                        items.add(new ItemView(pillar.id(), it.item(), de.kronwerke.core.Text.item(it.item()).getString(),
                                active ? d.progress(g.id(), it.item()) : 0, need, it.points()));
                    }
                }
            }
            if (estimate) nextShown = true;
            List<String> top = new ArrayList<>();
            if (done) {
                for (var e : gm.leaderboard(g, 5)) top.add(de.kronwerke.core.obelisk.Obelisk.get().nameOf(e.getKey()) + "|" + e.getValue());
            }
            String desc = g.description() == null ? "" : g.description();
            List<String> pillars = new ArrayList<>();
            for (de.kronwerke.core.goal.Goal.Pillar pl : g.pillars()) pillars.add(pl.id() + "|" + (pl.title() == null ? pl.id() : pl.title()));
            String event = active && gm.isHeld(g) ? gm.eventText(g) : "";
            goals.add(new GoalView(g.title(), desc, state, percent, new GoalMore(active, estimate, top, g.id(), pillars, event), items));
        }
        long mine = 0;
        int rank = 0, hands = 0;
        if (running != null) {
            var pts = gm.points(running);
            mine = pts.getOrDefault(p.getUUID(), 0L);
            rank = gm.rank(running, p.getUUID());
            hands = pts.size();
        }
        int streak = de.kronwerke.core.obelisk.Obelisk.get().data().streak(p.getUUID());
        HubMine me = new HubMine(de.kronwerke.core.config.KronwerkeConfig.SEASON_NAME.get(), mine, rank, hands, streak);
        boolean admin = p.hasPermissions(2);
        // the whitelist button only for players with places of their own to give
        boolean streamer = admin || SlotManager.get().ownSlots(p.getUUID()) != null;
        PacketDistributor.sendToPlayer(p, new HubPayload(goals, streamer, admin, message, error, me));
    }

    public static void sendAdmin(ServerPlayer p, String message, boolean error) {
        sendAdmin(p, -1, Component.literal(message), error);
    }

    /** A command's own reply, a component, so it shows in the reader's language where it has one. */
    public static void sendAdmin(ServerPlayer p, Component message, boolean error) {
        sendAdmin(p, -1, message, error);
    }

    public static void sendAdmin(ServerPlayer p, int tab, String message, boolean error) {
        sendAdmin(p, tab, Component.literal(message), error);
    }

    /** tab -1 keeps whatever tab the client shows. */
    public static void sendAdmin(ServerPlayer p, int tab, Component message, boolean error) {
        if (!p.hasPermissions(2)) return;
        SlotManager sm = SlotManager.get();
        List<StreamerView> list = new ArrayList<>();
        for (SlotData.StreamerEntry e : sm.allStreamers()) {
            if (!e.granted && e.invited.isEmpty()) continue;
            list.add(new StreamerView(e.name, e.used(), sm.allowance(e), e.granted ? e.kind : "", new ArrayList<>(e.invited.values())));
        }
        // streamers first, then Season 1 and team, then entries without a place of their own
        list.sort(java.util.Comparator.comparingInt((StreamerView v) -> switch (v.kind()) {
            case "streamer" -> 0;
            case "season1" -> 1;
            case "team" -> 2;
            default -> 3;
        }).thenComparing(StreamerView::name, String.CASE_INSENSITIVE_ORDER));
        de.kronwerke.core.goal.GoalManager gm = de.kronwerke.core.goal.GoalManager.get();
        de.kronwerke.core.goal.GoalData d = gm.progressData();
        List<GoalRow> goals = new ArrayList<>();
        for (de.kronwerke.core.goal.Goal g : gm.allGoals()) {
            boolean done = d.isCompleted(g.id());
            boolean active = gm.isActive(g);
            String state = done ? "geschafft" : active ? (gm.isHeld(g) ? "wartet" : "läuft") : "gesperrt";
            if (active) state += String.format(", Faktor %.2f%s", d.factor(g.id()), d.isProvisional(g.id()) ? " (vorläufig)" : "");
            goals.add(new GoalRow(g.id(), g.title(), state, done ? 100 : active ? (int) Math.floor(gm.fraction(g) * 100) : 0));
        }
        List<PlayerRow> players = new ArrayList<>();
        for (ServerPlayer o : p.getServer().getPlayerList().getPlayers()) {
            String where = o.level().dimension().location().getPath().replace('_', ' ');
            SlotData.StreamerEntry own = sm.find(o.getUUID()), from = sm.inviterOf(o.getUUID());
            String place = own != null && own.granted ? switch (own.kind) {
                case "streamer" -> "Streamer";
                case "season1" -> "Season 1";
                default -> "Team";
            } : from != null ? "Platz von " + from.name : o.hasPermissions(2) ? "Team" : "von Hand";
            players.add(new PlayerRow(o.getGameProfile().getName(), where, d.hasBypass(o.getUUID()), o.isCreative(), place));
        }
        players.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
        de.kronwerke.core.obelisk.ObeliskData od = de.kronwerke.core.obelisk.Obelisk.get().data();
        String obelisk = od.isSet() ? od.pos().getX() + " " + od.pos().getY() + " " + od.pos().getZ()
                + (od.dimension().equals("minecraft:overworld") ? "" : " (" + od.dimension() + ")") : "";
        var tw = de.kronwerke.core.world.TestWorld.level(p.getServer());
        boolean built = tw != null && de.kronwerke.core.world.TestWorld.built(tw);
        de.kronwerke.core.season.Season season = de.kronwerke.core.season.Season.get();
        StringBuilder net = new StringBuilder();
        for (var peer : de.kronwerke.core.link.NetworkSync.peerList()) {
            if (net.length() > 0) net.append(", ");
            net.append(peer.label().isEmpty() ? peer.name() : peer.label()).append(peer.running() ? "" : " (aus)");
        }
        ServerView sv = new ServerView(season.gateOn(), season.beta(), String.join(", ", season.gateList()),
                de.kronwerke.core.queue.JoinQueue.on(), de.kronwerke.core.queue.JoinQueue.status(), de.kronwerke.core.queue.JoinQueue.limit(),
                de.kronwerke.core.lock.MachineLocks.on(),
                de.kronwerke.core.lock.MachineLocks.on() ? "Stufe " + de.kronwerke.core.lock.MachineLocks.appliedStage() + " offen, " + de.kronwerke.core.lock.MachineLocks.heldCount() + " Rezepte zurückgehalten" : "aus",
                gm.activePlayers() + " aktive Spieler (Fenster " + de.kronwerke.core.config.KronwerkeConfig.SCALE_DAYS.get() + " Tage)",
                net.length() == 0 ? "allein" : net.toString());
        PacketDistributor.sendToPlayer(p, new AdminPayload(tab, season.running(), season.number(), goals, list, players, obelisk, built, message, error, sv));
    }

    private static void onSlots(SlotsPayload payload, IPayloadContext ctx) {
        if (FMLEnvironment.dist.isClient()) {
            ctx.enqueueWork(() -> de.kronwerke.core.client.InviteScreen.receive(payload));
        }
    }

    private static void onAction(ActionPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer p)) return;
            if (!allowAction(p, false)) return; // a flood of menu packets: dropped
            if (!de.kronwerke.core.link.Role.main()) {
                p.sendSystemMessage(de.kronwerke.core.Text.t("network.main_only", "Das geht nur in der Hauptwelt.").withStyle(net.minecraft.ChatFormatting.GRAY));
                return;
            }
            SlotManager sm = SlotManager.get();
            String name = payload.name().trim();
            switch (payload.action()) {
                case "invite" -> {
                    SlotData.StreamerEntry me = sm.ownSlots(p.getUUID());
                    if (me == null || !(KronwerkeConfig.STREAMERS_MANAGE_OWN_SLOTS.get() || p.hasPermissions(2))) {
                        send(p, false, de.kronwerke.core.Text.t("slots.discord", "Einladen geht über Discord: /whitelist add im Kronwerke-Discord."), true);
                        return;
                    }
                    if (!allowAction(p, true)) {
                        send(p, false, de.kronwerke.core.Text.t("slots.slow", "Langsam, eins nach dem anderen."), true);
                        return;
                    }
                    // the name is resolved off the server thread; the menu then shows the account
                    // it belongs to (head and exact name) and invites only after a yes
                    sm.resolve(name, profile -> {
                        if (p.hasDisconnected()) return;
                        if (profile.isEmpty()) {
                            send(p, false, de.kronwerke.core.Text.t("slots.unknown", "Kein Minecraft-Konto mit dem Namen %s. Schreibweise prüfen.", name), true);
                            return;
                        }
                        confirming.put(p.getUUID(), profile.get());
                        send(p, false, de.kronwerke.core.Text.t("slots.confirm", "Ist das das richtige Konto? %s muss danach noch dem Kronwerke-Discord beitreten, sonst ist der Platz wieder weg.",
                                profile.get().getName()), false, profile.get());
                    });
                    return;
                }
                case "invite_confirm" -> {
                    SlotData.StreamerEntry me = sm.ownSlots(p.getUUID());
                    com.mojang.authlib.GameProfile profile = confirming.remove(p.getUUID());
                    if (me == null || profile == null || !profile.getId().toString().equals(name)) return;
                    SlotManager.Result res = sm.invite(me, profile, false);
                    send(p, false, switch (res) {
                        case OK -> de.kronwerke.core.Text.t("slots.invited_menu", "%s steht auf der Whitelist.", profile.getName());
                        case NO_SLOTS_LEFT -> de.kronwerke.core.Text.t("slots.full", "Du hast keinen freien Platz mehr.");
                        case ALREADY_INVITED -> de.kronwerke.core.Text.t("slots.has_place", "%s hat schon einen Platz.", profile.getName());
                        case ALREADY_WHITELISTED -> de.kronwerke.core.Text.t("slots.whitelisted", "%s ist schon auf der Whitelist.", profile.getName());
                        default -> de.kronwerke.core.Text.t("failed", "Das hat nicht geklappt.");
                    }, res != SlotManager.Result.OK);
                    return;
                }
                case "invite_cancel" -> {
                    confirming.remove(p.getUUID());
                    send(p, false, "", false);
                    return;
                }
                case "revoke" -> {
                    SlotData.StreamerEntry me = sm.ownSlots(p.getUUID());
                    if (me == null || !allowAction(p, true)) return;
                    java.util.UUID id = null;
                    for (var e : me.invited.entrySet()) if (e.getValue().equalsIgnoreCase(name)) id = e.getKey();
                    SlotManager.Result res = id == null ? SlotManager.Result.NOT_INVITED_BY_YOU : sm.revoke(me, new com.mojang.authlib.GameProfile(id, me.invited.get(id)));
                    send(p, false, switch (res) {
                        case OK -> de.kronwerke.core.Text.t("slots.revoked", "%s ist von der Whitelist runter, der Platz ist wieder frei.", name);
                        case NOT_INVITED_BY_YOU -> de.kronwerke.core.Text.t("slots.not_yours", "%s hat seinen Platz nicht von dir.", name);
                        default -> de.kronwerke.core.Text.t("failed", "Das hat nicht geklappt.");
                    }, res != SlotManager.Result.OK);
                    return;
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
                    sendHub(p, taken > 0 ? de.kronwerke.core.Text.t("deposit.done", "Abgegeben: %s. ", de.kronwerke.core.Text.number(taken)) : de.kronwerke.core.Text.t("deposit.none", "Nichts davon passt zum aktuellen Ziel."), taken <= 0);
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
                    if (e == null || !e.granted || parts.length < 2) { sendAdmin(p, parts[0] + " hat keinen eigenen Platz.", true); return; }
                    int n;
                    try { n = Integer.parseInt(parts[1].replace("+", "")); } catch (NumberFormatException ex) { sendAdmin(p, "Das ist keine Zahl.", true); return; }
                    // "+1" and "-1" change the base by one (the bonus stays on top), a bare number sets the base
                    if (parts[1].startsWith("+") || parts[1].startsWith("-")) sm.addSlots(e, n);
                    else sm.setSlots(e, n);
                    sendAdmin(p, e.name + " hat jetzt " + sm.allowance(e) + " Plätze" + (e.bonusSlots > 0 ? " (davon " + e.bonusSlots + " Bonus)" : "") + ".", false);
                    return;
                }
                case "admin_move" -> {
                    if (!p.hasPermissions(2)) return;
                    String[] parts = name.split("\\|");
                    SlotData.StreamerEntry to = parts.length < 2 ? null : sm.entryByName(parts[1]);
                    if (to == null) { sendAdmin(p, "Kein Streamer mit dem Namen " + (parts.length < 2 ? "" : parts[1]) + ".", true); return; }
                    java.util.UUID mid = null;
                    for (SlotData.StreamerEntry se : sm.allStreamers()) for (var en : se.invited.entrySet()) if (en.getValue().equalsIgnoreCase(parts[0])) mid = en.getKey();
                    SlotManager.Result res = mid == null ? SlotManager.Result.NOT_INVITED_BY_YOU : sm.move(new com.mojang.authlib.GameProfile(mid, parts[0]), to);
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
                    // the panel lists invited players by the name they were invited with: no lookup
                    java.util.UUID rid = null;
                    SlotData.StreamerEntry from = null;
                    for (SlotData.StreamerEntry se : sm.allStreamers()) for (var en : se.invited.entrySet()) if (en.getValue().equalsIgnoreCase(name)) { rid = en.getKey(); from = se; }
                    if (from == null) { sendAdmin(p, name + " hat von niemandem einen Platz.", true); return; }
                    SlotManager.Result res = sm.revoke(from, new com.mojang.authlib.GameProfile(rid, name));
                    sendAdmin(p, res == SlotManager.Result.OK ? name + " ist von der Whitelist runter." : "Das hat nicht geklappt.", res != SlotManager.Result.OK);
                    return;
                }
                default -> {
                }
            }
        });
    }

    private static final Map<UUID, long[]> lastAction = new java.util.HashMap<>();

    /**
     * The menu packets are cheap to send and some of them are not cheap to answer: one slot
     * action per second per player (invite and revoke), a few per second for the rest.
     */
    public static boolean allowAction(ServerPlayer p, boolean slot) {
        long now = System.currentTimeMillis();
        long[] t = lastAction.computeIfAbsent(p.getUUID(), k -> new long[2]);
        int i = slot ? 0 : 1;
        long gap = slot ? 1000 : 50;
        if (now - t[i] < gap) return false;
        t[i] = now;
        return true;
    }

    /** Accounts a streamer looked up and has not confirmed yet. */
    private static final Map<UUID, com.mojang.authlib.GameProfile> confirming = new java.util.HashMap<>();

    public static void send(ServerPlayer p, boolean open, String message, boolean error) {
        send(p, open, Component.literal(message), error, null);
    }

    public static void send(ServerPlayer p, boolean open, Component message, boolean error) {
        send(p, open, message, error, null);
    }

    /** Sends the streamer's slots to their client; open makes the client show the menu. */
    public static void send(ServerPlayer p, boolean open, Component message, boolean error, com.mojang.authlib.GameProfile confirm) {
        SlotManager sm = SlotManager.get();
        SlotData.StreamerEntry me = sm.ownSlots(p.getUUID());
        if (me == null) {
            if (open) p.sendSystemMessage(de.kronwerke.core.Text.t("slots.none", "Du hast keine eigenen Plätze. Plätze vergeben Streamer und Season-1-Spieler über Discord.")
                    .withStyle(net.minecraft.ChatFormatting.GRAY));
            return;
        }
        List<Entry> entries = new ArrayList<>();
        for (Map.Entry<UUID, String> e : me.invited.entrySet()) {
            boolean online = p.getServer().getPlayerList().getPlayer(e.getKey()) != null;
            entries.add(new Entry(e.getKey(), e.getValue(), online));
        }
        boolean canInvite = KronwerkeConfig.STREAMERS_MANAGE_OWN_SLOTS.get() || p.hasPermissions(2);
        List<Entry> c = confirm == null ? List.of() : List.of(new Entry(confirm.getId(), confirm.getName(), false));
        PacketDistributor.sendToPlayer(p, new SlotsPayload(me.used(), sm.allowance(me), entries, open, message, new SlotsExtra(error, canInvite, c)));
    }
}
