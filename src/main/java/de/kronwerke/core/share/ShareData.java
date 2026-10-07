package de.kronwerke.core.share;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * What must survive a restart so that nothing is lost and nothing doubles: what this server gave
 * and has no answer for yet, what came back and does not fit yet, and how far it has taken in what
 * each other server gave. Saved with the world, so a reset world starts over with a new epoch.
 */
public final class ShareData extends SavedData {
    public static final String NAME = "kronwerke_share";
    public static final Factory<ShareData> FACTORY = new Factory<>(ShareData::new, ShareData::load, null);
    /** Answers kept per sender, for gives that arrive twice because the first answer got lost. */
    private static final int KEEP = 256;
    /** Sender runs remembered, the most recent ones. */
    private static final int SENDERS = 32;

    /** Something given to another server and not answered yet. */
    public static final class Entry {
        final String peer, peerEpoch, run, layer, tag;
        final long seq;
        final JsonObject data;
        long sentAt;

        Entry(String peer, String peerEpoch, String run, long seq, String layer, String tag, JsonObject data) {
            this.peer = peer;
            this.peerEpoch = peerEpoch;
            this.run = run;
            this.seq = seq;
            this.layer = layer;
            this.tag = tag;
            this.data = data;
        }
    }

    /** Something that came back and found no room yet. */
    public record Stuck(String layer, JsonObject data) {}

    String epoch;
    /**
     * This start of the server. Gives are numbered per run, so that a server that crashed and came
     * back with an older save never reuses a number the other side already took.
     */
    final String run = Long.toHexString(ThreadLocalRandom.current().nextLong() & 0xffffffffffffL);
    private final Map<String, Long> nextSeq = new HashMap<>();
    final List<Entry> outbox = new ArrayList<>();
    final List<Stuck> stuck = new ArrayList<>();
    final Map<String, Long> last = new LinkedHashMap<>();
    final Map<String, LinkedHashMap<Long, String>> answers = new HashMap<>();

    public ShareData() {
        epoch = Long.toHexString(ThreadLocalRandom.current().nextLong() & 0xffffffffffffL);
        // a new world's epoch must be saved before anyone relies on it
        setDirty();
    }

    public static ShareData get(MinecraftServer s) {
        return s.overworld().getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    long next(String peer, String peerEpoch) {
        long n = nextSeq.getOrDefault(peer + "@" + peerEpoch, 1L);
        nextSeq.put(peer + "@" + peerEpoch, n + 1);
        return n;
    }

    /** sender is the sender's epoch and run. */
    void answered(String sender, long seq, JsonObject rest) {
        last.remove(sender);
        last.put(sender, seq);
        LinkedHashMap<Long, String> a = answers.computeIfAbsent(sender, k -> new LinkedHashMap<>());
        a.put(seq, rest == null ? "" : rest.toString());
        while (a.size() > KEEP) a.remove(a.keySet().iterator().next());
        // runs of the other servers that are long over
        while (last.size() > SENDERS) {
            String oldest = last.keySet().iterator().next();
            last.remove(oldest);
            answers.remove(oldest);
        }
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putString("epoch", epoch);
        ListTag out = new ListTag();
        for (Entry e : outbox) {
            CompoundTag c = new CompoundTag();
            c.putString("peer", e.peer);
            c.putString("peerEpoch", e.peerEpoch);
            c.putString("run", e.run);
            c.putLong("seq", e.seq);
            c.putString("layer", e.layer);
            c.putString("tag", e.tag);
            c.putString("data", e.data.toString());
            out.add(c);
        }
        tag.put("outbox", out);
        ListTag st = new ListTag();
        for (Stuck s : stuck) {
            CompoundTag c = new CompoundTag();
            c.putString("layer", s.layer());
            c.putString("data", s.data().toString());
            st.add(c);
        }
        tag.put("stuck", st);
        CompoundTag l = new CompoundTag();
        last.forEach(l::putLong);
        tag.put("last", l);
        CompoundTag an = new CompoundTag();
        answers.forEach((k, v) -> {
            CompoundTag c = new CompoundTag();
            v.forEach((seq, rest) -> c.putString(Long.toString(seq), rest));
            an.put(k, c);
        });
        tag.put("answers", an);
        return tag;
    }

    public static ShareData load(CompoundTag tag, HolderLookup.Provider provider) {
        ShareData d = new ShareData();
        if (!tag.getString("epoch").isEmpty()) d.epoch = tag.getString("epoch");
        for (Tag t : tag.getList("outbox", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            d.outbox.add(new Entry(c.getString("peer"), c.getString("peerEpoch"), c.getString("run"), c.getLong("seq"), c.getString("layer"), c.getString("tag"),
                    JsonParser.parseString(c.getString("data")).getAsJsonObject()));
        }
        for (Tag t : tag.getList("stuck", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            d.stuck.add(new Stuck(c.getString("layer"), JsonParser.parseString(c.getString("data")).getAsJsonObject()));
        }
        CompoundTag l = tag.getCompound("last");
        for (String k : l.getAllKeys()) d.last.put(k, l.getLong(k));
        CompoundTag an = tag.getCompound("answers");
        for (String k : an.getAllKeys()) {
            CompoundTag c = an.getCompound(k);
            LinkedHashMap<Long, String> a = new LinkedHashMap<>();
            c.getAllKeys().stream().mapToLong(Long::parseLong).sorted().forEach(seq -> a.put(seq, c.getString(Long.toString(seq))));
            d.answers.put(k, a);
        }
        return d;
    }
}
