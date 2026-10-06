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
 * server tells it how far the running goal is (0 to 100), the mood of the goal, how many
 * stages are done (the tier, which decides the size of the crystal and its retinue), when
 * the last deposit happened and how big it was, and when a completion rite began, so the
 * renderer can play the whole sequence from that one timestamp.
 */
public class ObeliskTopBlockEntity extends BlockEntity {
    public static final int MOOD_RUNNING = 0, MOOD_HELD = 1, MOOD_DONE = 2, MOOD_IDLE = 3, MOOD_ASLEEP = 4;

    private int percent;
    private int mood = MOOD_IDLE;
    private int tier;
    private long lastDeposit;
    private float flashStrength;
    private long rite;

    public ObeliskTopBlockEntity(BlockPos pos, BlockState state) {
        super(ObeliskBlocks.OBELISK_TOP_ENTITY.get(), pos, state);
    }

    public int percent() {
        return percent;
    }

    public int mood() {
        return mood;
    }

    public int tier() {
        return tier;
    }

    /** Game time of the last deposit, for the flare. */
    public long lastDeposit() {
        return lastDeposit;
    }

    /** How big the last deposit was, 0 to 1. */
    public float flashStrength() {
        return flashStrength;
    }

    /** Game time the completion rite started, 0 when none runs. */
    public long rite() {
        return rite;
    }

    /** Server side: sets the state the client should draw; only sends when something changed. */
    public void show(int percent, int mood, int tier) {
        if (this.percent == percent && this.mood == mood && this.tier == tier) return;
        this.percent = percent;
        this.mood = mood;
        this.tier = tier;
        sync();
    }

    /** Server side: a deposit just happened, the crystal flares by strength (0 to 1). */
    public void flash(float strength) {
        if (level == null) return;
        lastDeposit = level.getGameTime();
        flashStrength = strength;
        sync();
    }

    /** Server side: the completion rite begins now (or ends, with 0). */
    public void rite(long start) {
        rite = start;
        sync();
    }

    private void sync() {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        tag.putInt("percent", percent);
        tag.putInt("mood", mood);
        tag.putInt("tier", tier);
        tag.putLong("lastDeposit", lastDeposit);
        tag.putFloat("flash", flashStrength);
        tag.putLong("rite", rite);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        percent = tag.getInt("percent");
        mood = tag.contains("mood") ? tag.getInt("mood") : MOOD_IDLE;
        tier = tag.getInt("tier");
        lastDeposit = tag.getLong("lastDeposit");
        flashStrength = tag.contains("flash") ? tag.getFloat("flash") : 0.6f;
        rite = tag.getLong("rite");
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
