package de.kronwerke.core.obelisk;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The top of the obelisk. The client draws the floating crystal and the beam from it; the
 * server tells it how far the running goal is (0 to 100) and whether the goal is held or
 * done, so the crystal and the beam change colour with the progress.
 */
public class ObeliskTopBlockEntity extends BlockEntity {
    private int percent;
    /** 0 running, 1 held before the event, 2 done, 3 nothing active */
    private int mood = 3;
    private long lastDeposit;

    public ObeliskTopBlockEntity(BlockPos pos, BlockState state) {
        super(ObeliskBlocks.OBELISK_TOP_ENTITY.get(), pos, state);
    }

    public int percent() {
        return percent;
    }

    public int mood() {
        return mood;
    }

    /** Game time of the last deposit, for the flash. */
    public long lastDeposit() {
        return lastDeposit;
    }

    /** Server side: sets the state the client should draw; only sends when something changed. */
    public void show(int percent, int mood) {
        if (this.percent == percent && this.mood == mood) return;
        this.percent = percent;
        this.mood = mood;
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    /** Server side: a deposit just happened, the crystal flashes. */
    public void flash() {
        if (level == null) return;
        lastDeposit = level.getGameTime();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        tag.putInt("percent", percent);
        tag.putInt("mood", mood);
        tag.putLong("lastDeposit", lastDeposit);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        percent = tag.getInt("percent");
        mood = tag.contains("mood") ? tag.getInt("mood") : 3;
        lastDeposit = tag.getLong("lastDeposit");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider provider) {
        return saveWithoutMetadata(provider);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
