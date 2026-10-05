package de.kronwerke.core.obelisk;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A piece of the obelisk that is not the core: the plinth blocks of the 5x5 base and the
 * shaft segments of the pillar. Unbreakable outside creative mode, placed by
 * {@link ObeliskStructure}, and every one of them takes deposits like the core.
 */
public class ObeliskPartBlock extends Block {
    public static final MapCodec<ObeliskPartBlock> CODEC = simpleCodec(p -> new ObeliskPartBlock(p, Shapes.block()));

    private final VoxelShape shape;

    public ObeliskPartBlock(Properties properties, VoxelShape shape) {
        super(properties);
        this.shape = shape;
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shape;
    }

    @Override
    public boolean canHarvestBlock(BlockState state, BlockGetter level, BlockPos pos, Player player) {
        return player.isCreative();
    }
}
