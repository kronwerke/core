package de.kronwerke.core.portal;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import org.joml.Vector3f;

import java.util.Map;

/**
 * The Grubenrahmen: an iron cage around a stone shell around a core of light. The core sleeps
 * until the block holds a portal; then it burns. Lighting a portal sets off a tick in every
 * frame block, later the further it is from the gem, so that the light runs around the frame.
 * <p>
 * Frames next to each other join: the cage runs along the outside of the whole group and has
 * no bars where two frames meet, as the flat picture did before with connected textures. The
 * block knows on which sides another frame stands (six states, kept up by the neighbours'
 * updates), and the model is put together from parts by them.
 */
public class FrameBlock extends Block {
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    private static final Map<Direction, BooleanProperty> SIDE = PipeBlock.PROPERTY_BY_DIRECTION;
    private static final DustParticleOptions BLUE = new DustParticleOptions(new Vector3f(0.52f, 0.84f, 1.0f), 1.0f);

    public FrameBlock(BlockBehaviour.Properties p) {
        super(p);
        BlockState d = stateDefinition.any().setValue(LIT, false);
        for (BooleanProperty side : SIDE.values()) d = d.setValue(side, false);
        registerDefaultState(d);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(LIT, BlockStateProperties.NORTH, BlockStateProperties.EAST, BlockStateProperties.SOUTH, BlockStateProperties.WEST,
                BlockStateProperties.UP, BlockStateProperties.DOWN);
    }

    /** The state with the sides set to where other frames stand. */
    private BlockState joined(BlockState state, LevelReader level, BlockPos pos) {
        for (Direction d : Direction.values()) state = state.setValue(SIDE.get(d), level.getBlockState(pos.relative(d)).is(this));
        return state;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return joined(defaultBlockState(), ctx.getLevel(), ctx.getClickedPos());
    }

    /** Put there by anything but a player's hand (a command, the world's own portal): the sides are worked out here. */
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
        super.onPlace(state, level, pos, old, moved);
        if (old.is(this)) return;
        BlockState j = joined(state, level, pos);
        if (j != state) level.setBlock(pos, j, 2 | 16);
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

    /** A frame came or went next to it; and when the portal next to it is gone, the core goes back to sleep. */
    @Override
    protected BlockState updateShape(BlockState state, Direction dir, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        state = state.setValue(SIDE.get(dir), neighbor.is(this));
        if (state.getValue(LIT) && !holds(level, pos)) return state.setValue(LIT, false);
        return state;
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
