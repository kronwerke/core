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
    public void animateTick(BlockState state, net.minecraft.world.level.Level level, BlockPos pos, net.minecraft.util.RandomSource random) {
        // the rune bands let a few glowing motes rise from their faces towards the crystal
        if (this != ObeliskBlocks.OBELISK_RUNES.get() || random.nextInt(5) != 0) return;
        net.minecraft.core.Direction d = net.minecraft.core.Direction.Plane.HORIZONTAL.getRandomDirection(random);
        if (!level.getBlockState(pos.relative(d)).isAir()) return;
        double x = pos.getX() + 0.5 + d.getStepX() * 0.56 + (d.getStepX() == 0 ? random.nextDouble() * 0.8 - 0.4 : 0);
        double z = pos.getZ() + 0.5 + d.getStepZ() * 0.56 + (d.getStepZ() == 0 ? random.nextDouble() * 0.8 - 0.4 : 0);
        level.addParticle(net.minecraft.core.particles.ParticleTypes.GLOW, x, pos.getY() + random.nextDouble(), z,
                d.getStepX() * 0.004, 0.02 + random.nextDouble() * 0.02, d.getStepZ() * 0.004);
    }

    @Override
    public boolean canHarvestBlock(BlockState state, BlockGetter level, BlockPos pos, Player player) {
        return player.isCreative();
    }
}
