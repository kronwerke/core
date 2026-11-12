package de.kronwerke.core.portal;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Vector3f;

/** The portal's surface: like the Nether's, blue with gold sparks, and the way to the other world. */
public class MinePortalBlock extends Block {
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
    /** How far the surface has formed after the lighting: 0 a thin veil, 1 half, 2 whole and open. */
    public static final IntegerProperty STAGE = IntegerProperty.create("stage", 0, 2);
    public static final int WHOLE = 2;
    private static final VoxelShape X_SHAPE = Block.box(0, 0, 6, 16, 16, 10);
    private static final VoxelShape Z_SHAPE = Block.box(6, 0, 0, 10, 16, 16);
    private static final DustParticleOptions BLUE = new DustParticleOptions(new Vector3f(0.42f, 0.7f, 1.0f), 1.1f);
    private static final DustParticleOptions GOLD = new DustParticleOptions(new Vector3f(1.0f, 0.78f, 0.32f), 0.8f);

    public MinePortalBlock(BlockBehaviour.Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(AXIS, Direction.Axis.X).setValue(STAGE, WHOLE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(AXIS, STAGE);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return state.getValue(AXIS) == Direction.Axis.Z ? Z_SHAPE : X_SHAPE;
    }

    /** A missing frame block or portal next to it in its plane ends the portal, like the Nether's. */
    @Override
    protected BlockState updateShape(BlockState state, Direction dir, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        Direction.Axis axis = state.getValue(AXIS);
        boolean inPlane = dir.getAxis() == axis || dir.getAxis().isVertical();
        if (inPlane && !neighbor.is(this) && !neighbor.is(PortalBlocks.FRAME.get())) return Blocks.AIR.defaultBlockState();
        return super.updateShape(state, dir, neighbor, level, pos, neighborPos);
    }

    /** Only the whole surface leads anywhere. */
    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (!level.isClientSide && state.getValue(STAGE) == WHOLE) Travel.inside(entity, level, pos);
    }

    /** The surface forms: set off by the lighting, later the further in the block lies. */
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource r) {
        int stage = state.getValue(STAGE);
        if (stage >= WHOLE) return;
        level.setBlock(pos, state.setValue(STAGE, stage + 1), 2 | 16);
        level.sendParticles(BLUE, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 4, 0.35, 0.35, 0.35, 0);
        if (stage + 1 < WHOLE) level.scheduleTick(pos, this, 5);
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return ItemStack.EMPTY;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource r) {
        if (r.nextInt(120) == 0) {
            level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, SoundEvents.PORTAL_AMBIENT, SoundSource.BLOCKS,
                    0.35f, 0.55f + r.nextFloat() * 0.1f, false);
        }
        boolean x = state.getValue(AXIS) == Direction.Axis.X;
        if (state.getValue(STAGE) < WHOLE) {
            // while it forms it crackles
            for (int i = 0; i < 2; i++) {
                level.addParticle(ParticleTypes.ELECTRIC_SPARK, pos.getX() + (x ? r.nextDouble() : 0.5), pos.getY() + r.nextDouble(),
                        pos.getZ() + (x ? 0.5 : r.nextDouble()), 0, 0, 0);
            }
            return;
        }
        for (int i = 0; i < 3; i++) {
            double px = pos.getX() + (x ? r.nextDouble() : 0.5 + (r.nextDouble() - 0.5) * 0.3);
            double pz = pos.getZ() + (x ? 0.5 + (r.nextDouble() - 0.5) * 0.3 : r.nextDouble());
            double py = pos.getY() + r.nextDouble();
            level.addParticle(BLUE, px, py, pz, 0, 0.02 + r.nextDouble() * 0.04, 0);
        }
        if (r.nextInt(4) == 0) {
            level.addParticle(GOLD, pos.getX() + r.nextDouble(), pos.getY() + r.nextDouble(), pos.getZ() + r.nextDouble(), 0, 0.06, 0);
        }
        if (r.nextInt(10) == 0) {
            level.addParticle(ParticleTypes.END_ROD, pos.getX() + r.nextDouble(), pos.getY() + 0.1, pos.getZ() + r.nextDouble(), 0, 0.03, 0);
        }
    }
}
