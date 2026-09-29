package de.kronwerke.core.slot;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Persistent storage for streamer slot allowances and the players they invited.
 * Lives in the overworld's data folder as kronwerke_slots.dat.
 */
public class SlotData extends SavedData {
    public static final String NAME = "kronwerke_slots";

    public static final Factory<SlotData> FACTORY = new Factory<>(SlotData::new, SlotData::load, null);

    /** streamer uuid -> entry */
    private final Map<UUID, StreamerEntry> streamers = new LinkedHashMap<>();

    public SlotData() {}

    public Map<UUID, StreamerEntry> streamers() {
        return streamers;
    }

    public StreamerEntry getOrCreate(UUID streamer, String name) {
        StreamerEntry e = streamers.get(streamer);
        if (e == null) {
            e = new StreamerEntry(streamer, name);
            streamers.put(streamer, e);
            setDirty();
        } else if (!e.name.equals(name)) {
            e.name = name;
            setDirty();
        }
        return e;
    }

    public StreamerEntry find(UUID streamer) {
        return streamers.get(streamer);
    }

    public StreamerEntry findByName(String name) {
        for (StreamerEntry e : streamers.values()) {
            if (e.name.equalsIgnoreCase(name)) return e;
        }
        return null;
    }

    /** Finds the streamer that invited a given player, if any. */
    public StreamerEntry findInviter(UUID invited) {
        for (StreamerEntry e : streamers.values()) {
            if (e.invited.containsKey(invited)) return e;
        }
        return null;
    }

    public static SlotData load(CompoundTag tag, HolderLookup.Provider provider) {
        SlotData data = new SlotData();
        ListTag list = tag.getList("streamers", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            StreamerEntry e = StreamerEntry.fromNbt(list.getCompound(i));
            data.streamers.put(e.uuid, e);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag list = new ListTag();
        for (StreamerEntry e : streamers.values()) list.add(e.toNbt());
        tag.put("streamers", list);
        return tag;
    }

    public static class StreamerEntry {
        public final UUID uuid;
        public String name;
        /** -1 means "use the config default". */
        public int slotOverride = -1;
        public int bonusSlots = 0;
        /** invited uuid -> last known name */
        public final Map<UUID, String> invited = new LinkedHashMap<>();

        public StreamerEntry(UUID uuid, String name) {
            this.uuid = uuid;
            this.name = name;
        }

        public int allowance(int configDefault) {
            return (slotOverride >= 0 ? slotOverride : configDefault) + bonusSlots;
        }

        public int used() {
            return invited.size();
        }

        CompoundTag toNbt() {
            CompoundTag t = new CompoundTag();
            t.putUUID("uuid", uuid);
            t.putString("name", name);
            t.putInt("slotOverride", slotOverride);
            t.putInt("bonusSlots", bonusSlots);
            ListTag inv = new ListTag();
            invited.forEach((id, n) -> {
                CompoundTag it = new CompoundTag();
                it.putUUID("uuid", id);
                it.putString("name", n);
                inv.add(it);
            });
            t.put("invited", inv);
            return t;
        }

        static StreamerEntry fromNbt(CompoundTag t) {
            StreamerEntry e = new StreamerEntry(t.getUUID("uuid"), t.getString("name"));
            e.slotOverride = t.getInt("slotOverride");
            e.bonusSlots = t.getInt("bonusSlots");
            ListTag inv = t.getList("invited", Tag.TAG_COMPOUND);
            for (int i = 0; i < inv.size(); i++) {
                CompoundTag it = inv.getCompound(i);
                e.invited.put(it.getUUID("uuid"), it.getString("name"));
            }
            return e;
        }
    }
}
