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

    /** Where a player who logged out on a dimension server goes on with their next login. */
    public record Resume(String server, Back at) {}

    private final Map<UUID, String> where = new HashMap<>();
    private final Map<UUID, Back> back = new HashMap<>();
    private final Map<UUID, Resume> resume = new HashMap<>();

    private MinecraftServer server;

    public static AwayData get(MinecraftServer s) {
        AwayData d = s.overworld().getDataStorage().computeIfAbsent(FACTORY, NAME);
        d.server = s;
        return d;
    }

    /**
     * Written at once on every change, not only at the next autosave: if main crashed in
     * between it would forget who is away, and the side world's reports for them would be
     * ignored as coming from a stranger (review R3-01). The file is tiny.
     */
    private void changed() {
        setDirty();
        if (server == null) return;
        try {
            java.io.File f = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                    .resolve("data").resolve(NAME + ".dat").toFile();
            save(f, server.registryAccess());
        } catch (RuntimeException e) {
            de.kronwerke.core.KronwerkeCore.LOGGER.warn("Could not save who is away: {}", e.toString());
        }
    }

    public void leave(UUID id, String server, JsonObject b) {
        where.put(id, server);
        if (b != null) {
            back.put(id, new Back(Travel.str(b, "dim"), b.get("x").getAsDouble(), b.get("y").getAsDouble(), b.get("z").getAsDouble(), b.get("yaw").getAsFloat()));
        } else {
            back.remove(id);
        }
        resume.remove(id);
        changed();
    }

    public Back back(UUID id) {
        return where.containsKey(id) ? back.get(id) : null;
    }

    public String where(UUID id) {
        return where.get(id);
    }

    public void home(UUID id) {
        if (where.remove(id) != null) changed();
    }

    public void resume(UUID id, String server, Back at) {
        if (at == null) return;
        resume.put(id, new Resume(server, at));
        changed();
    }

    /** The resume for this login, once. */
    public Resume takeResume(UUID id) {
        Resume r = resume.remove(id);
        if (r != null) changed();
        return r;
    }

    private static CompoundTag write(Back b) {
        CompoundTag e = new CompoundTag();
        e.putString("dim", b.dim());
        e.putDouble("x", b.x());
        e.putDouble("y", b.y());
        e.putDouble("z", b.z());
        e.putFloat("yaw", b.yaw());
        return e;
    }

    private static Back read(CompoundTag e) {
        return new Back(e.getString("dim"), e.getDouble("x"), e.getDouble("y"), e.getDouble("z"), e.getFloat("yaw"));
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
        CompoundTag r = new CompoundTag();
        resume.forEach((k, v) -> {
            CompoundTag e = write(v.at());
            e.putString("server", v.server());
            r.put(k.toString(), e);
        });
        tag.put("resume", r);
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
        CompoundTag r = tag.getCompound("resume");
        for (String k : r.getAllKeys()) {
            CompoundTag e = r.getCompound(k);
            d.resume.put(UUID.fromString(k), new Resume(e.getString("server"), read(e)));
        }
        return d;
    }
}
