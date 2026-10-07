package de.kronwerke.core.portal;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import de.kronwerke.core.KronwerkeCore;
import de.kronwerke.core.Text;
import de.kronwerke.core.link.NetworkSync;
import de.kronwerke.core.link.Role;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.common.ClientboundTransferPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Moving players (and thrown items) between the servers of the network through the portal.
 * <p>
 * A player who stands in the portal for a moment is saved here and sent to the target as a
 * whole (the player file: inventory, ender chest, effects, every mod's attachments), the target
 * writes it as its own player file and says ready, and only then the client is told to connect
 * there (Minecraft's transfer packet). A side world only lets in players it expects this way.
 * <p>
 * main remembers who is away and where they left (in front of their portal). A side world sends
 * the player back home when they go through its portal, when they log out there, and every 30
 * seconds as a checkpoint, so main always has the newest state if the side world goes down.
 */
public final class Travel {
    private static final int CHARGE_TICKS = 50;

    private static MinecraftServer server;
    private static long tick;
    /** Players in a portal: since which tick, last tick seen. */
    private static final Map<UUID, long[]> inPortal = new HashMap<>();
    /** Departures waiting for the target's ready. */
    private static final Map<String, Departure> pending = new HashMap<>();
    /** Arrivals this server expects, by player, with the server they come from. */
    private static final Map<UUID, Arrival> expected = new HashMap<>();
    /** Who arrived lately (no portal for a few seconds) and from where, for the join message. */
    private static final Map<UUID, Arrival> arrived = new HashMap<>();
    /** Who is leaving through a move, and to where, for the leave message. */
    private static final Map<UUID, String> leaving = new HashMap<>();
    /** Item sends waiting for delivery, to drop them again if nobody took them. */
    private static final Map<String, ItemSend> itemSends = new HashMap<>();
    private static final Set<UUID> evacuating = new HashSet<>();
    private static long evacuateUntil;

    record Departure(UUID player, String target, long until, JsonObject back) {}

    record Arrival(String from, long until) {}

    record ItemSend(ResourceKey<Level> dim, double x, double y, double z, List<ItemStack> stacks, long until) {}

    private Travel() {
    }

    public static void init(MinecraftServer s) {
        server = s;
        pending.clear();
        expected.clear();
        if (!Role.main()) Hub.ensure(s);
    }

    // ---- what others ask ----

    public static boolean expected(UUID id) {
        Arrival a = expected.get(id);
        return a != null && a.until() > System.currentTimeMillis();
    }

    public static String arrivedFrom(UUID id) {
        Arrival a = arrived.get(id);
        return a == null ? null : a.from();
    }

    public static String leavingTo(UUID id) {
        return leaving.get(id);
    }

    // ---- the portal ----

    /** Every tick an entity touches the portal. */
    static void inside(Entity e, Level level, BlockPos pos) {
        if (e instanceof ServerPlayer p) {
            insidePlayer(p, pos);
        } else if (e instanceof ItemEntity item && item.isAlive()) {
            sendItem(item);
        }
    }

    private static void insidePlayer(ServerPlayer p, BlockPos pos) {
        UUID id = p.getUUID();
        Arrival a = arrived.get(id);
        if (a != null && a.until() > System.currentTimeMillis()) return;
        if (p.isPassenger() || p.isSpectator() || leaving.containsKey(id)) return;
        for (Departure d : pending.values()) if (d.player().equals(id)) return;
        long[] t = inPortal.get(id);
        if (t == null) {
            NetworkSync.Peer target = NetworkSync.portalTarget();
            if (target == null || !target.bus() || !target.running() || target.host().isEmpty()) {
                p.displayClientMessage(Text.t("portal.closed", "Das Portal flackert: die andere Welt ist gerade nicht erreichbar.")
                        .withStyle(ChatFormatting.GRAY), true);
                inPortal.put(id, new long[] {tick + 100, tick + 100, pos.asLong()}); // quiet for five seconds
                return;
            }
            inPortal.put(id, new long[] {tick, tick, pos.asLong()});
            send(p, TravelPayload.CHARGE, target);
            p.level().playSound(null, p.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.8f, 0.6f);
        }
    }

    /** Whether any block the player stands in is the portal (checked every tick, as players only report moves now and then). */
    private static boolean inPortal(ServerPlayer p) {
        var box = p.getBoundingBox().deflate(0.05);
        for (BlockPos b : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ), BlockPos.containing(box.maxX, box.maxY, box.maxZ))) {
            if (p.level().getBlockState(b).is(PortalBlocks.PORTAL.get())) return true;
        }
        return false;
    }

    /** Every tick: who is still in the portal charges on, who stepped out stops, who is done goes. */
    private static void charge() {
        for (var it = inPortal.entrySet().iterator(); it.hasNext(); ) {
            var en = it.next();
            long[] t = en.getValue();
            if (t[0] > tick) {
                if (t[1] < tick) it.remove(); // the quiet time after a failure is over
                continue;
            }
            ServerPlayer p = server.getPlayerList().getPlayer(en.getKey());
            if (p == null) {
                it.remove();
                continue;
            }
            if (!inPortal(p)) {
                send(p, TravelPayload.CANCEL, null);
                it.remove();
                continue;
            }
            int need = p.isCreative() ? 10 : CHARGE_TICKS;
            if (tick - t[0] >= need) {
                it.remove();
                NetworkSync.Peer target = NetworkSync.portalTarget();
                if (target != null) depart(p, target, back(p, BlockPos.of(t[2])));
            }
        }
    }

    /**
     * Sends a player to the portal's other side without the portal (/kw move): from main to the
     * mining world, coming back where they stood; from a side world home. False when there is no
     * such server on the bus.
     */
    public static boolean move(ServerPlayer p) {
        NetworkSync.Peer target = NetworkSync.portalTarget();
        if (target == null || !target.bus() || !target.running()) return false;
        JsonObject b = null;
        if (Role.main()) {
            b = new JsonObject();
            b.addProperty("dim", p.level().dimension().location().toString());
            b.addProperty("x", p.getX());
            b.addProperty("y", p.getY());
            b.addProperty("z", p.getZ());
            b.addProperty("yaw", p.getYRot());
        }
        depart(p, target, b);
        return true;
    }

    /** Where a player who went through the portal at pos stands when they come back: in front of it. */
    private static JsonObject back(ServerPlayer p, BlockPos pos) {
        if (!Role.main()) return null;
        Direction.Axis axis = p.level().getBlockState(pos).hasProperty(MinePortalBlock.AXIS)
                ? p.level().getBlockState(pos).getValue(MinePortalBlock.AXIS) : Direction.Axis.X;
        double x = p.getX(), z = p.getZ();
        float yaw;
        if (axis == Direction.Axis.X) {
            boolean north = p.getZ() < pos.getZ() + 0.5;
            z = pos.getZ() + 0.5 + (north ? -1.4 : 1.4);
            yaw = north ? 180 : 0;
        } else {
            boolean west = p.getX() < pos.getX() + 0.5;
            x = pos.getX() + 0.5 + (west ? -1.4 : 1.4);
            yaw = west ? 90 : -90;
        }
        JsonObject b = new JsonObject();
        b.addProperty("dim", p.level().dimension().location().toString());
        b.addProperty("x", x);
        b.addProperty("y", p.getY());
        b.addProperty("z", z);
        b.addProperty("yaw", yaw);
        return b;
    }

    public static void onTick(ServerTickEvent.Post e) {
        tick++;
        if (server == null) return;
        long now = System.currentTimeMillis();
        charge();
        for (var it = pending.entrySet().iterator(); it.hasNext(); ) {
            var en = it.next();
            if (en.getValue().until() < now) {
                it.remove();
                ServerPlayer p = server.getPlayerList().getPlayer(en.getValue().player());
                if (p != null) {
                    send(p, TravelPayload.CANCEL, null);
                    p.displayClientMessage(Text.t("portal.failed", "Die Reise hat nicht geklappt, versuch es gleich noch einmal.").withStyle(ChatFormatting.RED), false);
                }
            }
        }
        expected.values().removeIf(a -> a.until() < now);
        arrived.values().removeIf(a -> a.until() < now);
        for (var it = itemSends.entrySet().iterator(); it.hasNext(); ) {
            var en = it.next();
            if (en.getValue().until() < now) {
                // nobody took them: they fall out of the portal again
                drop(en.getValue());
                it.remove();
            }
        }
        if (!Role.main() && tick % 600 == 0) checkpoints();
        if (!Role.main() && tick % 100 == 0) tiefgraeber();
        if (!evacuating.isEmpty() || evacuateUntil > 0) {
            evacuating.removeIf(id -> server.getPlayerList().getPlayer(id) == null);
            if (evacuating.isEmpty() || now > evacuateUntil) {
                evacuating.clear();
                evacuateUntil = 0;
                NetworkSync.evacuated();
            }
        }
    }

    // ---- leaving ----

    /** Saves the player and hands them to the target; the move happens when the target is ready. */
    public static void depart(ServerPlayer p, NetworkSync.Peer target, JsonObject back) {
        p.stopRiding();
        CompoundTag tag = p.saveWithoutId(new CompoundTag());
        JsonObject data = new JsonObject();
        data.addProperty("uuid", p.getUUID().toString());
        data.addProperty("name", p.getGameProfile().getName());
        data.addProperty("nbt", encode(tag));
        if (Role.main()) {
            // the quest book's task; before the advancements are packed, so it travels too
            var adv = server.getAdvancements().get(ResourceLocation.fromNamespaceAndPath("kronwerke", "minenwelt"));
            if (adv != null) p.getAdvancements().award(adv, "travel");
        }
        progress(p, data);
        if (back != null) data.add("back", back);
        JsonObject extra = de.kronwerke.core.share.Share.pack(p, Compat.collect(p));
        if (extra != null) data.add("extra", extra);
        String id = UUID.randomUUID().toString();
        pending.put(id, new Departure(p.getUUID(), target.name(), System.currentTimeMillis() + 12_000, back));
        NetworkSync.send(target.name(), "player.handoff", data, id);
        send(p, TravelPayload.GO, target);
    }

    private static void ready(String from, JsonObject d) {
        Departure dep = pending.remove(str(d, "id"));
        if (dep == null) return;
        ServerPlayer p = server.getPlayerList().getPlayer(dep.player());
        NetworkSync.Peer target = NetworkSync.peer(dep.target());
        if (p == null || target == null) return;
        if (Role.main()) {
            AwayData.get(server).leave(p.getUUID(), dep.target(), dep.back());
            // main saves the player as they log out: in front of the portal, not in it
            JsonObject b = dep.back();
            if (b != null) p.teleportTo(p.serverLevel(), b.get("x").getAsDouble(), b.get("y").getAsDouble(), b.get("z").getAsDouble(), b.get("yaw").getAsFloat(), 0);
        }
        leaving.put(p.getUUID(), dep.target());
        KronwerkeCore.LOGGER.info("{} moves to {} ({}:{})", p.getGameProfile().getName(), dep.target(), target.host(), target.port());
        p.connection.send(new ClientboundTransferPacket(target.host(), target.port()));
    }

    /** A side world: everyone goes home (before the world is reset). */
    public static void evacuate() {
        if (server == null) return;
        server.execute(() -> {
            NetworkSync.Peer home = NetworkSync.portalTarget();
            evacuateUntil = System.currentTimeMillis() + 30_000;
            for (ServerPlayer p : new ArrayList<>(server.getPlayerList().getPlayers())) {
                evacuating.add(p.getUUID());
                p.sendSystemMessage(Text.t("portal.evacuate", "Diese Welt wird zurückgesetzt. Du reist jetzt nach Hause.").withStyle(ChatFormatting.GOLD));
                if (home != null && home.bus()) depart(p, home, null);
                else p.connection.disconnect(Text.t("portal.reset", "Diese Welt wird zurückgesetzt."));
            }
        });
    }

    /** A side world: the player logs out here (not by a move), so main gets the newest state. */
    public static void onLogout(ServerPlayer p) {
        UUID id = p.getUUID();
        inPortal.remove(id);
        if (leaving.remove(id) != null) return;
        if (Role.main()) return;
        NetworkSync.Peer home = NetworkSync.portalTarget();
        if (home == null) return;
        JsonObject data = new JsonObject();
        data.addProperty("uuid", id.toString());
        data.addProperty("nbt", encode(p.saveWithoutId(new CompoundTag())));
        progress(p, data);
        JsonObject extra = de.kronwerke.core.share.Share.pack(p, Compat.collect(p));
        if (extra != null) data.add("extra", extra);
        NetworkSync.send(home.name(), "player.home", data, null);
    }

    /** A side world stops: everyone's state goes home now, while the bus is still there. */
    public static void onStopping() {
        if (server == null || Role.main()) return;
        for (ServerPlayer p : new ArrayList<>(server.getPlayerList().getPlayers())) {
            onLogout(p);
            leaving.put(p.getUUID(), "stop"); // their logout a moment later is not sent again
        }
    }

    private static void checkpoints() {
        NetworkSync.Peer home = NetworkSync.portalTarget();
        if (home == null || !home.bus()) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (leaving.containsKey(p.getUUID())) continue;
            JsonObject data = new JsonObject();
            data.addProperty("uuid", p.getUUID().toString());
            data.addProperty("nbt", encode(p.saveWithoutId(new CompoundTag())));
            progress(p, data);
            NetworkSync.send(home.name(), "player.checkpoint", data, null);
        }
    }

    // ---- arriving ----

    public static void onMessage(String from, String topic, JsonObject d) {
        if (server == null) return;
        server.execute(() -> {
            try {
                switch (topic) {
                    case "player.handoff" -> handoff(from, d);
                    case "player.ready" -> ready(from, d);
                    case "player.home", "player.checkpoint" -> home(from, d, topic.equals("player.home"));
                    case "player.claimed" -> expected.remove(UUID.fromString(str(d, "uuid")));
                    case "kw.items" -> receiveItems(from, d);
                    default -> {
                        // a newer Core's topic
                    }
                }
            } catch (RuntimeException | IOException e) {
                KronwerkeCore.LOGGER.warn("{} from {}: {}", topic, from, e.toString());
            }
        });
    }

    private static void handoff(String from, JsonObject d) throws IOException {
        UUID id = UUID.fromString(str(d, "uuid"));
        if (server.getPlayerList().getPlayer(id) != null) return; // here already: the old move is stale
        CompoundTag tag = decode(str(d, "nbt"));
        if (Role.main()) {
            AwayData.Back b = AwayData.get(server).back(id);
            place(tag, b == null ? spawn(server) : b);
            keepFromHere(id, tag);
            AwayData.get(server).home(id);
        } else {
            place(tag, Hub.arrival(server));
        }
        writePlayer(id, tag);
        writeProgress(id, d);
        if (d.get("extra") instanceof JsonObject x) {
            Compat.restore(server, x);
            de.kronwerke.core.share.Share.unpack(id, x);
        }
        expected.put(id, new Arrival(from, System.currentTimeMillis() + 90_000));
        NetworkSync.send(from, "player.ready", withId(d), null);
    }

    /** main: a player left a side world without a move, or a side world's checkpoint. */
    private static void home(String from, JsonObject d, boolean final_) throws IOException {
        if (!Role.main()) return;
        UUID id = UUID.fromString(str(d, "uuid"));
        if (server.getPlayerList().getPlayer(id) != null) return; // already back here: theirs wins
        AwayData away = AwayData.get(server);
        AwayData.Back b = away.back(id);
        if (b == null && !final_) return; // not away: an old checkpoint
        CompoundTag tag = decode(str(d, "nbt"));
        place(tag, b == null ? spawn(server) : b);
        keepFromHere(id, tag);
        writePlayer(id, tag);
        writeProgress(id, d);
        if (d.get("extra") instanceof JsonObject x) {
            Compat.restore(server, x);
            de.kronwerke.core.share.Share.unpack(id, x);
        }
        if (final_) away.home(id);
    }

    /**
     * main: parts of the player file that only make sense here and stay as main knows them
     * (network.keepOnMain, by tag name wherever it sits): the waystones a player activated
     * point at main's world, and a side world must not change them.
     */
    private static void keepFromHere(UUID id, CompoundTag incoming) {
        Path f = server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(id + ".dat");
        if (!Files.exists(f)) return;
        try {
            CompoundTag old = NbtIo.readCompressed(f, NbtAccounter.unlimitedHeap());
            for (String key : de.kronwerke.core.config.KronwerkeConfig.KEEP_ON_MAIN.get()) {
                List<String> path = new ArrayList<>();
                Tag t = findKey(old, key, path);
                if (t != null) putAt(incoming, path, t.copy());
            }
        } catch (IOException | RuntimeException e) {
            KronwerkeCore.LOGGER.warn("Could not keep main's part of {}: {}", id, e.toString());
        }
    }

    private static Tag findKey(CompoundTag c, String key, List<String> path) {
        if (c.contains(key)) {
            path.add(key);
            return c.get(key);
        }
        for (String k : c.getAllKeys()) {
            if (c.get(k) instanceof CompoundTag inner) {
                path.add(k);
                Tag t = findKey(inner, key, path);
                if (t != null) return t;
                path.remove(path.size() - 1);
            }
        }
        return null;
    }

    private static void putAt(CompoundTag c, List<String> path, Tag value) {
        CompoundTag at = c;
        for (int i = 0; i < path.size() - 1; i++) {
            CompoundTag next = at.get(path.get(i)) instanceof CompoundTag n ? n : new CompoundTag();
            at.put(path.get(i), next);
            at = next;
        }
        at.put(path.get(path.size() - 1), value);
    }

    /** Sets where a player file puts its player: dimension, position, facing, no speed. */
    private static void place(CompoundTag tag, AwayData.Back b) {
        tag.putString("Dimension", b.dim());
        ListTag pos = new ListTag();
        pos.add(DoubleTag.valueOf(b.x()));
        pos.add(DoubleTag.valueOf(b.y()));
        pos.add(DoubleTag.valueOf(b.z()));
        tag.put("Pos", pos);
        ListTag motion = new ListTag();
        for (int i = 0; i < 3; i++) motion.add(DoubleTag.valueOf(0));
        tag.put("Motion", motion);
        ListTag rot = new ListTag();
        rot.add(FloatTag.valueOf(b.yaw()));
        rot.add(FloatTag.valueOf(0));
        tag.put("Rotation", rot);
        tag.putFloat("FallDistance", 0);
        tag.remove("RootVehicle");
        tag.remove("enteredNetherPosition");
    }

    static AwayData.Back spawn(MinecraftServer s) {
        ServerLevel o = s.overworld();
        BlockPos sp = o.getSharedSpawnPos();
        int y = o.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, sp.getX(), sp.getZ());
        return new AwayData.Back(Level.OVERWORLD.location().toString(), sp.getX() + 0.5, Math.max(y, sp.getY()), sp.getZ() + 0.5, 0);
    }

    private static void writePlayer(UUID id, CompoundTag tag) throws IOException {
        Path dir = server.getWorldPath(LevelResource.PLAYER_DATA_DIR);
        Files.createDirectories(dir);
        Path tmp = dir.resolve(id + ".kwtmp");
        NbtIo.writeCompressed(tag, tmp);
        Files.move(tmp, dir.resolve(id + ".dat"), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    /** Advancements and statistics live in files of their own; they travel too. */
    private static void progress(ServerPlayer p, JsonObject data) {
        try {
            p.getAdvancements().save();
            p.getStats().save();
            Path adv = server.getWorldPath(LevelResource.PLAYER_ADVANCEMENTS_DIR).resolve(p.getUUID() + ".json");
            Path stats = server.getWorldPath(LevelResource.PLAYER_STATS_DIR).resolve(p.getUUID() + ".json");
            if (Files.exists(adv)) data.addProperty("advancements", Files.readString(adv));
            if (Files.exists(stats)) data.addProperty("stats", Files.readString(stats));
        } catch (IOException | RuntimeException e) {
            KronwerkeCore.LOGGER.warn("Advancements of {} did not travel: {}", p.getGameProfile().getName(), e.toString());
        }
    }

    private static void writeProgress(UUID id, JsonObject d) throws IOException {
        for (String[] k : new String[][] {{"advancements", "adv"}, {"stats", "stats"}}) {
            if (!d.has(k[0])) continue;
            Path dir = server.getWorldPath(k[0].equals("stats") ? LevelResource.PLAYER_STATS_DIR : LevelResource.PLAYER_ADVANCEMENTS_DIR);
            Files.createDirectories(dir);
            Path tmp = dir.resolve(id + ".kwtmp");
            Files.writeString(tmp, d.get(k[0]).getAsString());
            Files.move(tmp, dir.resolve(id + ".json"), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        }
    }

    /** After the gate: a player who came by a move sees the arrival; main forgets who was away. */
    public static void onLogin(ServerPlayer p) {
        UUID id = p.getUUID();
        Arrival a = expected.remove(id);
        if (a != null) {
            arrived.put(id, new Arrival(a.from(), System.currentTimeMillis() + 6_000));
            send(p, TravelPayload.ARRIVE, null);
            p.level().playSound(null, p.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8f, 1.4f);
        } else if (Role.main()) {
            // came straight to main while main thought them away (a side world went down): the
            // file has the last checkpoint; the side world must not take them back with its own
            AwayData away = AwayData.get(server);
            AwayData.Back b = away.back(id);
            String was = away.where(id);
            if (b != null) {
                away.home(id);
                if (was != null) {
                    JsonObject d = new JsonObject();
                    d.addProperty("uuid", id.toString());
                    NetworkSync.send(was, "player.claimed", d, null);
                }
            }
        }
    }

    // ---- items through the portal ----

    private static void sendItem(ItemEntity item) {
        NetworkSync.Peer target = NetworkSync.portalTarget();
        if (target == null || !target.bus() || !target.running() || !NetworkSync.on()) return;
        ItemStack stack = item.getItem();
        if (stack.isEmpty()) return;
        JsonObject data = new JsonObject();
        JsonArray list = new JsonArray();
        Tag t = stack.save(item.level().registryAccess());
        if (!(t instanceof CompoundTag c)) return;
        list.add(encode(c));
        data.add("items", list);
        Entity owner = item.getOwner();
        if (owner != null) data.addProperty("thrower", owner.getUUID().toString());
        String id = UUID.randomUUID().toString();
        itemSends.put(id, new ItemSend(item.level().dimension(), item.getX(), item.getY(), item.getZ(), List.of(stack.copy()), System.currentTimeMillis() + 10_000));
        item.discard();
        NetworkSync.send(target.name(), "kw.items", data, id);
    }

    /** The launcher's answer to a send: delivered to the target's mod, or not. */
    public static void sent(String id, boolean delivered) {
        if (server == null) return;
        server.execute(() -> {
            if (delivered) {
                itemSends.remove(id);
                return;
            }
            ItemSend s = itemSends.remove(id);
            if (s != null) drop(s);
            Departure d = pending.remove(id);
            if (d != null) {
                ServerPlayer p = server.getPlayerList().getPlayer(d.player());
                if (p != null) send(p, TravelPayload.CANCEL, null);
            }
        });
    }

    private static void drop(ItemSend s) {
        ServerLevel level = server.getLevel(s.dim());
        if (level == null) return;
        for (ItemStack st : s.stacks()) {
            ItemEntity e = new ItemEntity(level, s.x(), s.y(), s.z(), st);
            e.setPickUpDelay(40);
            level.addFreshEntity(e);
        }
    }

    private static void receiveItems(String from, JsonObject d) throws IOException {
        AwayData.Back at;
        if (Role.main()) {
            UUID thrower = d.has("thrower") ? UUID.fromString(str(d, "thrower")) : null;
            AwayData.Back b = thrower == null ? null : AwayData.get(server).back(thrower);
            at = b == null ? spawn(server) : b;
        } else {
            at = Hub.arrival(server);
        }
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(at.dim())));
        if (level == null) level = server.overworld();
        level.getChunk(BlockPos.containing(at.x(), at.y(), at.z())); // loaded, so the items are kept
        for (JsonElement e : d.getAsJsonArray("items")) {
            ItemStack st = ItemStack.parseOptional(level.registryAccess(), decode(e.getAsString()));
            if (st.isEmpty()) continue;
            ItemEntity ie = new ItemEntity(level, at.x(), at.y() + 0.5, at.z(), st, 0, 0.1, 0);
            ie.setPickUpDelay(10);
            level.addFreshEntity(ie);
        }
    }

    // ---- the Tiefgräber: Haste in the mining world ----

    private static void tiefgraeber() {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (Compat.hasPower(p, "tiefgraeber")) {
                p.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 220, 0, true, false, true));
            }
        }
    }

    // ---- small things ----

    private static void send(ServerPlayer p, int phase, NetworkSync.Peer target) {
        String label = target == null ? "" : target.label();
        int color = target == null ? 0x8bb6dc : target.color();
        PacketDistributor.sendToPlayer(p, new TravelPayload(phase, label, color));
    }

    private static JsonObject withId(JsonObject d) {
        JsonObject r = new JsonObject();
        r.addProperty("id", str(d, "id"));
        return r;
    }

    static String encode(CompoundTag tag) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            NbtIo.writeCompressed(tag, out);
            return Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    static CompoundTag decode(String s) throws IOException {
        return NbtIo.readCompressed(new ByteArrayInputStream(Base64.getDecoder().decode(s)), NbtAccounter.unlimitedHeap());
    }

    static String str(JsonObject o, String key) {
        JsonElement e = o == null ? null : o.get(key);
        return e == null || e.isJsonNull() ? "" : e.getAsString();
    }
}
