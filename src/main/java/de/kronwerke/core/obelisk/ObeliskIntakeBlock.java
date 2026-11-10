package de.kronwerke.core.obelisk;

import com.mojang.serialization.MapCodec;
import de.kronwerke.core.Text;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The intake: a small pedestal that belongs to the player who placed it. Pipes push into
 * it, the items go into the goal under that player's name. It only works next to the
 * obelisk (feederRadius), and a right click tells whose it is and whether it is connected.
 */
public class ObeliskIntakeBlock extends BaseEntityBlock {
    public static final MapCodec<ObeliskIntakeBlock> CODEC = simpleCodec(ObeliskIntakeBlock::new);
    private static final VoxelShape SHAPE = Shapes.or(Block_box(0, 0, 0, 16, 4, 16), Block_box(2, 4, 2, 14, 12, 14), Block_box(1, 12, 1, 15, 16, 15));

    private static VoxelShape Block_box(double x0, double y0, double z0, double x1, double y1, double z1) {
        return net.minecraft.world.level.block.Block.box(x0, y0, z0, x1, y1, z1);
    }

    public ObeliskIntakeBlock(Properties properties) {
        super(properties);
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
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ObeliskIntakeBlockEntity(pos, state);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        // a machine's fake player owns nothing: the first real player to click it takes it
        if (level.isClientSide() || !(placer instanceof Player p) || p instanceof net.neoforged.neoforge.common.util.FakePlayer) return;
        if (level.getBlockEntity(pos) instanceof ObeliskIntakeBlockEntity be) {
            be.setOwner(p.getUUID(), p.getGameProfile().getName());
            if (be.connected()) {
                p.sendSystemMessage(Text.t("obelisk.intake", "Dein Zubringer steht. Alles, was hier reinkommt, zählt für dich.").withStyle(ChatFormatting.GREEN));
            } else {
                p.sendSystemMessage(Text.t("obelisk.intake_far_r", "Der Zubringer ist zu weit vom Obelisken weg. Er muss höchstens %s Blöcke vom Obelisken entfernt stehen.",
                        de.kronwerke.core.config.KronwerkeConfig.FEEDER_RADIUS.get()).withStyle(ChatFormatting.RED));
            }
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (level.getBlockEntity(pos) instanceof ObeliskIntakeBlockEntity be) {
            if (be.owner() == null && !(player instanceof net.neoforged.neoforge.common.util.FakePlayer)) {
                // set by a structure, a command or a machine: whoever clicks it first owns it
                be.setOwner(player.getUUID(), player.getGameProfile().getName());
                player.sendSystemMessage(Text.t("obelisk.intake_claimed", "Der Zubringer hatte keinen Besitzer, jetzt gehört er dir. Alles, was hier reinkommt, zählt für dich.")
                        .withStyle(ChatFormatting.GREEN));
                return InteractionResult.CONSUME;
            }
            var gm = de.kronwerke.core.goal.GoalManager.get();
            boolean held = gm.activeGoals().stream().anyMatch(gm::isHeld);
            Object who = be.ownerName().isEmpty() ? Text.t("obelisk.intake_nobody", "niemandem") : be.ownerName();
            Component state2 = !be.connected() ? Text.t("obelisk.intake_far", "nicht verbunden: zu weit weg")
                    : held ? Text.t("obelisk.intake_held", "verbunden, aber das Ziel wartet gerade auf das Event") : Text.t("obelisk.intake_ok", "mit dem Obelisken verbunden");
            player.sendSystemMessage(Text.t("obelisk.intake_info", "Zubringer von %s, %s.", who, state2).withStyle(ChatFormatting.GRAY));
        }
        return InteractionResult.CONSUME;
    }
}
