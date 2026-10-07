package de.kronwerke.core.obelisk;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** The crystal on top of the pillar, two blocks tall, with the beam above it. */
public class ObeliskTopBlock extends BaseEntityBlock {
    public static final MapCodec<ObeliskTopBlock> CODEC = simpleCodec(ObeliskTopBlock::new);

    private static final VoxelShape SHAPE = Shapes.or(
            Block_box(2, 0, 2, 14, 12, 14),
            Block_box(3, 12, 3, 13, 22, 13));

    private static VoxelShape Block_box(double x0, double y0, double z0, double x1, double y1, double z1) {
        return net.minecraft.world.level.block.Block.box(x0, y0, z0, x1, y1, z1);
    }

    public ObeliskTopBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(ObeliskPartBlock.HIDDEN, false));
    }

    @Override
    protected void createBlockStateDefinition(net.minecraft.world.level.block.state.StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(ObeliskPartBlock.HIDDEN);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public void animateTick(BlockState state, net.minecraft.world.level.Level level, BlockPos pos, net.minecraft.util.RandomSource random) {
        // sparks drift out of the crystal and a few motes fall slowly along the tip
        if (random.nextInt(2) == 0) {
            double a = random.nextDouble() * Math.PI * 2;
            double r = 0.15 + random.nextDouble() * 0.25;
            level.addParticle(net.minecraft.core.particles.ParticleTypes.END_ROD,
                    pos.getX() + 0.5 + Math.cos(a) * r, pos.getY() + 1.7 + random.nextDouble() * 1.0, pos.getZ() + 0.5 + Math.sin(a) * r,
                    Math.cos(a) * 0.01, 0.005 + random.nextDouble() * 0.01, Math.sin(a) * 0.01);
        }
        if (random.nextInt(6) == 0) {
            level.addParticle(net.minecraft.core.particles.ParticleTypes.GLOW,
                    pos.getX() + 0.2 + random.nextDouble() * 0.6, pos.getY() + 1.2 + random.nextDouble() * 0.6, pos.getZ() + 0.2 + random.nextDouble() * 0.6,
                    0, -0.01, 0);
        }
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        // hidden while the rite tears the obelisk apart; the crystal above it is drawn by the block entity all the same
        return state.getValue(ObeliskPartBlock.HIDDEN) ? RenderShape.ENTITYBLOCK_ANIMATED : RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ObeliskTopBlockEntity(pos, state);
    }

    @Override
    public boolean canHarvestBlock(BlockState state, BlockGetter level, BlockPos pos, Player player) {
        return player.isCreative();
    }
}
