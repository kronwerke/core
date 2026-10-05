package de.kronwerke.core.obelisk;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The upper two blocks of the obelisk. Placed by {@link ObeliskBlock} and gone with it; it
 * has no item and cannot stand on its own.
 */
public class ObeliskTopBlock extends Block {
    public static final MapCodec<ObeliskTopBlock> CODEC = simpleCodec(ObeliskTopBlock::new);

    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(2, 0, 2, 14, 12, 14),
            Block.box(3, 12, 3, 13, 22, 13),
            Block.box(5, 22, 5, 11, 32, 11));

    public ObeliskTopBlock(Properties properties) {
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
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return level.getBlockState(pos.below()).is(ObeliskBlocks.OBELISK.get());
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (direction == Direction.DOWN && !neighborState.is(ObeliskBlocks.OBELISK.get())) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    public boolean canHarvestBlock(BlockState state, BlockGetter level, BlockPos pos, Player player) {
        return false;
    }
}
