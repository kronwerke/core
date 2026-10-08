package de.kronwerke.core.share.soph;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import de.kronwerke.core.KronwerkeCore;
import de.kronwerke.core.Text;
import de.kronwerke.core.link.NetworkSync;
import de.kronwerke.core.link.Role;
import de.kronwerke.core.mixin.SophGroupsAccessor;
import de.kronwerke.core.mixin.SophManagerAccessor;
import de.kronwerke.core.share.soph.SophWrapperAccess;
import de.kronwerke.core.share.Layer;
import de.kronwerke.core.share.Share;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.p3pp3rf1y.sophisticatedcore.common.gui.StorageContainerMenuBase;
import net.p3pp3rf1y.sophisticatedcore.init.ModCoreDataComponents;
import net.p3pp3rf1y.sophisticatedcore.inventory.InventoryHandler;
import net.p3pp3rf1y.sophisticatedcore.linkedstorage.ILinkedStorageContents;
import net.p3pp3rf1y.sophisticatedcore.linkedstorage.ILinkedStorageVirtualHost;
import net.p3pp3rf1y.sophisticatedcore.linkedstorage.LinkedStorageEndpointData;
import net.p3pp3rf1y.sophisticatedcore.linkedstorage.LinkedStorageGroupManager;
import net.p3pp3rf1y.sophisticatedcore.linkedstorage.LinkedStorageGroupsSavedData;
import net.p3pp3rf1y.sophisticatedcore.linkedstorage.LinkedStorageHostDescriptor;
import net.p3pp3rf1y.sophisticatedcore.linkedstorage.LinkedStorageHostFactories;
import net.p3pp3rf1y.sophisticatedcore.upgrades.UpgradeHandler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Sophisticated's linked storage (backpacks and storage blocks joined with the Ender Linker)
 * between servers. A group's record, with everything in it, is on exactly one server at a time.
 * Every other server keeps a stand-in of the record that holds nothing and lets nothing in or out
 * (see {@link Gate}), so a backpack that is linked works everywhere in the sense that it never
 * fails, and it is usable where its group is.
 * <p>
 * The group moves when it is wanted somewhere else: a player opens it there (it comes as soon as
 * nobody has it open where it is), a player who carries it moves there (it comes along unless
 * someone else carries it where it was), or a player there carries it and it lies unused where it
 * is. A side world sends a group home to main when nobody there carries it and it has not been
 * used for a minute, and sends everything home before it stops. Main keeps the copy it last got
 * of every group on a side world, in case that world is reset without having sent it home.
 */
public final class LinkedLayer implements Layer {
    private static final long SEC = 1_000_000_000L;
    private static final long WAIT_OPEN = 60 * SEC, AUTO_IDLE = 120 * SEC, HOME_IDLE = 60 * SEC, MIRROR_EVERY = 10 * SEC;

    private static LinkedLayer instance;
    private static boolean listening;

    private MinecraftServer server;
    private LinkedData data;
    private long ticks, moved, restored;

    private record Meta(long rev, JsonObject json) {}

    private final Map<UUID, Meta> meta = new HashMap<>();
    private final Map<String, Map<UUID, JsonObject>> peerGroups = new HashMap<>();
    private final Set<String> freshPeers = new HashSet<>();
    private final Map<UUID, Long> lastRev = new HashMap<>(), lastUse = new HashMap<>();
    private final Map<UUID, Long> mirroredRev = new HashMap<>(), mirroredAt = new HashMap<>();
    private final Map<UUID, Long> askedAt = new HashMap<>();
    /** Players who tried to open a group that is elsewhere, and since when. */
    private final Map<UUID, Map<UUID, Long>> waiting = new HashMap<>();
    /** Groups that came along with a player, asked for once that player is here: group, player, since. */
    private final Map<UUID, Map.Entry<UUID, Long>> arriving = new HashMap<>();
    private final Set<UUID> warned = new HashSet<>();
    private Map<UUID, Set<UUID>> carried = Map.of();

    public static LinkedLayer create() {
        try {
            boolean gate = SophWrapperAccess.class.isAssignableFrom(InventoryHandler.class)
                    && SophWrapperAccess.class.isAssignableFrom(UpgradeHandler.class)
                    && SophGroupsAccessor.class.isAssignableFrom(LinkedStorageGroupsSavedData.class)
                    && SophManagerAccessor.class.isAssignableFrom(LinkedStorageGroupManager.class);
            boolean members = false;
            for (var m : Class.forName("net.p3pp3rf1y.sophisticatedcore.linkedstorage.LinkedStorageGroupRecord").getDeclaredMethods())
                if (m.getName().contains("kronwerke$")) members = true;
            if (!gate || !members) {
                KronwerkeCore.LOGGER.error("Shared network: Sophisticated's linked storage stays per server, Core's hooks into it did not apply (gate {}, members {})", gate, members);
                return null;
            }
        } catch (ClassNotFoundException | LinkageError e) {
            KronwerkeCore.LOGGER.error("Shared network: Sophisticated's linked storage stays per server: {}", e.toString());
            return null;
        }
        instance = new LinkedLayer();
        Gate.lenient = true;
        if (!listening) {
            listening = true;
            NeoForge.EVENT_BUS.addListener(LinkedLayer::onOpen);
        }
        return instance;
    }

    @Override
    public String name() {
        return "soph.linked";
    }

    // ---- Sophisticated's records ----

    private void ensure(MinecraftServer s) {
        if (server != s || data == null) {
            server = s;
            data = LinkedData.get(s);
        }
    }

    private HolderLookup.Provider reg() {
        return server.registryAccess();
    }

    private LinkedStorageGroupsSavedData sd() {
        return LinkedStorageGroupsSavedData.get(server.overworld());
    }

    @SuppressWarnings("unchecked")
    private static Map<UUID, Object> groupsOf(LinkedStorageGroupsSavedData d) {
        return ((SophGroupsAccessor) (Object) d).kronwerke$groups();
    }

    private Map<UUID, Object> groups() {
        return groupsOf(sd());
    }

    private boolean held(UUID g) {
        return groups().containsKey(g) && !data.away.containsKey(g);
    }

    /** One group's record as Sophisticated saves it, or null. */
    private CompoundTag tagOf(UUID g) {
        Object rec = groups().get(g);
        if (rec == null) return null;
        LinkedStorageGroupsSavedData tmp = SophGroupsAccessor.kronwerke$load(new CompoundTag(), reg());
        groupsOf(tmp).put(g, rec);
        ListTag l = tmp.save(new CompoundTag(), reg()).getList("groups", Tag.TAG_COMPOUND);
        return l.isEmpty() ? null : l.getCompound(0).copy();
    }

    /** A record made from its saved form; throws when it cannot be read, before anything changed. */
    private Object recordOf(CompoundTag t) {
        CompoundTag all = new CompoundTag();
        ListTag l = new ListTag();
        l.add(t.copy());
        all.put("groups", l);
        Object rec = groupsOf(SophGroupsAccessor.kronwerke$load(all, reg())).get(t.getUUID("id"));
        if (rec == null) throw new IllegalArgumentException("unreadable linked storage group");
        return rec;
    }

    private static CompoundTag parse(String snbt) {
        try {
            return TagParser.parseTag(snbt);
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            throw new IllegalArgumentException("unreadable linked storage group: " + e.getMessage(), e);
        }
    }

    /** What a record holds while its group is elsewhere: nothing, in the same layout. */
    private static CompoundTag emptyLike(CompoundTag contents) {
        CompoundTag t = new CompoundTag();
        for (String k : List.of("numberOfInventorySlots", "numberOfUpgradeSlots"))
            if (contents.contains(k)) t.put(k, contents.get(k).copy());
        return t;
    }

    /** What another server needs to keep a stand-in of a group held here. */
    private JsonObject metaOf(UUID g) {
        long rev = sd().manager().getRevision(g);
        Meta m = meta.get(g);
        if (m != null && m.rev() == rev) return m.json();
        CompoundTag t = tagOf(g);
        if (t == null) return null;
        JsonObject o = new JsonObject();
        o.addProperty("o", t.getUUID("owner_id").toString());
        o.addProperty("p", t.getUUID("primary_endpoint_id").toString());
        o.addProperty("f", t.getString("factory_id"));
        o.addProperty("c", t.getCompound("virtual_carrier").toString());
        o.addProperty("col", t.getInt("columns_taken"));
        meta.put(g, new Meta(rev, o));
        return o;
    }

    /** A stand-in for a group that is on another server: holds nothing, lets nothing in or out. */
    private void standIn(UUID g, JsonObject m, String holder) {
        String f = m.get("f").getAsString();
        ResourceLocation factory = ResourceLocation.tryParse(f);
        if (factory == null || LinkedStorageHostFactories.get(factory).isEmpty()) return;
        CompoundTag t = new CompoundTag();
        t.putUUID("id", g);
        t.putLong("revision", 0);
        t.putLong("render_revision", 0);
        t.putInt("columns_taken", m.get("col").getAsInt());
        t.putUUID("owner_id", UUID.fromString(m.get("o").getAsString()));
        UUID primary = UUID.fromString(m.get("p").getAsString());
        t.putUUID("primary_endpoint_id", primary);
        ListTag eps = new ListTag();
        CompoundTag pe = new CompoundTag();
        pe.putUUID("id", primary);
        pe.putLong("last_opened_at", -1);
        eps.add(pe);
        t.put("endpoints", eps);
        t.putString("factory_id", f);
        t.put("virtual_carrier", parse(m.get("c").getAsString()));
        t.put("contents", new CompoundTag());
        Object rec = recordOf(t);
        data.setAway(g, new LinkedData.Away(holder, nz(Share.epochOf(holder))));
        groups().put(g, rec);
        sd().setDirty();
    }

    // ---- moving a group ----

    /** Takes a group out of this server and gives it to another; it is held there from now on. */
    private boolean ship(UUID g, String peer, String why) {
        if (!held(g) || !Share.canGive(peer)) return false;
        CompoundTag t = tagOf(g);
        ILinkedStorageContents c = sd().manager().resolveContents(g).orElse(null);
        if (t == null || c == null) return false;
        JsonObject what = new JsonObject();
        what.addProperty("g", g.toString());
        what.addProperty("n", t.toString());
        c.setContents(emptyLike(t.getCompound("contents")));
        data.setAway(g, new LinkedData.Away(peer, nz(Share.epochOf(peer))));
        if (Role.main()) data.mirrors.put(g, new LinkedData.Mirror(peer, t.toString()));
        data.setDirty();
        if (!Share.give(this, peer, "g" + g, what)) {
            install(t, "back");
            return false;
        }
        moved++;
        KronwerkeCore.LOGGER.info("Shared network: linked storage {} goes to {} ({})", g, peer, why);
        return true;
    }

    /** Puts a group's whole record here, over the stand-in if there is one. */
    private void install(CompoundTag t, String how) {
        UUID g = t.getUUID("id");
        CompoundTag in = t.copy();
        if (groups().containsKey(g)) {
            CompoundTag mine = tagOf(g);
            // endpoints linked here while the group was elsewhere stay members
            ListTag eps = in.getList("endpoints", Tag.TAG_COMPOUND);
            Set<UUID> ids = new HashSet<>();
            for (Tag x : eps) ids.add(((CompoundTag) x).getUUID("id"));
            for (Tag x : mine.getList("endpoints", Tag.TAG_COMPOUND))
                if (ids.add(((CompoundTag) x).getUUID("id"))) eps.add(x.copy());
            in.put("endpoints", eps);
            // clients keep what they saw by revision: it only ever goes up
            in.putLong("revision", Math.max(in.getLong("revision"), mine.getLong("revision")) + 1);
            in.putLong("render_revision", Math.max(in.getLong("render_revision"), mine.getLong("render_revision")) + 1);
        }
        Object rec = recordOf(in);
        groups().put(g, rec);
        sd().setDirty();
        data.setAway(g, null);
        data.mirrors.remove(g);
        data.setDirty();
        meta.remove(g);
        lastUse.put(g, System.nanoTime());
        try {
            LinkedStorageGroupManager mgr = sd().manager();
            ILinkedStorageVirtualHost host = ((SophManagerAccessor) (Object) mgr).kronwerke$virtualHosts().get(g);
            if (host != null) host.onVirtualCarrierChanged(in.getCompound("virtual_carrier"));
            mgr.resolveContents(g).ifPresent(c -> {
                c.setContents(c.getContents());
                c.markRenderDirty();
            });
        } catch (RuntimeException e) {
            // the record is in place; what shows it catches up on its next change
            KronwerkeCore.LOGGER.warn("Shared network: linked storage {} is here, refreshing what shows it failed: {}", g, e.toString());
        }
        KronwerkeCore.LOGGER.info("Shared network: linked storage {} is here ({})", g, how);
        Map<UUID, Long> w = waiting.remove(g);
        if (w != null) for (UUID id : w.keySet()) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) p.displayClientMessage(Text.t("linked.here", "Dein verknüpfter Speicher ist jetzt hier, du kannst ihn öffnen.").withStyle(ChatFormatting.GREEN), true);
        }
    }

    // ---- who uses what ----

    private static Optional<UUID> menuGroup(ServerPlayer p) {
        if (p.containerMenu instanceof StorageContainerMenuBase<?> m) return Gate.groupOf(m.getStorageWrapper());
        return Optional.empty();
    }

    private boolean menuOpen(UUID g) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) if (menuGroup(p).filter(g::equals).isPresent()) return true;
        return false;
    }

    private static Set<UUID> carriedBy(ServerPlayer p) {
        Set<UUID> out = new HashSet<>();
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            if (st.isEmpty()) continue;
            LinkedStorageEndpointData e = st.get(ModCoreDataComponents.LINKED_STORAGE_ENDPOINT);
            if (e != null) out.add(e.groupId());
        }
        return out;
    }

    private Map<UUID, Set<UUID>> carried() {
        Map<UUID, Set<UUID>> out = new HashMap<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers())
            for (UUID g : carriedBy(p)) out.computeIfAbsent(g, x -> new HashSet<>()).add(p.getUUID());
        return out;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String mainPeer() {
        for (String p : Share.peers()) {
            NetworkSync.Peer q = NetworkSync.peer(p);
            if (q != null && q.role().equals("main")) return p;
        }
        return null;
    }

    private static Component label(String peer) {
        NetworkSync.Peer q = NetworkSync.peer(peer);
        return Component.literal(q == null ? peer : q.label());
    }

    // ---- asking for a group ----

    /** A player opens a linked storage whose group is elsewhere: closed again, and the group is asked for. */
    private static void onOpen(PlayerContainerEvent.Open e) {
        LinkedLayer l = instance;
        if (l == null || l.server == null || !(e.getEntity() instanceof ServerPlayer p)) return;
        Optional<UUID> g = menuGroup(p);
        if (g.isEmpty()) return;
        if (!Gate.away(g.get())) {
            l.lastUse.put(g.get(), System.nanoTime());
            return;
        }
        p.closeContainer();
        l.want(g.get(), "open", p);
    }

    private void want(UUID g, String mode, ServerPlayer p) {
        LinkedData.Away a = data.away.get(g);
        if (a == null) return;
        boolean up = Share.peers().contains(a.peer());
        if (p != null) {
            if (!up) {
                p.displayClientMessage(Text.t("linked.offline", "Dieser Speicher ist gerade in %s, und dorthin gibt es gerade keine Verbindung.", label(a.peer())).withStyle(ChatFormatting.RED), true);
                return;
            }
            waiting.computeIfAbsent(g, x -> new HashMap<>()).put(p.getUUID(), System.nanoTime());
            p.displayClientMessage(Text.t("linked.fetching", "Dieser Speicher ist gerade in %s. Er wird hergeholt, gleich kannst du ihn öffnen.", label(a.peer())).withStyle(ChatFormatting.YELLOW), true);
        }
        if (!up) return;
        long now = System.nanoTime(), every = mode.equals("auto") ? 10 * SEC : 2 * SEC;
        Long at = askedAt.get(g);
        if (at != null && now - at < every) return;
        askedAt.put(g, now);
        JsonObject w = new JsonObject();
        w.addProperty("k", "want");
        w.addProperty("g", g.toString());
        w.addProperty("mode", mode);
        Share.tell(this, a.peer(), w);
    }

    @Override
    public void onTell(MinecraftServer s, String from, JsonObject d) {
        ensure(s);
        String k = d.has("k") ? d.get("k").getAsString() : "";
        UUID g = UUID.fromString(d.get("g").getAsString());
        switch (k) {
            case "want" -> {
                String mode = d.get("mode").getAsString();
                JsonObject r = new JsonObject();
                r.addProperty("g", g.toString());
                if (!held(g)) {
                    LinkedData.Away a = data.away.get(g);
                    r.addProperty("k", "no");
                    r.addProperty("at", a == null ? "" : a.peer());
                    Share.tell(this, from, r);
                    return;
                }
                boolean idle = System.nanoTime() - lastUse.getOrDefault(g, 0L) >= AUTO_IDLE;
                // who carries it here now: a player who just left is not here any more
                carried = carried();
                boolean carriedHere = carried.containsKey(g);
                boolean stay = menuOpen(g)
                        || (mode.equals("arrive") && carriedHere)
                        || (mode.equals("auto") && (carriedHere || !idle));
                if (stay || !ship(g, from, mode)) {
                    r.addProperty("k", "busy");
                    r.addProperty("mode", mode);
                    Share.tell(this, from, r);
                }
            }
            case "busy" -> {
                if (!"open".equals(d.has("mode") ? d.get("mode").getAsString() : "")) return;
                LinkedData.Away a = data.away.get(g);
                Map<UUID, Long> w = waiting.get(g);
                if (a == null || w == null) return;
                for (UUID id : w.keySet()) {
                    ServerPlayer p = server.getPlayerList().getPlayer(id);
                    if (p != null) p.displayClientMessage(Text.t("linked.busy", "Dieser Speicher ist gerade in %s offen. Sobald er dort zu ist, kommt er her.", label(a.peer())).withStyle(ChatFormatting.YELLOW), true);
                }
            }
            case "no" -> {
                String at = d.get("at").getAsString();
                LinkedData.Away a = data.away.get(g);
                if (a != null && !at.isEmpty() && !at.equals(Role.server()) && !at.equals(a.peer()))
                    data.setAway(g, new LinkedData.Away(at, nz(Share.epochOf(at))));
            }
            case "mirror" -> {
                if (!Role.main() || held(g)) return;
                data.mirrors.put(g, new LinkedData.Mirror(from, d.get("n").getAsString()));
                data.setDirty();
            }
            default -> {
                // a newer Core
            }
        }
    }

    // ---- the tick ----

    @Override
    public void tick(MinecraftServer s) {
        ensure(s);
        ticks++;
        if (!freshPeers.isEmpty()) {
            for (String peer : freshPeers) learn(peer);
            freshPeers.clear();
        }
        if (ticks % 40 != 0) return;
        long now = System.nanoTime();
        carried = carried();
        LinkedStorageGroupManager mgr = sd().manager();

        JsonObject state = new JsonObject();
        for (UUID g : new ArrayList<>(groups().keySet())) {
            if (data.away.containsKey(g)) continue;
            long rev = mgr.getRevision(g);
            Long before = lastRev.put(g, rev);
            if (before == null || before != rev) lastUse.put(g, now);
            if (menuOpen(g)) lastUse.put(g, now);
            JsonObject m = metaOf(g);
            if (m != null) state.add(g.toString(), m);
        }
        Share.state(this, state);

        // carried here, held elsewhere and lying unused there: ask for it
        for (UUID g : carried.keySet()) if (data.away.containsKey(g)) want(g, "auto", null);
        arriving.entrySet().removeIf(e -> {
            if (now - e.getValue().getValue() > 90 * SEC || !data.away.containsKey(e.getKey())) return true;
            if (server.getPlayerList().getPlayer(e.getValue().getKey()) == null) return false;
            askedAt.remove(e.getKey());
            want(e.getKey(), "arrive", null);
            return true;
        });
        // players who tried to open it: keep asking for a while
        waiting.entrySet().removeIf(e -> {
            e.getValue().values().removeIf(t -> now - t > WAIT_OPEN);
            return e.getValue().isEmpty() || !data.away.containsKey(e.getKey());
        });
        for (UUID g : waiting.keySet()) want(g, "open", null);

        if (!Role.main()) {
            String home = mainPeer();
            if (home != null) for (UUID g : new ArrayList<>(groups().keySet())) {
                if (!held(g)) continue;
                // nobody here carries it and it lies unused: home
                if (!carried.containsKey(g) && !menuOpen(g) && now - lastUse.getOrDefault(g, now) >= HOME_IDLE && ship(g, home, "home")) continue;
                long rev = mgr.getRevision(g);
                Long mr = mirroredRev.get(g);
                if ((mr == null || mr != rev) && now - mirroredAt.getOrDefault(g, 0L) >= MIRROR_EVERY) {
                    CompoundTag t = tagOf(g);
                    if (t == null) continue;
                    JsonObject w = new JsonObject();
                    w.addProperty("k", "mirror");
                    w.addProperty("g", g.toString());
                    w.addProperty("n", t.toString());
                    Share.tell(this, home, w);
                    mirroredRev.put(g, rev);
                    mirroredAt.put(g, now);
                }
            }
        } else {
            restore();
        }
    }

    /** main: a group that was on a world which is gone (reset without sending it home) comes back from its last copy. */
    private void restore() {
        for (Map.Entry<UUID, LinkedData.Away> e : new ArrayList<>(data.away.entrySet())) {
            UUID g = e.getKey();
            LinkedData.Away a = e.getValue();
            String cur = Share.epochOf(a.peer());
            if (cur == null) continue;
            if (a.epoch().isEmpty()) {
                data.setAway(g, new LinkedData.Away(a.peer(), cur));
                continue;
            }
            if (cur.equals(a.epoch())) continue;
            LinkedData.Mirror m = data.mirrors.get(g);
            if (m != null) {
                install(parse(m.group()), "restored, " + a.peer() + " has a new world");
                restored++;
                KronwerkeCore.LOGGER.warn("Shared network: linked storage {} was on {}'s earlier world, restored from its last copy", g, a.peer());
            } else {
                data.setAway(g, null);
                KronwerkeCore.LOGGER.error("Shared network: linked storage {} was on {}'s earlier world and no copy of it is here; it is empty now", g, a.peer());
            }
        }
    }

    // ---- what the other servers say ----

    @Override
    public void onState(String from, JsonObject state) {
        Map<UUID, JsonObject> m = new HashMap<>();
        for (Map.Entry<String, JsonElement> e : state.entrySet()) {
            try {
                m.put(UUID.fromString(e.getKey()), e.getValue().getAsJsonObject());
            } catch (IllegalArgumentException | IllegalStateException x) {
                // not a group
            }
        }
        peerGroups.put(from, m);
        // at once: a give that came after this state must find it already learned
        if (server != null && data != null) learn(from);
        else freshPeers.add(from);
    }

    /** Stand-ins for what another server holds; where a group is now. */
    private void learn(String peer) {
        Map<UUID, JsonObject> theirs = peerGroups.get(peer);
        if (theirs == null) return;
        LinkedStorageGroupManager mgr = sd().manager();
        for (Map.Entry<UUID, JsonObject> e : theirs.entrySet()) {
            UUID g = e.getKey();
            JsonObject m = e.getValue();
            try {
                if (!groups().containsKey(g)) {
                    standIn(g, m, peer);
                    continue;
                }
                LinkedData.Away a = data.away.get(g);
                if (a == null) {
                    if (warned.add(g)) KronwerkeCore.LOGGER.warn("Shared network: linked storage {} is held here and on {}", g, peer);
                    continue;
                }
                if (!a.peer().equals(peer)) data.setAway(g, new LinkedData.Away(peer, nz(Share.epochOf(peer))));
                // how it looks follows the holder
                CompoundTag carrier = parse(m.get("c").getAsString());
                LinkedStorageHostDescriptor now = mgr.getHostDescriptor(g).orElse(null);
                if (now != null && !now.virtualCarrier().equals(carrier))
                    mgr.updatePrimaryHostDescriptor(g, UUID.fromString(m.get("p").getAsString()), new LinkedStorageHostDescriptor(now.factoryId(), carrier));
            } catch (RuntimeException x) {
                if (warned.add(g)) KronwerkeCore.LOGGER.warn("Shared network: linked storage {} from {}: {}", g, peer, x.toString());
            }
        }
    }

    @Override
    public JsonObject receive(MinecraftServer s, String from, JsonObject d) {
        ensure(s);
        UUID g = UUID.fromString(d.get("g").getAsString());
        CompoundTag t = parse(d.get("n").getAsString());
        if (held(g)) {
            // never over what is here: it goes back
            KronwerkeCore.LOGGER.warn("Shared network: linked storage {} came from {} but is held here, it goes back", g, from);
            return d;
        }
        install(t, "from " + from);
        return null;
    }

    @Override
    public JsonObject refund(MinecraftServer s, JsonObject d) {
        ensure(s);
        UUID g = UUID.fromString(d.get("g").getAsString());
        CompoundTag t = parse(d.get("n").getAsString());
        if (held(g)) {
            KronwerkeCore.LOGGER.warn("Shared network: linked storage {} came back but is held here already, the one here stays", g);
            return null;
        }
        install(t, "came back");
        return null;
    }

    // ---- along with a player ----

    @Override
    public JsonObject pack(ServerPlayer p) {
        ensure(p.server);
        JsonObject out = new JsonObject();
        for (UUID g : carriedBy(p)) {
            if (!groups().containsKey(g)) continue;
            JsonObject m;
            String holder;
            if (held(g)) {
                m = metaOf(g);
                holder = Role.server();
            } else {
                m = theirMeta(g);
                holder = data.away.get(g).peer();
            }
            if (m == null) continue;
            JsonObject o = m.deepCopy();
            o.addProperty("h", holder);
            out.add(g.toString(), o);
        }
        return out.size() == 0 ? null : out;
    }

    private JsonObject theirMeta(UUID g) {
        for (Map<UUID, JsonObject> m : peerGroups.values()) if (m.containsKey(g)) return m.get(g);
        return null;
    }

    @Override
    public void unpack(MinecraftServer s, UUID player, JsonObject d) {
        ensure(s);
        for (Map.Entry<String, JsonElement> e : d.entrySet()) {
            try {
                UUID g = UUID.fromString(e.getKey());
                JsonObject m = e.getValue().getAsJsonObject();
                String holder = m.get("h").getAsString();
                if (holder.equals(Role.server())) continue;
                if (!groups().containsKey(g)) standIn(g, m, holder);
                if (data.away.containsKey(g)) arriving.put(g, Map.entry(player, System.nanoTime()));
            } catch (RuntimeException x) {
                KronwerkeCore.LOGGER.warn("Shared network: a linked storage that came along with {}: {}", player, x.toString());
            }
        }
    }

    @Override
    public void onStopping(MinecraftServer s) {
        ensure(s);
        Gate.lenient = false;
        if (Role.main()) return;
        String home = mainPeer();
        if (home == null) return;
        for (ServerPlayer p : s.getPlayerList().getPlayers()) if (menuGroup(p).isPresent()) p.closeContainer();
        for (UUID g : new ArrayList<>(groups().keySet())) if (held(g)) ship(g, home, "stopping");
    }

    @Override
    public String describe() {
        if (data == null) return "not started";
        int here = 0;
        for (UUID g : groups().keySet()) if (!data.away.containsKey(g)) here++;
        String s = here + " groups here, " + data.away.size() + " on other servers, " + moved + " sent since start";
        if (Role.main()) s += ", " + data.mirrors.size() + " copies of side world groups, " + restored + " restored";
        return s;
    }
}
