package de.kronwerke.core.obelisk;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The obelisk at spawn: the base block of a pillar four blocks tall (the upper half is
 * {@link ObeliskTopBlock}, placed and removed with this one). It cannot be broken outside
 * creative mode and registers itself as the obelisk when an operator places it. Everything
 * the obelisk does (deposits, feeders, the goals) lives in {@link Obelisk}; this block is the
 * thing players see and click.
 */
public class ObeliskBlock extends Block {
    public static final MapCodec<ObeliskBlock> CODEC = simpleCodec(ObeliskBlock::new);

    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(0, 0, 0, 16, 4, 16),
            Block.box(1, 4, 1, 15, 6, 15),
            Block.box(2, 6, 2, 14, 32, 14));

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
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockState above = level.getBlockState(pos.above());
        return above.isAir() || above.canBeReplaced() || above.is(ObeliskBlocks.OBELISK_TOP.get());
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide() && !level.getBlockState(pos.above()).is(ObeliskBlocks.OBELISK_TOP.get())) {
            level.setBlock(pos.above(), ObeliskBlocks.OBELISK_TOP.get().defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && !level.isClientSide() && level.getBlockState(pos.above()).is(ObeliskBlocks.OBELISK_TOP.get())) {
            level.removeBlock(pos.above(), false);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide() || !(placer instanceof Player player) || !player.hasPermissions(2)) return;
        Obelisk.get().data().set(level.dimension().location().toString(), pos);
    }

    @Override
    public boolean canHarvestBlock(BlockState state, BlockGetter level, BlockPos pos, Player player) {
        return player.isCreative();
    }
}
