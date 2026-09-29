package de.kronwerke.core.obelisk;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Where the obelisk stands and which containers next to it feed it, with the player each
 * feeder counts for. Feeders are always in the obelisk's dimension.
 */
public class ObeliskData extends SavedData {
    public static final String NAME = "kronwerke_obelisk";
    public static final Factory<ObeliskData> FACTORY = new Factory<>(ObeliskData::new, ObeliskData::load, null);

    private String dimension;
    private BlockPos pos;
    private final Map<BlockPos, UUID> feeders = new LinkedHashMap<>();

    public boolean isSet() {
        return pos != null && dimension != null;
    }

    public String dimension() {
        return dimension;
    }

    public BlockPos pos() {
        return pos;
    }

    public void set(String dimension, BlockPos pos) {
        this.dimension = dimension;
        this.pos = pos.immutable();
        feeders.clear();
        setDirty();
    }

    public void clear() {
        dimension = null;
        pos = null;
        feeders.clear();
        setDirty();
    }

    public Map<BlockPos, UUID> feeders() {
        return feeders;
    }

    public UUID feeder(BlockPos p) {
        return feeders.get(p);
    }

    public void addFeeder(BlockPos p, UUID owner) {
        feeders.put(p.immutable(), owner);
        setDirty();
    }

    public boolean removeFeeder(BlockPos p) {
        boolean had = feeders.remove(p) != null;
        if (had) setDirty();
        return had;
    }

    public static ObeliskData load(CompoundTag tag, HolderLookup.Provider provider) {
        ObeliskData d = new ObeliskData();
        if (tag.contains("dimension")) {
            d.dimension = tag.getString("dimension");
            d.pos = BlockPos.of(tag.getLong("pos"));
        }
        ListTag list = tag.getList("feeders", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag f = list.getCompound(i);
            d.feeders.put(BlockPos.of(f.getLong("pos")), f.getUUID("owner"));
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        if (isSet()) {
            tag.putString("dimension", dimension);
            tag.putLong("pos", pos.asLong());
        }
        ListTag list = new ListTag();
        feeders.forEach((p, owner) -> {
            CompoundTag f = new CompoundTag();
            f.putLong("pos", p.asLong());
            f.putUUID("owner", owner);
            list.add(f);
        });
        tag.put("feeders", list);
        return tag;
    }
}
