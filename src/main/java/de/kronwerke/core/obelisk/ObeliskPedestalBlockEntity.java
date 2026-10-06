package de.kronwerke.core.obelisk;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * One of the four pedestals around the obelisk. It shows the last item that went in for its
 * pillar (or for any pillar, on the fourth one), with two lines of text above it: the pillar
 * and its progress, and who gave how much.
 */
public class ObeliskPedestalBlockEntity extends BlockEntity {
    private ItemStack item = ItemStack.EMPTY;
    private String title = "";
    private String line = "";
    private long changedAt;
    /** the gauge: which pillar this pedestal stands for (-1 for the pedestal of the last gift), how full it is, whether it is the one furthest behind */
    private int pillar = -1, percent;
    private boolean lowest;

    public ObeliskPedestalBlockEntity(BlockPos pos, BlockState state) {
        super(ObeliskBlocks.OBELISK_PEDESTAL_ENTITY.get(), pos, state);
    }

    public ItemStack item() {
        return item;
    }

    public String title() {
        return title;
    }

    public String line() {
        return line;
    }

    public long changedAt() {
        return changedAt;
    }

    public int pillar() {
        return pillar;
    }

    public int percent() {
        return percent;
    }

    public boolean lowest() {
        return lowest;
    }

    /** The gauge above the plate: the pillar's fill as a column of light, the weakest pillar flickering. */
    public void gauge(int pillar, int percent, boolean lowest) {
        if (this.pillar == pillar && this.percent == percent && this.lowest == lowest) return;
        this.pillar = pillar;
        this.percent = percent;
        this.lowest = lowest;
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    /** Sets what the pedestal shows; only sends an update when something changed. */
    public void show(ItemStack stack, String title, String line) {
        boolean newItem = stack != null && !ItemStack.isSameItemSameComponents(stack, item);
        boolean changed = newItem || !this.title.equals(title) || !this.line.equals(line);
        if (!changed) return;
        if (stack != null) item = stack.copyWithCount(1);
        this.title = title;
        this.line = line;
        if (newItem && level != null) changedAt = level.getGameTime();
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        if (!item.isEmpty()) tag.put("item", item.save(provider));
        tag.putString("title", title);
        tag.putString("line", line);
        tag.putLong("changedAt", changedAt);
        tag.putInt("pillar", pillar);
        tag.putInt("percent", percent);
        tag.putBoolean("lowest", lowest);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        item = tag.contains("item") ? ItemStack.parseOptional(provider, tag.getCompound("item")) : ItemStack.EMPTY;
        title = tag.getString("title");
        line = tag.getString("line");
        changedAt = tag.getLong("changedAt");
        pillar = tag.contains("pillar") ? tag.getInt("pillar") : -1;
        percent = tag.getInt("percent");
        lowest = tag.getBoolean("lowest");
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
