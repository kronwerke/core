package de.kronwerke.core.portal;

import de.kronwerke.core.Text;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** A source gem on a finished frame lights the portal; the gem is used up. */
public final class Ignite {
    private static final ResourceLocation SOURCE_GEM = ResourceLocation.fromNamespaceAndPath("ars_nouveau", "source_gem");

    private Ignite() {
    }

    private record Due(ServerLevel level, long tick, Runnable what) {
    }

    private static final List<Due> due = new ArrayList<>();

    /** Something for the end of the lighting; lost if the server stops before, which costs a sound. */
    private static void later(ServerLevel level, int ticks, Runnable what) {
        due.add(new Due(level, level.getServer().getTickCount() + ticks, what));
    }

    public static void onTick(ServerTickEvent.Post e) {
        if (due.isEmpty()) return;
        long now = e.getServer().getTickCount();
        for (Iterator<Due> it = due.iterator(); it.hasNext(); ) {
            Due d = it.next();
            if (d.level().getServer() != e.getServer()) {
                it.remove();
            } else if (now >= d.tick()) {
                it.remove();
                d.what().run();
            }
        }
    }

    static Item key() {
        Item i = BuiltInRegistries.ITEM.get(SOURCE_GEM);
        return i == Items.AIR ? Items.AMETHYST_SHARD : i;
    }

    public static void onRightClick(PlayerInteractEvent.RightClickBlock e) {
        ItemStack held = e.getItemStack();
        if (!held.is(key()) || !(e.getLevel() instanceof ServerLevel level)) return;
        if (!level.getBlockState(e.getPos()).is(PortalBlocks.FRAME.get())) return;
        // the gem may go on any side of a frame block, not only on the side that faces the inside
        BlockPos at = null;
        PortalShape shape = null;
        boolean burning = false;
        List<net.minecraft.core.Direction> sides = new ArrayList<>(List.of(net.minecraft.core.Direction.values()));
        if (e.getFace() != null) {
            sides.remove(e.getFace());
            sides.add(0, e.getFace());
        }
        for (net.minecraft.core.Direction d : sides) {
            PortalShape s = PortalShape.find(level, e.getPos().relative(d));
            if (s == null) continue;
            if (level.getBlockState(s.positions().get(0)).is(PortalBlocks.PORTAL.get())) {
                burning = true; // this side's portal stands already
                continue;
            }
            shape = s;
            at = e.getPos().relative(d);
            break;
        }
        if (shape == null && burning) return; // nothing to light, and no gem lost on it
        if (shape == null) {
            e.getEntity().displayClientMessage(Text.t("portal.frame", "Der Rahmen ist nicht geschlossen: innen 2 bis 21 breit, 3 bis 21 hoch, rundherum Grubenrahmen.")
                    .withStyle(ChatFormatting.GRAY), true);
            e.setCanceled(true);
            e.setCancellationResult(InteractionResult.FAIL);
            return;
        }
        if (!e.getEntity().isCreative()) held.shrink(1);
        // the gem goes into the frame: a deep note, then the light runs around and the surface forms
        level.playSound(null, at, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.0f, 0.7f);
        level.playSound(null, at, SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.BLOCKS, 0.7f, 0.8f);
        BlockPos gem = e.getPos();
        level.sendParticles(ParticleTypes.END_ROD, gem.getX() + 0.5, gem.getY() + 0.5, gem.getZ() + 0.5, 14, 0.4, 0.4, 0.4, 0.05);
        PortalShape lit = shape;
        int ticks = lit.ignite(level, gem);
        Vec3 c = lit.centre();
        double wide = lit.axis() == net.minecraft.core.Direction.Axis.X ? lit.width() / 2.5 : 0.15;
        double deep = lit.axis() == net.minecraft.core.Direction.Axis.X ? 0.15 : lit.width() / 2.5;
        later(level, ticks, () -> {
            // it stands: only if nobody broke the frame meanwhile
            if (!level.getBlockState(BlockPos.containing(c)).is(PortalBlocks.PORTAL.get())) return;
            level.playSound(null, c.x, c.y, c.z, SoundEvents.END_PORTAL_SPAWN, SoundSource.BLOCKS, 0.6f, 1.6f);
            level.playSound(null, c.x, c.y, c.z, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.8f, 1.3f);
            level.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 30 + lit.width() * lit.height() * 2, wide, lit.height() / 2.5, deep, 0.08);
            level.sendParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 1, 0, 0, 0, 0);
        });
        e.setCanceled(true);
        e.setCancellationResult(InteractionResult.SUCCESS);
    }
}
