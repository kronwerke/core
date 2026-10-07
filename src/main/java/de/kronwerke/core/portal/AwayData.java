package de.kronwerke.core.portal;

import com.google.gson.JsonObject;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** main: who is on another server of the network, and where they come back (in front of their portal). */
public final class AwayData extends SavedData {
    public static final String NAME = "kronwerke_away";
    public static final Factory<AwayData> FACTORY = new Factory<>(AwayData::new, AwayData::load, null);

    public record Back(String dim, double x, double y, double z, float yaw) {}

    private final Map<UUID, String> where = new HashMap<>();
    private final Map<UUID, Back> back = new HashMap<>();

    public static AwayData get(MinecraftServer s) {
        return s.overworld().getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    public void leave(UUID id, String server, JsonObject b) {
        where.put(id, server);
        if (b != null) {
            back.put(id, new Back(Travel.str(b, "dim"), b.get("x").getAsDouble(), b.get("y").getAsDouble(), b.get("z").getAsDouble(), b.get("yaw").getAsFloat()));
        }
        setDirty();
    }

    public Back back(UUID id) {
        return where.containsKey(id) ? back.get(id) : null;
    }

    public String where(UUID id) {
        return where.get(id);
    }

    public void home(UUID id) {
        if (where.remove(id) != null) setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        CompoundTag w = new CompoundTag();
        where.forEach((k, v) -> {
            CompoundTag e = new CompoundTag();
            e.putString("server", v);
            Back b = back.get(k);
            if (b != null) {
                e.putString("dim", b.dim());
                e.putDouble("x", b.x());
                e.putDouble("y", b.y());
                e.putDouble("z", b.z());
                e.putFloat("yaw", b.yaw());
            }
            w.put(k.toString(), e);
        });
        tag.put("away", w);
        return tag;
    }

    public static AwayData load(CompoundTag tag, HolderLookup.Provider provider) {
        AwayData d = new AwayData();
        CompoundTag w = tag.getCompound("away");
        for (String k : w.getAllKeys()) {
            CompoundTag e = w.getCompound(k);
            UUID id = UUID.fromString(k);
            d.where.put(id, e.getString("server"));
            if (e.contains("dim")) d.back.put(id, new Back(e.getString("dim"), e.getDouble("x"), e.getDouble("y"), e.getDouble("z"), e.getFloat("yaw")));
        }
        return d;
    }
}
