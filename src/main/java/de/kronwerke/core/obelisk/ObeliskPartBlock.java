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

    /** Lit while the obelisk is awake; the sleeping stone puts its lanterns, embers and glows out. */
    public static final net.minecraft.world.level.block.state.properties.BooleanProperty LIT = net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT;
    /** Not drawn while the rite tears the obelisk apart; the clients draw flying copies in its place. */
    public static final net.minecraft.world.level.block.state.properties.BooleanProperty HIDDEN = net.minecraft.world.level.block.state.properties.BooleanProperty.create("hidden");

    private final VoxelShape shape;

    public ObeliskPartBlock(Properties properties, VoxelShape shape) {
        super(properties);
        this.shape = shape;
        registerDefaultState(stateDefinition.any().setValue(LIT, true).setValue(HIDDEN, false));
    }

    @Override
    protected void createBlockStateDefinition(net.minecraft.world.level.block.state.StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT, HIDDEN);
    }

    @Override
    protected net.minecraft.world.level.block.RenderShape getRenderShape(BlockState state) {
        return state.getValue(HIDDEN) ? net.minecraft.world.level.block.RenderShape.INVISIBLE : net.minecraft.world.level.block.RenderShape.MODEL;
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
        if (!state.getValue(LIT) || state.getValue(HIDDEN)) return;
        double x = pos.getX() + random.nextDouble(), z = pos.getZ() + random.nextDouble();
        if (this == ObeliskBlocks.OBELISK_RUNES.get()) {
            // the rune bands let a few glowing motes rise from their faces towards the crystal
            if (random.nextInt(5) != 0) return;
            net.minecraft.core.Direction d = net.minecraft.core.Direction.Plane.HORIZONTAL.getRandomDirection(random);
            if (!level.getBlockState(pos.relative(d)).isAir()) return;
            double rx = pos.getX() + 0.5 + d.getStepX() * 0.56 + (d.getStepX() == 0 ? random.nextDouble() * 0.8 - 0.4 : 0);
            double rz = pos.getZ() + 0.5 + d.getStepZ() * 0.56 + (d.getStepZ() == 0 ? random.nextDouble() * 0.8 - 0.4 : 0);
            var type = random.nextInt(3) == 0 ? KwParticles.RUNE.get() : net.minecraft.core.particles.ParticleTypes.GLOW;
            level.addParticle(type, rx, pos.getY() + random.nextDouble(), rz, d.getStepX() * 0.004, 0.02 + random.nextDouble() * 0.02, d.getStepZ() * 0.004);
        } else if (this == ObeliskBlocks.OBELISK_EMBER.get()) {
            // ember stones breathe a little smoke and the odd spark
            if (!level.getBlockState(pos.above()).isAir()) return;
            if (random.nextInt(4) == 0) level.addParticle(net.minecraft.core.particles.ParticleTypes.SMOKE, x, pos.getY() + 1.05, z, 0, 0.01, 0);
            if (random.nextInt(9) == 0) level.addParticle(net.minecraft.core.particles.ParticleTypes.SMALL_FLAME, x, pos.getY() + 1.05, z, 0, 0.02, 0);
        } else if (this == ObeliskBlocks.OBELISK_LANTERN.get()) {
            if (random.nextInt(3) == 0) {
                net.minecraft.core.Direction d = net.minecraft.core.Direction.getRandom(random);
                level.addParticle(net.minecraft.core.particles.ParticleTypes.GLOW, pos.getX() + 0.5 + d.getStepX() * 0.6, pos.getY() + 0.5 + d.getStepY() * 0.6, pos.getZ() + 0.5 + d.getStepZ() * 0.6, 0, 0.005, 0);
            }
        } else if (this == ObeliskBlocks.OBELISK_CROWN.get()) {
            if (random.nextInt(6) == 0) level.addParticle(net.minecraft.core.particles.ParticleTypes.END_ROD, x, pos.getY() + 1.1, z, 0, 0.01, 0);
        } else if (this == ObeliskBlocks.OBELISK_ARCH.get()) {
            if (random.nextInt(10) == 0) level.addParticle(net.minecraft.core.particles.ParticleTypes.PORTAL, x, pos.getY() + random.nextDouble(), z, (random.nextDouble() - 0.5) * 0.3, -0.2, (random.nextDouble() - 0.5) * 0.3);
        } else if (this == ObeliskBlocks.OBELISK_SHARD.get()) {
            if (random.nextInt(8) == 0) level.addParticle(net.minecraft.core.particles.ParticleTypes.END_ROD, x + (random.nextDouble() - 0.5) * 1.1, pos.getY() + 0.5 + (random.nextDouble() - 0.5) * 1.1, z + (random.nextDouble() - 0.5) * 1.1, 0, 0.015, 0);
        } else if (this == ObeliskBlocks.OBELISK_BRASS.get()) {
            if (random.nextInt(40) == 0 && level.getBlockState(pos.above()).isAir()) level.addParticle(net.minecraft.core.particles.ParticleTypes.WAX_OFF, x, pos.getY() + 1.05, z, 0, 0, 0);
        }
    }

    @Override
    public boolean canHarvestBlock(BlockState state, BlockGetter level, BlockPos pos, Player player) {
        return player.isCreative();
    }
}
