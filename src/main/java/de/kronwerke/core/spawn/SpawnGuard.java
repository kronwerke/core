package de.kronwerke.core.spawn;

import de.kronwerke.core.Text;
import de.kronwerke.core.config.KronwerkeConfig;
import de.kronwerke.core.obelisk.Obelisk;
import de.kronwerke.core.obelisk.ObeliskData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.EntityMobGriefingEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;

/**
 * The area around the world spawn, kept the way the team built it. Inside spawn.radius
 * blocks of the overworld spawn nobody but operators breaks or places blocks, no explosion
 * takes a block, no mob griefs, no hostile mob spawns and no player hurts another. Doors,
 * buttons, waystones and the obelisk keep working. That holds at the obelisk too: nobody
 * sets a hopper, a pipe or a belt next to it, giving is done by hand.
 */
public final class SpawnGuard {
    private SpawnGuard() {
    }

    /** On a side world only the arrival place is protected, the rest is there to be dug up. */
    private static int radius() {
        int r = KronwerkeConfig.SPAWN_RADIUS.get();
        return de.kronwerke.core.link.Role.main() ? r : Math.min(r, 8);
    }

    public static boolean inside(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel sl) || sl.dimension() != Level.OVERWORLD || radius() <= 0) return false;
        int[] c = center(sl);
        int dx = pos.getX() - c[0], dz = pos.getZ() - c[1];
        return Math.max(Math.abs(dx), Math.abs(dz)) <= radius();
    }

    private static int[] center;
    private static String centerFrom;

    /**
     * The middle of the protection: the configured x,z, or the world spawn as it was the first
     * time (kept in the world's data), so /setworldspawn cannot move it away from the obelisk.
     */
    static int[] center(ServerLevel level) {
        String cfg = KronwerkeConfig.SPAWN_CENTER.get().trim();
        if (center != null && cfg.equals(centerFrom)) return center;
        int[] c = null;
        if (!cfg.isEmpty()) {
            String[] p = cfg.split(",");
            try {
                c = new int[]{Integer.parseInt(p[0].trim()), Integer.parseInt(p[1].trim())};
            } catch (RuntimeException e) {
                de.kronwerke.core.KronwerkeCore.LOGGER.warn("spawn.center {} is not x,z; the kept spawn counts", cfg);
            }
        }
        if (c == null) c = Anchor.get(level);
        center = c;
        centerFrom = cfg;
        return c;
    }

    /** The first world spawn the protection saw. */
    public static final class Anchor extends net.minecraft.world.level.saveddata.SavedData {
        static final String NAME = "kronwerke_spawnguard";
        int x, z;
        boolean set;

        static int[] get(ServerLevel level) {
            Anchor a = level.getServer().overworld().getDataStorage().computeIfAbsent(
                    new Factory<>(Anchor::new, (t, p) -> {
                        Anchor n = new Anchor();
                        n.x = t.getInt("x");
                        n.z = t.getInt("z");
                        n.set = t.getBoolean("set");
                        return n;
                    }, null), NAME);
            if (!a.set) {
                BlockPos s = level.getServer().overworld().getSharedSpawnPos();
                a.x = s.getX();
                a.z = s.getZ();
                a.set = true;
                a.setDirty();
            }
            return new int[]{a.x, a.z};
        }

        @Override
        public net.minecraft.nbt.CompoundTag save(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider provider) {
            tag.putInt("x", x);
            tag.putInt("z", z);
            tag.putBoolean("set", set);
            return tag;
        }
    }

    /** Things at spawn that belong to everybody: frames, paintings, armour stands, animals, villagers, pets. */
    private static boolean sheltered(Entity e) {
        if (e instanceof net.minecraft.world.entity.decoration.HangingEntity || e instanceof net.minecraft.world.entity.decoration.ArmorStand) return true;
        return e instanceof net.minecraft.world.entity.Mob m && m.getType().getCategory() != MobCategory.MONSTER && !(m instanceof net.minecraft.world.entity.monster.Enemy);
    }

    private static boolean mayBuild(Entity entity) {
        return entity instanceof Player p && (p.hasPermissions(2) || p.isCreative());
    }

    /**
     * The player's own things once more, a tick later. A placement that is refused here is undone
     * on the server, stack and all, but the client has already taken one off the stack in the
     * hand and nobody tells it otherwise: it showed 19 of 20 for good after one refused block at
     * the obelisk (seen on the real servers), which reads as a lost item.
     */
    private static void resync(ServerPlayer p) {
        p.server.tell(new net.minecraft.server.TickTask(p.server.getTickCount() + 1, () -> {
            if (!p.hasDisconnected()) p.inventoryMenu.sendAllDataToRemote();
        }));
    }

    private static void tell(Entity entity) {
        if (entity instanceof ServerPlayer p) {
            resync(p);
            int[] c = center(p.serverLevel());
            int out = radius() + 1 - Math.max(Math.abs(p.getBlockX() - c[0]), Math.abs(p.getBlockZ() - c[1]));
            p.displayClientMessage(Text.t("spawn.protected_where", "Der Spawn gehört allen. Bauen geht %s Blöcke weiter draußen (Schutzzone %s Blöcke um die Mitte).",
                    Math.max(1, out), radius()).withStyle(ChatFormatting.RED), true);
        }
    }

    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !inside(level, event.getPos()) || mayBuild(event.getPlayer())) return;
        event.setCanceled(true);
        tell(event.getPlayer());
    }

    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !inside(level, event.getPos()) || mayBuild(event.getEntity())) return;
        event.setCanceled(true);
        tell(event.getEntity());
    }

    public static void onMultiPlace(BlockEvent.EntityMultiPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || mayBuild(event.getEntity())) return;
        for (var snap : event.getReplacedBlockSnapshots()) {
            if (inside(level, snap.getPos())) {
                event.setCanceled(true);
                tell(event.getEntity());
                return;
            }
        }
    }

    /** Spawn stays whole, and a few blocks survive every explosion anywhere: bedrock, end portal frames, boss altars. */
    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        event.getAffectedBlocks().removeIf(p -> inside(level, p) || keeps(level.getBlockState(p)));
        event.getAffectedEntities().removeIf(e -> sheltered(e) && inside(level, e.blockPosition()));
    }

    /** Hitting what belongs to everybody at spawn (a frame, a stand, an animal, a pet). */
    public static void onAttack(net.neoforged.neoforge.event.entity.player.AttackEntityEvent event) {
        Entity target = event.getTarget();
        if (!sheltered(target) || !inside(target.level(), target.blockPosition()) || mayBuild(event.getEntity())) return;
        event.setCanceled(true);
        tell(event.getEntity());
    }

    /** Taking from or turning a frame, dressing a stand at spawn. */
    public static void onEntityInteract(PlayerInteractEvent.EntityInteractSpecific event) {
        Entity target = event.getTarget();
        if (!(target instanceof net.minecraft.world.entity.decoration.ArmorStand) || !inside(target.level(), target.blockPosition()) || mayBuild(event.getEntity())) return;
        event.setCanceled(true);
    }

    public static void onEntityInteractPlain(PlayerInteractEvent.EntityInteract event) {
        Entity target = event.getTarget();
        if (!(target instanceof net.minecraft.world.entity.decoration.ItemFrame) || !inside(target.level(), target.blockPosition()) || mayBuild(event.getEntity())) return;
        event.setCanceled(true);
    }

    /** A pattern is an id, or an id with * standing for anything. */
    static boolean matches(String id, String pattern) {
        if (!pattern.contains("*")) return id.equals(pattern);
        String[] parts = pattern.split("\\*", -1);
        int at = 0;
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (part.isEmpty()) continue;
            int found = i == 0 ? (id.startsWith(part) ? 0 : -1) : id.indexOf(part, at);
            if (found < 0) return false;
            at = found + part.length();
            if (i == parts.length - 1 && !id.endsWith(part)) return false;
        }
        return true;
    }

    private static boolean keeps(BlockState state) {
        if (state.is(net.minecraft.world.level.block.Blocks.BEDROCK) || state.is(net.minecraft.world.level.block.Blocks.END_PORTAL_FRAME)
                || state.is(net.minecraft.world.level.block.Blocks.END_PORTAL) || state.is(net.minecraft.world.level.block.Blocks.END_GATEWAY)
                || state.is(net.minecraft.world.level.block.Blocks.REINFORCED_DEEPSLATE)) return true;
        String id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        for (String keep : KronwerkeConfig.BLAST_PROOF.get()) {
            if (matches(id, keep)) return true;
        }
        return false;
    }

    public static void onMobGriefing(EntityMobGriefingEvent event) {
        Entity e = event.getEntity();
        if (e != null && inside(e.level(), e.blockPosition())) event.setCanGrief(false);
    }

    public static void onSpawn(FinalizeSpawnEvent event) {
        var mob = event.getEntity();
        if (mob.getType().getCategory() == MobCategory.MONSTER && inside(mob.level(), mob.blockPosition())) {
            event.setSpawnCancelled(true);
        }
    }

    /**
     * At spawn nobody hurts a player but a monster or the world itself (falling, lava): not
     * another player, their wolf, their arrow or their TNT. Animals, villagers and pets there
     * take no damage from players or what players set off.
     */
    public static void onDamage(LivingIncomingDamageEvent event) {
        var src = event.getSource();
        if (src.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        Entity cause = src.getEntity(), direct = src.getDirectEntity();
        if (event.getEntity() instanceof Player victim) {
            if (cause == victim && direct == victim) return;
            boolean byMonster = cause instanceof net.minecraft.world.entity.monster.Enemy || (cause == null && direct instanceof net.minecraft.world.entity.monster.Enemy);
            if (byMonster || (cause == null && direct == null)) return;
            if (inside(victim.level(), victim.blockPosition()) || (cause != null && inside(cause.level(), cause.blockPosition()))) {
                event.setCanceled(true);
            }
            return;
        }
        if (sheltered(event.getEntity()) && inside(event.getEntity().level(), event.getEntity().blockPosition())
                && (cause instanceof Player || src.is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION) || direct instanceof net.minecraft.world.entity.projectile.Projectile)) {
            if (!(cause instanceof Player p && mayBuild(p))) event.setCanceled(true);
        }
    }

    /** Buckets, fire charges, spawn eggs and the like: using an item on a block inside is placing. */
    public static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !inside(level, event.getPos()) || mayBuild(event.getEntity())) return;
        BlockState state = level.getBlockState(event.getPos());
        if (state.hasBlockEntity() || state.getBlock().defaultBlockState().hasBlockEntity()) return; // containers, waystones, the obelisk's neighbours
        if (event.getItemStack().isEmpty()) return; // doors, buttons, levers
        if (event.getItemStack().getItem() instanceof net.minecraft.world.item.BlockItem) return; // handled by onPlace
        // buckets, fire and the like: not at spawn, and not next to the obelisk either
        event.setUseItem(net.neoforged.neoforge.common.util.TriState.FALSE);
    }
}
