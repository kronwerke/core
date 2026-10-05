package de.kronwerke.core.obelisk;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * The anchor of the leaderboard wall: its size and the lines carved into it. The first line
 * is the heading, the second the subheading; every other line is "left TAB right", drawn
 * with the right part aligned to the right edge.
 */
public class ObeliskBoardBlockEntity extends BlockEntity {
    private int width = 7, height = 4;
    private List<String> lines = new ArrayList<>();

    public ObeliskBoardBlockEntity(BlockPos pos, BlockState state) {
        super(ObeliskBlocks.OBELISK_BOARD_ENTITY.get(), pos, state);
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public List<String> lines() {
        return lines;
    }

    public void setSize(int width, int height) {
        this.width = width;
        this.height = height;
        setChanged();
    }

    public void show(List<String> lines) {
        if (lines.equals(this.lines)) return;
        this.lines = new ArrayList<>(lines);
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        tag.putInt("width", width);
        tag.putInt("height", height);
        ListTag l = new ListTag();
        for (String s : lines) l.add(StringTag.valueOf(s));
        tag.put("lines", l);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        if (tag.contains("width")) width = tag.getInt("width");
        if (tag.contains("height")) height = tag.getInt("height");
        lines = new ArrayList<>();
        ListTag l = tag.getList("lines", Tag.TAG_STRING);
        for (int i = 0; i < l.size(); i++) lines.add(l.getString(i));
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
