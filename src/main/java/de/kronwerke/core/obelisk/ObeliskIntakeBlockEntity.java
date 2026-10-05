package de.kronwerke.core.obelisk;

import de.kronwerke.core.config.KronwerkeConfig;
import de.kronwerke.core.goal.GoalManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.UUID;

/**
 * The item side of the obelisk. Whatever a pipe, hopper or belt pushes in goes straight
 * into the running goal under the name of the player who placed the intake. Nothing is
 * stored: what the goal does not want is refused, so the pipe keeps it.
 */
public class ObeliskIntakeBlockEntity extends BlockEntity {
    private UUID owner;
    private String ownerName = "";

    public ObeliskIntakeBlockEntity(BlockPos pos, BlockState state) {
        super(ObeliskBlocks.OBELISK_INTAKE_ENTITY.get(), pos, state);
    }

    public void setOwner(UUID id, String name) {
        this.owner = id;
        this.ownerName = name;
        setChanged();
    }

    public UUID owner() {
        return owner;
    }

    public String ownerName() {
        return ownerName;
    }

    /** Connected to the obelisk: within feederRadius of its core, in its dimension. */
    public boolean connected() {
        ObeliskData d = Obelisk.get().data();
        if (level == null || !d.isSet() || !level.dimension().location().toString().equals(d.dimension())) return false;
        BlockPos o = d.pos();
        int r = KronwerkeConfig.FEEDER_RADIUS.get();
        return Math.abs(worldPosition.getX() - o.getX()) <= r + 2 && Math.abs(worldPosition.getY() - o.getY()) <= r + 2 && Math.abs(worldPosition.getZ() - o.getZ()) <= r + 2;
    }

    public final IItemHandler handler = new IItemHandler() {
        @Override
        public int getSlots() {
            return 1;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (stack.isEmpty() || owner == null || !connected() || level == null || level.isClientSide()) return stack;
            GoalManager gm = GoalManager.get();
            long take = simulate ? gm.wouldTake(stack) : gm.deposit(owner, Component.literal(ownerName), stack.copy(), true);
            if (take <= 0) return stack;
            ItemStack rest = stack.copy();
            rest.shrink((int) take);
            return rest;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 64;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return GoalManager.get().wouldTake(stack) > 0;
        }
    };

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (owner != null) tag.putUUID("owner", owner);
        tag.putString("ownerName", ownerName);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        owner = tag.hasUUID("owner") ? tag.getUUID("owner") : null;
        ownerName = tag.getString("ownerName");
    }
}
