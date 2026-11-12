package de.kronwerke.core.portal;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import org.joml.Vector3f;

/**
 * The Grubenrahmen: an iron cage around a stone shell around a core of light. The core sleeps
 * until the block holds a portal; then it burns. Lighting a portal sets off a tick in every
 * frame block, later the further it is from the gem, so that the light runs around the frame.
 */
public class FrameBlock extends Block {
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    private static final DustParticleOptions BLUE = new DustParticleOptions(new Vector3f(0.52f, 0.84f, 1.0f), 1.0f);

    public FrameBlock(BlockBehaviour.Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(LIT, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(LIT);
    }

    static boolean atPortal(LevelReader level, BlockPos pos) {
        for (Direction d : Direction.values()) {
            if (level.getBlockState(pos.relative(d)).is(PortalBlocks.PORTAL.get())) return true;
        }
        return false;
    }

    /** Part of a frame with a portal in it: next to the portal, or a corner between two blocks that are. */
    static boolean holds(LevelReader level, BlockPos pos) {
        if (atPortal(level, pos)) return true;
        int n = 0;
        for (Direction d : Direction.values()) {
            BlockPos q = pos.relative(d);
            if (level.getBlockState(q).is(PortalBlocks.FRAME.get()) && atPortal(level, q)) n++;
        }
        return n >= 2;
    }

    /** The portal next to it is gone: the core goes back to sleep. */
    @Override
    protected BlockState updateShape(BlockState state, Direction dir, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (state.getValue(LIT) && !holds(level, pos)) return state.setValue(LIT, false);
        return super.updateShape(state, dir, neighbor, level, pos, neighborPos);
    }

    /** The light of a portal being lit reaches this block. */
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource r) {
        if (state.getValue(LIT) || !holds(level, pos)) return;
        level.setBlock(pos, state.setValue(LIT, true), 2);
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.7f, 0.7f + r.nextFloat() * 0.9f);
        level.sendParticles(BLUE, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 10, 0.45, 0.45, 0.45, 0);
        level.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 2, 0.4, 0.4, 0.4, 0.02);
    }

    /** A frame that holds a portal but sleeps (a portal from before the frame could burn) wakes up by itself. */
    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return !state.getValue(LIT);
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource r) {
        if (holds(level, pos)) level.setBlock(pos, state.setValue(LIT, true), 2);
    }
}
