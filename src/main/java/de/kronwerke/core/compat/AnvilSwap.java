package de.kronwerke.core.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Easy Anvils puts a block of its own in place of every anvil a player sets, and since its
 * world generation mixin also of those a new chunk brings. Every other anvil stays the game's
 * own: one set by a command, a schematic or a machine, one in a chunk older than the mod. The
 * mod refuses those ("Unable to open. Break and replace to use."). Visual Workbench, its
 * sibling, swaps such a block when it is clicked; Easy Anvils 21.1.0 has no such setting, so
 * this does the same for it: a right click on a plain anvil turns it into the mod's twin, with
 * its facing, before the mod looks at the click, and the twin answers the click. Anvils in the mod's tag of anvils to leave
 * alone are left alone.
 */
public final class AnvilSwap {
    private AnvilSwap() {}

    private static final boolean LOADED = ModList.get().isLoaded("easyanvils");
    private static final TagKey<Block> UNALTERED = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath("easyanvils", "unaltered_anvils"));

    /** Runs on both sides, so that the client does not show the refusal for a block the server has swapped. */
    public static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        if (!LOADED || event.getEntity().isSpectator()) return;
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        BlockState twin = twin(state);
        if (twin == null) return;
        level.setBlock(pos, twin, Block.UPDATE_ALL);
        // the game has read the block before this event and would let the plain anvil answer the
        // click, with the game's own anvil window: answer it with the new block instead. A sneak
        // click with something in a hand is not for the block, it goes on as it is.
        Player player = event.getEntity();
        boolean holds = !player.getMainHandItem().isEmpty() || !player.getOffhandItem().isEmpty();
        if (event.getHand() != InteractionHand.MAIN_HAND || (player.isSecondaryUseActive() && holds)) return;
        event.setCanceled(true);
        event.setCancellationResult(level.getBlockState(pos).useWithoutItem(level, player, event.getHitVec()));
    }

    /** The mod's block for a plain anvil, or null when there is nothing to swap. */
    static BlockState twin(BlockState state) {
        if (!(state.getBlock() instanceof AnvilBlock) || state.is(UNALTERED)) return null;
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (id.getNamespace().equals("easyanvils")) return null;
        // the mod names its twins <namespace>/<path> of the block they stand for
        Block other = BuiltInRegistries.BLOCK.getOptional(
                ResourceLocation.fromNamespaceAndPath("easyanvils", id.getNamespace() + "/" + id.getPath())).orElse(null);
        if (other == null) return null;
        BlockState next = other.defaultBlockState();
        for (Property<?> property : state.getProperties()) next = copy(state, next, property);
        return next;
    }

    private static <T extends Comparable<T>> BlockState copy(BlockState from, BlockState to, Property<T> property) {
        return to.hasProperty(property) ? to.setValue(property, from.getValue(property)) : to;
    }
}
