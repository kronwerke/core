package de.kronwerke.core.portal;

import de.kronwerke.core.Text;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** A source gem on a finished frame lights the portal; the gem is used up. */
public final class Ignite {
    private static final ResourceLocation SOURCE_GEM = ResourceLocation.fromNamespaceAndPath("ars_nouveau", "source_gem");

    private Ignite() {
    }

    static Item key() {
        Item i = BuiltInRegistries.ITEM.get(SOURCE_GEM);
        return i == Items.AIR ? Items.AMETHYST_SHARD : i;
    }

    public static void onRightClick(PlayerInteractEvent.RightClickBlock e) {
        ItemStack held = e.getItemStack();
        if (!held.is(key()) || !(e.getLevel() instanceof ServerLevel level)) return;
        if (!level.getBlockState(e.getPos()).is(PortalBlocks.FRAME.get())) return;
        BlockPos at = e.getPos().relative(e.getFace() == null ? net.minecraft.core.Direction.UP : e.getFace());
        PortalShape shape = PortalShape.find(level, at);
        if (shape == null) {
            e.getEntity().displayClientMessage(Text.t("portal.frame", "Der Rahmen ist nicht geschlossen: innen 2 bis 21 breit, 3 bis 21 hoch, rundherum Grubenrahmen.")
                    .withStyle(ChatFormatting.GRAY), true);
            e.setCanceled(true);
            e.setCancellationResult(InteractionResult.FAIL);
            return;
        }
        shape.fill(level);
        if (!e.getEntity().isCreative()) held.shrink(1);
        level.playSound(null, at, SoundEvents.END_PORTAL_SPAWN, SoundSource.BLOCKS, 0.6f, 1.6f);
        level.playSound(null, at, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.0f, 0.7f);
        e.setCanceled(true);
        e.setCancellationResult(InteractionResult.SUCCESS);
    }
}
