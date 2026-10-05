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
 * buttons, waystones and the obelisk keep working. A container next to the obelisk may be
 * placed by anyone, so feeders keep working; nothing else may.
 */
public final class SpawnGuard {
    private SpawnGuard() {
    }

    private static int radius() {
        return KronwerkeConfig.SPAWN_RADIUS.get();
    }

    public static boolean inside(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel sl) || sl.dimension() != Level.OVERWORLD || radius() <= 0) return false;
        BlockPos spawn = sl.getSharedSpawnPos();
        int dx = pos.getX() - spawn.getX(), dz = pos.getZ() - spawn.getZ();
        return Math.max(Math.abs(dx), Math.abs(dz)) <= radius();
    }

    private static boolean mayBuild(Entity entity) {
        return entity instanceof Player p && (p.hasPermissions(2) || p.isCreative());
    }

    private static boolean nearObelisk(Level level, BlockPos pos) {
        ObeliskData d = Obelisk.get().data();
        if (!d.isSet() || !level.dimension().location().toString().equals(d.dimension())) return false;
        BlockPos o = d.pos();
        int r = KronwerkeConfig.FEEDER_RADIUS.get();
        return Math.abs(pos.getX() - o.getX()) <= r && Math.abs(pos.getY() - o.getY()) <= r && Math.abs(pos.getZ() - o.getZ()) <= r;
    }

    private static void tell(Entity entity) {
        if (entity instanceof ServerPlayer p) {
            p.displayClientMessage(Text.t("spawn.protected", "Der Spawn gehört allen. Hier wird nicht gebaut.").withStyle(ChatFormatting.RED), true);
        }
    }

    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !inside(level, event.getPos()) || mayBuild(event.getPlayer())) return;
        // a player takes back their own feeder
        if (nearObelisk(level, event.getPos()) && event.getPlayer().getUUID().equals(Obelisk.get().data().feeder(event.getPos()))) return;
        event.setCanceled(true);
        tell(event.getPlayer());
    }

    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !inside(level, event.getPos()) || mayBuild(event.getEntity())) return;
        if (nearObelisk(level, event.getPos()) && level.getCapability(Capabilities.ItemHandler.BLOCK, event.getPos(), null) != null) return;
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

    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        event.getAffectedBlocks().removeIf(p -> inside(level, p));
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

    public static void onDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim) || !(event.getSource().getEntity() instanceof Player attacker)) return;
        if (attacker == victim) return;
        if (inside(victim.level(), victim.blockPosition()) || inside(attacker.level(), attacker.blockPosition())) {
            event.setCanceled(true);
        }
    }

    /** Buckets, fire charges, spawn eggs and the like: using an item on a block inside is placing. */
    public static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !inside(level, event.getPos()) || mayBuild(event.getEntity())) return;
        BlockState state = level.getBlockState(event.getPos());
        if (state.hasBlockEntity() || state.getBlock().defaultBlockState().hasBlockEntity()) return; // containers, waystones, the obelisk's neighbours
        if (event.getItemStack().isEmpty()) return; // doors, buttons, levers
        if (event.getItemStack().getItem() instanceof net.minecraft.world.item.BlockItem) return; // handled by onPlace
        if (nearObelisk(level, event.getPos())) return;
        event.setUseItem(net.neoforged.neoforge.common.util.TriState.FALSE);
    }
}
