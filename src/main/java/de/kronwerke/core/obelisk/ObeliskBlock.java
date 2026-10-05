package de.kronwerke.core.obelisk;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The core of the obelisk. An operator places it and {@link ObeliskStructure} builds the
 * rest around it: the 5x5 plinth, the shaft and the crystal. It cannot be broken outside
 * creative mode and registers itself as the obelisk; breaking it in creative mode takes the
 * whole build with it. Everything
 * the obelisk does (deposits, feeders, the goals) lives in {@link Obelisk}; this block is the
 * thing players see and click.
 */
public class ObeliskBlock extends Block {
    public static final MapCodec<ObeliskBlock> CODEC = simpleCodec(ObeliskBlock::new);

    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(1, 0, 1, 15, 4, 15),
            Block.box(2, 4, 2, 14, 16, 14));

    public ObeliskBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level instanceof net.minecraft.server.level.ServerLevel sl) {
            ObeliskStructure.clear(sl, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!(level instanceof net.minecraft.server.level.ServerLevel sl) || !(placer instanceof Player player) || !player.hasPermissions(2)) return;
        ObeliskStructure.build(sl, pos);
        Obelisk.get().data().set(level.dimension().location().toString(), pos);
    }

    @Override
    public boolean canHarvestBlock(BlockState state, BlockGetter level, BlockPos pos, Player player) {
        return player.isCreative();
    }
}
