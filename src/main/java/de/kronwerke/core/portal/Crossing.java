package de.kronwerke.core.portal;

import com.google.gson.JsonObject;
import de.kronwerke.core.KronwerkeCore;
import de.kronwerke.core.Text;
import de.kronwerke.core.link.NetworkSync;
import de.kronwerke.core.link.Role;
import net.minecraft.BlockUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.feature.EndPlatformFeature;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.level.portal.PortalShape;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.CommonHooks;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Crossing into a dimension that runs on another server (see {@link Dimensions}): a Nether
 * portal, an End portal, a teleport by a mod or a command, a respawn at a bed or an anchor on
 * the other side, and logging in again where one logged out.
 * <p>
 * The server the player leaves only says how they crossed (where in which portal, or the exact
 * spot); the server they arrive on works out the place in its own world, as vanilla would:
 * it finds the nearest portal in range or builds one, puts players on the End's obsidian
 * platform, or at their bed. That way no portal is ever built in a world nobody plays in.
 */
public final class Crossing {
    /** Players to move once they are online and the time has come (respawns, logins). */
    private static final Map<UUID, Hold> holds = new HashMap<>();
    /** Players to tell, after they arrive, that their bed or anchor was gone. */
    private static final Set<UUID> noBlock = new HashSet<>();
    /** Arrivals that are to move on right after they log in (an anchor that was gone). */
    private static final Map<UUID, Hold> onLogin = new HashMap<>();
    private static final Map<UUID, Long> quiet = new HashMap<>();

    record Hold(String peer, JsonObject arrive, JsonObject back, long notBefore) {}

    private Crossing() {
    }

    // ---- leaving ----

    /**
     * A portal block wants to send an entity across (from the portal mixins). True when the
     * crossing is taken over here (or refused), so the block returns no destination.
     */
    /** When a rider may be told again that the portal does not take their mount. */
    private static final java.util.Map<java.util.UUID, Long> rideHint = new java.util.HashMap<>();

    public static boolean portal(ServerLevel level, Entity e, BlockPos pos, boolean end) {
        ResourceKey<Level> to = end
                ? (level.dimension() == Level.END ? Level.OVERWORLD : Level.END)
                : (level.dimension() == Level.NETHER ? Level.OVERWORLD : Level.NETHER);
        if (Dimensions.here(to)) return false;
        if (!(e instanceof ServerPlayer p)) {
            // a horse, boat or cart with a player on it stays too: tell the rider why (R2-06)
            for (Entity rider : e.getPassengers()) {
                if (rider instanceof ServerPlayer rp && rideHint.getOrDefault(rp.getUUID(), 0L) < level.getGameTime()) {
                    rideHint.put(rp.getUUID(), level.getGameTime() + 100);
                    rp.displayClientMessage(Text.t("portal.ride", "Steig erst ab: das Portal nimmt nur dich mit, kein Reittier und kein Boot.").withStyle(ChatFormatting.GRAY), true);
                }
            }
            return true; // animals, items and carts stay on this side
        }
        if (Travel.busy(p.getUUID())) return true;
        if (!CommonHooks.onTravelToDimension(p, to)) {
            // a stage lock said no, and told the player
            if (end) outOfEndPortal(p, pos);
            return true;
        }
        NetworkSync.Peer host = Dimensions.host(to);
        if (!Dimensions.up(host)) {
            closed(p, host, to);
            if (end) outOfEndPortal(p, pos);
            return true;
        }
        JsonObject a = new JsonObject();
        JsonObject back = null;
        if (end && to == Level.END) {
            a.addProperty("kind", "end");
            if (Role.main()) back = respawnBack(p);
        } else if (end) {
            a.addProperty("respawn", true); // out of the End: home to the bed, as vanilla
        } else {
            a.addProperty("kind", "nether");
            a.addProperty("dim", to.location().toString());
            ServerLevel target = level.getServer().getLevel(to);
            double scale = target != null ? DimensionType.getTeleportationScale(level.dimensionType(), target.dimensionType())
                    : (to == Level.NETHER ? 0.125 : 8);
            a.addProperty("x", p.getX() * scale);
            a.addProperty("y", p.getY());
            a.addProperty("z", p.getZ() * scale);
            BlockState st = level.getBlockState(pos);
            Direction.Axis axis = st.getOptionalValue(BlockStateProperties.HORIZONTAL_AXIS).orElse(Direction.Axis.X);
            BlockUtil.FoundRectangle rect = BlockUtil.getLargestRectangleAround(pos, axis, 21, Direction.Axis.Y, 21, b -> level.getBlockState(b) == st);
            Vec3 rel = p.getRelativePortalPosition(axis, rect);
            a.addProperty("axis", axis.getName());
            a.addProperty("rx", rel.x);
            a.addProperty("ry", rel.y);
            a.addProperty("rz", rel.z);
            a.addProperty("yaw", p.getYRot());
            if (Role.main()) back = Travel.back(p, pos);
        }
        KronwerkeCore.LOGGER.info("{} crosses to {} through a portal", p.getGameProfile().getName(), host.name());
        Travel.depart(p, host, back, a);
        return true;
    }

    /**
     * An End portal that does not take the player. Its blocks hold nobody, and under the portal
     * of a stronghold is lava: who jumps in while the End is locked (eyes of ender can be made
     * two stages earlier) or its server is down would fall through and burn with all they
     * carry. They are set on the nearest block of the frame instead.
     */
    private static void outOfEndPortal(ServerPlayer p, BlockPos portal) {
        ServerLevel level = p.serverLevel();
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (BlockPos b : BlockPos.betweenClosed(portal.offset(-4, 0, -4), portal.offset(4, 0, 4))) {
            if (!level.getBlockState(b).is(net.minecraft.world.level.block.Blocks.END_PORTAL_FRAME)) continue;
            double d = b.distToCenterSqr(p.position());
            if (d < bestD) {
                bestD = d;
                best = b.immutable();
            }
        }
        if (best == null) return; // no frame (a portal a command made, the one in the End): nothing to stand on
        p.teleportTo(level, best.getX() + 0.5, best.getY() + 1.0, best.getZ() + 0.5, p.getYRot(), p.getXRot());
        p.setDeltaMovement(Vec3.ZERO);
        p.fallDistance = 0;
    }

    /** When an entity a mod wanted to send elsewhere was last logged, so a farm does not fill the log. */
    private static long refusedLogAt;

    /** Entity.changeDimension of anything but a player into a dimension on another server: it stays. */
    public static boolean refuseEntity(Entity e, DimensionTransition t) {
        return t != null && refuseEntity(e, t.newLevel());
    }

    /** Anything but a player that would go into the level of a dimension on another server: it stays. */
    public static boolean refuseEntity(Entity e, ServerLevel to) {
        if (e instanceof ServerPlayer || e.level().isClientSide() || to == null) return false;
        if (Dimensions.here(to.dimension())) return false;
        long now = System.currentTimeMillis();
        if (now - refusedLogAt > 60_000) {
            refusedLogAt = now;
            KronwerkeCore.LOGGER.info("{} at {} stays here: {} is played on another server", e.getType().getDescriptionId(), e.blockPosition(), to.dimension().location());
        }
        return true;
    }

    /** A Create portal track's exit (a PortalTrackProvider.Exit record) in a dimension on another server. */
    public static boolean foreignExit(Object exit) {
        if (exit == null) return false;
        try {
            Object level = exit.getClass().getMethod("level").invoke(exit);
            return level instanceof ServerLevel sl && !Dimensions.here(sl.dimension());
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return false;
        }
    }

    /** ServerPlayer.changeDimension into a dimension elsewhere (a mod's teleport, /tp). */
    public static boolean transition(ServerPlayer p, DimensionTransition t) {
        ResourceKey<Level> to = t.newLevel().dimension();
        if (Dimensions.here(to)) return false;
        if (Travel.busy(p.getUUID())) return true;
        if (!CommonHooks.onTravelToDimension(p, to)) return true;
        NetworkSync.Peer host = Dimensions.host(to);
        if (!Dimensions.up(host)) {
            closed(p, host, to);
            return true;
        }
        Travel.depart(p, host, Role.main() ? here(p) : null, exact(to, t.pos(), t.yRot()));
        return true;
    }

    /**
     * ServerPlayer.findRespawnPositionAndUseSpawnBlock: when the bed, the anchor or the world
     * spawn is on another server, the player respawns at a spot here for a moment and moves on.
     */
    public static DimensionTransition respawn(ServerPlayer p, boolean keepInventory, Supplier<DimensionTransition> vanilla) {
        MinecraftServer s = p.server;
        if (p.getRespawnPosition() != null && !Dimensions.here(p.getRespawnDimension())) {
            NetworkSync.Peer host = Dimensions.host(p.getRespawnDimension());
            if (host != null) return hold(p, host, respawnArrive(keepInventory, false));
        }
        DimensionTransition t = vanilla.get();
        if (Dimensions.here(t.newLevel().dimension())) return t;
        NetworkSync.Peer host = Dimensions.host(t.newLevel().dimension());
        if (host == null) return t;
        return hold(p, host, respawnArrive(keepInventory, t.missingRespawnBlock()));
    }

    private static DimensionTransition hold(ServerPlayer p, NetworkSync.Peer host, JsonObject arrive) {
        MinecraftServer s = p.server;
        holds.put(p.getUUID(), new Hold(host.name(), arrive, null, 0));
        AwayData.Back b = holding(s);
        return new DimensionTransition(s.getLevel(key(b.dim())), new Vec3(b.x(), b.y(), b.z()), Vec3.ZERO, b.yaw(), 0, DimensionTransition.DO_NOTHING);
    }

    private static JsonObject respawnArrive(boolean keepInventory, boolean missing) {
        JsonObject a = new JsonObject();
        a.addProperty("respawn", true);
        if (keepInventory) a.addProperty("keep", true);
        if (missing) a.addProperty("missing", true);
        return a;
    }

    /** Players who just arrived from another server, until when they may hang in the air. */
    private static final java.util.Map<UUID, Long> floatGrace = new java.util.concurrent.ConcurrentHashMap<>();

    /** Every tick: holds whose players are here go. */
    public static void tick(MinecraftServer s) {
        if (!floatGrace.isEmpty()) {
            long t = System.currentTimeMillis();
            floatGrace.entrySet().removeIf(e -> {
                ServerPlayer p = s.getPlayerList().getPlayer(e.getKey());
                if (p == null || t > e.getValue()) return true;
                ((de.kronwerke.core.mixin.FloatGraceAccessor) p.connection).kronwerke$setAboveGroundTicks(0);
                return false;
            });
        }
        heldAt.keySet().removeIf(id -> !holds.containsKey(id));
        if (holds.isEmpty()) return;
        long now = System.currentTimeMillis();
        for (var it = holds.entrySet().iterator(); it.hasNext(); ) {
            var en = it.next();
            Hold h = en.getValue();
            ServerPlayer p = s.getPlayerList().getPlayer(en.getKey());
            if (p != null && !p.isDeadOrDying() && !Role.main()) {
                Vec3 at = heldAt.computeIfAbsent(en.getKey(), k -> p.position());
                if (p.position().distanceToSqr(at) > 2.25) p.teleportTo(at.x, at.y, at.z);
            }
            if (h.notBefore() > now) continue;
            if (p == null || p.isDeadOrDying()) continue; // still on the death screen, or gone: wait
            if (Travel.busy(p.getUUID())) continue;
            NetworkSync.Peer peer = NetworkSync.peer(h.peer());
            if (!Dimensions.up(peer)) {
                if (h.notBefore() == 0) closed(p, peer, null);
                en.setValue(new Hold(h.peer(), h.arrive(), h.back(), now + 10_000));
                continue;
            }
            it.remove();
            Travel.depart(p, peer, h.back(), h.arrive());
        }
        holds.keySet().removeIf(id -> s.getPlayerList().getPlayer(id) == null && !onLogin.containsKey(id));
    }

    /** A move that did not come through: a respawn waiting elsewhere tries again soon. */
    static void failed(UUID id, String peer, JsonObject arrive) {
        if (arrive != null && arrive.has("respawn")) holds.put(id, new Hold(peer, arrive, null, System.currentTimeMillis() + 10_000));
    }

    // ---- logging in and out ----

    /** After the gate, for a player who did not arrive by a move. */
    public static void onLogin(ServerPlayer p, boolean arrived) {
        UUID id = p.getUUID();
        // the client may still be loading the world while the player hangs in the air
        if (arrived) floatGrace.put(id, System.currentTimeMillis() + 20_000);
        if (noBlock.remove(id)) p.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.NO_RESPAWN_BLOCK_AVAILABLE, 0));
        Hold next = onLogin.remove(id);
        if (next != null) {
            holds.put(id, new Hold(next.peer(), next.arrive(), next.back(), System.currentTimeMillis() + 1_000));
            return;
        }
        if (arrived) return;
        if (Role.main()) {
            AwayData.Resume r = AwayData.get(p.server).takeResume(id);
            if (r != null) {
                NetworkSync.Peer peer = NetworkSync.peer(r.server());
                // the world they logged out in is gone (the farm world was reset): the place in it
                // means nothing in the new one, they stay where they left the main world. Before,
                // the next login sent them into the new world at the old coordinates (12.11.2026).
                String then = r.epoch(), now = de.kronwerke.core.share.Share.epochOf(r.server());
                if (!then.isEmpty() && now != null && !now.equals(then)) {
                    KronwerkeCore.LOGGER.info("{} logged out on {} before its world was reset and stays on main", p.getGameProfile().getName(), r.server());
                    p.displayClientMessage(Text.t("crossing.resume_reset", "%s ist seit deinem letzten Besuch neu entstanden, du bist an deinem Portal.",
                            label(peer, key(r.at().dim()))).withStyle(ChatFormatting.GRAY), false);
                    return;
                }
                if (Dimensions.up(peer)) {
                    AwayData.Back b = r.at();
                    holds.put(id, new Hold(peer.name(), exact(key(b.dim()), new Vec3(b.x(), b.y(), b.z()), b.yaw()), here(p), System.currentTimeMillis() + 1_500));
                    return;
                }
                p.displayClientMessage(Text.t("crossing.resume_closed", "%s ist gerade nicht erreichbar, du bist an deinem Portal.",
                        label(peer, key(r.at().dim()))).withStyle(ChatFormatting.GRAY), false);
            }
        }
        // in a dimension that is played elsewhere (an old player file, or an operator who joined a side directly)
        ResourceKey<Level> dim = p.level().dimension();
        if (!Dimensions.here(dim)) {
            NetworkSync.Peer host = Dimensions.host(dim);
            if (host == null) return;
            JsonObject a = Role.main() ? exact(dim, p.position(), p.getYRot()) : respawnArrive(false, false);
            holds.put(id, new Hold(host.name(), a, null, System.currentTimeMillis() + 1_500));
        }
    }

    /** What a dimension server adds when a player logs out: where to put them when they come back. */
    static void resume(ServerPlayer p, JsonObject data) {
        // every side world: logging out deep in a mine or in the Nether, the next login goes back there
        if (Role.main() || !Dimensions.here(p.level().dimension()) || p.isDeadOrDying()) return;
        data.add("resume", exact(p.level().dimension(), p.position(), p.getYRot()));
    }

    // ---- arriving ----

    /**
     * Where a player who crossed this way stands here, or null when the arrival says nothing
     * Crossing knows. Runs on the server they arrive on, before they log in.
     */
    static AwayData.Back arrive(MinecraftServer s, UUID id, JsonObject a, CompoundTag tag) {
        if (a == null) return null;
        if (a.has("exact")) {
            ResourceKey<Level> dim = key(Travel.str(a, "dim"));
            ServerLevel level = s.getLevel(dim);
            if (level == null) return null;
            AwayData.Back b = new AwayData.Back(dim.location().toString(), a.get("x").getAsDouble(), a.get("y").getAsDouble(), a.get("z").getAsDouble(), a.get("yaw").getAsFloat());
            // a place from before a reset of the mining world may be inside rock now: then the hub
            if (!Role.main() && !Dimensions.dimensionServer()) {
                BlockPos at = BlockPos.containing(b.x(), b.y(), b.z());
                level.getChunk(at);
                if (!level.getBlockState(at).getCollisionShape(level, at).isEmpty() || !level.getBlockState(at.above()).getCollisionShape(level, at.above()).isEmpty()) return null;
            }
            return b;
        }
        String kind = Travel.str(a, "kind");
        if (kind.equals("end")) return endPlatform(s);
        if (kind.equals("nether")) return netherExit(s, a);
        if (a.has("respawn")) return respawnAt(s, id, a, tag);
        return null;
    }

    /**
     * Whether the chunks the arrival needs are loaded. When not, asks the chunk system for them
     * (a portal ticket, as vanilla gives a portal's other side) and says false.
     */
    static boolean prepared(MinecraftServer s, JsonObject a, CompoundTag tag) {
        if (a == null) return true;
        ServerLevel level;
        BlockPos at;
        int radius;
        String kind = Travel.str(a, "kind");
        if (kind.equals("nether")) {
            level = s.getLevel(key(Travel.str(a, "dim")));
            if (level == null) return true;
            at = level.getWorldBorder().clampToBounds(a.get("x").getAsDouble(), a.get("y").getAsDouble(), a.get("z").getAsDouble());
            radius = 2; // the search for a portal and the place for a new one: 16 blocks around
        } else if (kind.equals("end")) {
            level = s.getLevel(Level.END);
            at = ServerLevel.END_SPAWN_POINT;
            radius = 1;
        } else if (a.has("respawn") && tag.contains("SpawnX")) {
            level = s.getLevel(tag.contains("SpawnDimension") ? key(tag.getString("SpawnDimension")) : Level.OVERWORLD);
            at = new BlockPos(tag.getInt("SpawnX"), tag.getInt("SpawnY"), tag.getInt("SpawnZ"));
            radius = 1;
        } else {
            return true;
        }
        if (level == null) return true;
        net.minecraft.world.level.ChunkPos c = new net.minecraft.world.level.ChunkPos(at);
        boolean all = true;
        for (int dx = -radius; dx <= radius && all; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (level.getChunkSource().getChunkNow(c.x + dx, c.z + dz) == null) {
                    all = false;
                    break;
                }
            }
        }
        if (!all) level.getChunkSource().addRegionTicket(net.minecraft.server.level.TicketType.PORTAL, c, radius + 1, at);
        return all;
    }

    /** A dimension server with nothing to go by: in its own dimension, at a safe place. */
    static AwayData.Back fallback(MinecraftServer s, CompoundTag tag) {
        ResourceKey<Level> own = key(tag.getString("Dimension"));
        if (s.getLevel(own) != null && Dimensions.here(own) && tag.contains("Pos")) {
            var pos = tag.getList("Pos", 6);
            return new AwayData.Back(own.location().toString(), pos.getDouble(0), pos.getDouble(1), pos.getDouble(2), 0);
        }
        if (s.getLevel(Level.END) != null && Dimensions.here(Level.END)) return endPlatform(s);
        JsonObject a = new JsonObject();
        a.addProperty("kind", "nether");
        a.addProperty("dim", Level.NETHER.location().toString());
        a.addProperty("x", 0.5);
        a.addProperty("y", 70);
        a.addProperty("z", 0.5);
        a.addProperty("axis", "x");
        a.addProperty("rx", 0.5);
        a.addProperty("ry", 0);
        a.addProperty("rz", 0);
        a.addProperty("yaw", 0);
        AwayData.Back b = netherExit(s, a);
        return b != null ? b : holding(s);
    }

    private static AwayData.Back endPlatform(MinecraftServer s) {
        ServerLevel end = s.getLevel(Level.END);
        if (end == null) return null;
        BlockPos sp = ServerLevel.END_SPAWN_POINT;
        EndPlatformFeature.createEndPlatform(end, sp.below(), true);
        Vec3 v = sp.getBottomCenter().subtract(0, 1, 0);
        return new AwayData.Back(Level.END.location().toString(), v.x, v.y, v.z, Direction.WEST.toYRot());
    }

    /** The other side of a Nether portal: the nearest portal in range, or a new one, as vanilla. */
    private static AwayData.Back netherExit(MinecraftServer s, JsonObject a) {
        ResourceKey<Level> dim = key(Travel.str(a, "dim"));
        ServerLevel level = s.getLevel(dim);
        if (level == null) return null;
        WorldBorder border = level.getWorldBorder();
        BlockPos exit = border.clampToBounds(a.get("x").getAsDouble(), a.get("y").getAsDouble(), a.get("z").getAsDouble());
        boolean toNether = dim == Level.NETHER;
        Direction.Axis fromAxis = "z".equals(Travel.str(a, "axis")) ? Direction.Axis.Z : Direction.Axis.X;
        BlockUtil.FoundRectangle rect;
        Optional<BlockPos> found = level.getPortalForcer().findClosestPortalPosition(exit, toNether, border);
        if (found.isPresent()) {
            BlockState st = level.getBlockState(found.get());
            rect = BlockUtil.getLargestRectangleAround(found.get(), st.getValue(BlockStateProperties.HORIZONTAL_AXIS), 21, Direction.Axis.Y, 21,
                    b -> level.getBlockState(b) == st);
        } else {
            Optional<BlockUtil.FoundRectangle> made = level.getPortalForcer().createPortal(exit, fromAxis);
            if (made.isEmpty()) {
                KronwerkeCore.LOGGER.error("No portal could be made at {} in {}", exit, dim.location());
                return null;
            }
            rect = made.get();
        }
        // where in the portal, as NetherPortalBlock.createDimensionTransition puts an entity
        BlockPos min = rect.minCorner;
        Direction.Axis axis = level.getBlockState(min).getOptionalValue(BlockStateProperties.HORIZONTAL_AXIS).orElse(Direction.Axis.X);
        EntityDimensions dims = EntityType.PLAYER.getDimensions();
        double w = dims.width(), h = dims.height();
        double along = w / 2 + (rect.axis1Size - w) * a.get("rx").getAsDouble();
        double up = (rect.axis2Size - h) * a.get("ry").getAsDouble();
        double across = 0.5 + a.get("rz").getAsDouble();
        boolean x = axis == Direction.Axis.X;
        Vec3 v = new Vec3(min.getX() + (x ? along : across), min.getY() + up, min.getZ() + (x ? across : along));
        v = PortalShape.findCollisionFreePosition(v, level, null, dims);
        float yaw = a.get("yaw").getAsFloat() + (fromAxis == axis ? 0 : 90);
        return new AwayData.Back(dim.location().toString(), v.x, v.y, v.z, yaw);
    }

    /** At the bed or anchor the player file names, if it is here and still there. */
    private static AwayData.Back respawnAt(MinecraftServer s, UUID id, JsonObject a, CompoundTag tag) {
        boolean missing = a.has("missing");
        if (!missing && tag.contains("SpawnX")) {
            ResourceKey<Level> dim = tag.contains("SpawnDimension") ? key(tag.getString("SpawnDimension")) : Level.OVERWORLD;
            ServerLevel level = s.getLevel(dim);
            if (level != null && Dimensions.here(dim)) {
                BlockPos pos = new BlockPos(tag.getInt("SpawnX"), tag.getInt("SpawnY"), tag.getInt("SpawnZ"));
                Optional<ServerPlayer.RespawnPosAngle> r = de.kronwerke.core.mixin.ServerPlayerRespawnInvoker.kronwerke$find(
                        level, pos, tag.getFloat("SpawnAngle"), tag.getBoolean("SpawnForced"), a.has("keep"));
                if (r.isPresent()) return new AwayData.Back(dim.location().toString(), r.get().position().x, r.get().position().y, r.get().position().z, r.get().yaw());
                missing = true;
            }
        }
        if (missing) noBlock.add(id);
        if (Role.main()) return Travel.spawn(s);
        // a dimension server with no bed or anchor for them: home to main's spawn right after
        NetworkSync.Peer main = NetworkSync.peerByRole("main");
        if (main != null) onLogin.put(id, new Hold(main.name(), respawnArrive(false, true), null, 0));
        noBlock.remove(id);
        return holding(s);
    }

    // ---- small things ----

    /**
     * A spot on this server where a player waits for a moment. On the End server that is
     * the End's own platform, not an overworld nobody plays; elsewhere the overworld spawn.
     * Wherever it is, a waiting player cannot walk off, build or be hurt (held()), so
     * nothing happens there that would be lost (review R2-07).
     */
    private static AwayData.Back holding(MinecraftServer s) {
        if (Dimensions.dimensionServer() && Dimensions.here(Level.END)) {
            ServerLevel end = s.getLevel(Level.END);
            if (end != null) {
                net.minecraft.world.level.levelgen.feature.EndPlatformFeature.createEndPlatform(end, ServerLevel.END_SPAWN_POINT.below(), true);
                BlockPos at = ServerLevel.END_SPAWN_POINT;
                return new AwayData.Back(Level.END.location().toString(), at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 90);
            }
        }
        return Travel.spawn(s);
    }

    /** Where held players were put, so they stay there. */
    private static final java.util.Map<UUID, Vec3> heldAt = new java.util.HashMap<>();

    /** Whether the player waits here for a move to another server. */
    public static boolean held(UUID id) {
        // on main the waiting room is the real world: there a player plays on while waiting
        return !Role.main() && holds.containsKey(id);
    }

    /** main: where a player who leaves for the End comes back if the End goes down: their bed, or the spawn. */
    private static JsonObject respawnBack(ServerPlayer p) {
        AwayData.Back b = null;
        BlockPos pos = p.getRespawnPosition();
        if (pos != null && Dimensions.here(p.getRespawnDimension())) {
            ServerLevel level = p.server.getLevel(p.getRespawnDimension());
            if (level != null) {
                var r = de.kronwerke.core.mixin.ServerPlayerRespawnInvoker.kronwerke$find(level, pos, p.getRespawnAngle(), p.isRespawnForced(), true);
                if (r.isPresent()) b = new AwayData.Back(p.getRespawnDimension().location().toString(), r.get().position().x, r.get().position().y, r.get().position().z, r.get().yaw());
            }
        }
        if (b == null) b = Travel.spawn(p.server);
        JsonObject o = new JsonObject();
        o.addProperty("dim", b.dim());
        o.addProperty("x", b.x());
        o.addProperty("y", b.y());
        o.addProperty("z", b.z());
        o.addProperty("yaw", b.yaw());
        return o;
    }

    private static JsonObject here(ServerPlayer p) {
        JsonObject o = new JsonObject();
        o.addProperty("dim", p.level().dimension().location().toString());
        o.addProperty("x", p.getX());
        o.addProperty("y", p.getY());
        o.addProperty("z", p.getZ());
        o.addProperty("yaw", p.getYRot());
        return o;
    }

    static JsonObject exact(ResourceKey<Level> dim, Vec3 pos, float yaw) {
        JsonObject a = new JsonObject();
        a.addProperty("exact", true);
        a.addProperty("dim", dim.location().toString());
        a.addProperty("x", pos.x);
        a.addProperty("y", pos.y);
        a.addProperty("z", pos.z);
        a.addProperty("yaw", yaw);
        return a;
    }

    private static void closed(ServerPlayer p, NetworkSync.Peer host, ResourceKey<Level> dim) {
        long now = System.currentTimeMillis();
        Long q = quiet.get(p.getUUID());
        if (q != null && q > now) return;
        quiet.put(p.getUUID(), now + 5_000);
        p.displayClientMessage(Text.t("crossing.closed", "Das Portal flackert: %s ist gerade nicht erreichbar.", label(host, dim))
                .withStyle(ChatFormatting.GRAY), true);
    }

    private static Component label(NetworkSync.Peer host, ResourceKey<Level> dim) {
        if (host != null && !host.label().isEmpty()) return Component.literal(host.label());
        if (dim == Level.NETHER) return Text.t("dimension.minecraft.the_nether", "Der Nether");
        if (dim == Level.END) return Text.t("dimension.minecraft.the_end", "Das End");
        return Text.t("crossing.other", "die andere Welt");
    }

    static ResourceKey<Level> key(String dim) {
        ResourceLocation rl = ResourceLocation.tryParse(dim);
        return ResourceKey.create(Registries.DIMENSION, rl == null ? Level.OVERWORLD.location() : rl);
    }
}
