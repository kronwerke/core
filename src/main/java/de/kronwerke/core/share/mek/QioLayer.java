package de.kronwerke.core.share.mek;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import de.kronwerke.core.KronwerkeCore;
import de.kronwerke.core.link.Role;
import de.kronwerke.core.share.Layer;
import de.kronwerke.core.share.Share;
import mekanism.api.Action;
import mekanism.api.security.SecurityMode;
import mekanism.common.content.qio.QIOFrequency;
import mekanism.common.lib.frequency.Frequency;
import mekanism.common.lib.frequency.FrequencyManager;
import mekanism.common.lib.frequency.FrequencyType;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.TagParser;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Mekanism's QIO between servers, one way: a QIO frequency on a side world with the same name,
 * owner and security as one on main sends everything stored in it home to main's frequency. What
 * main cannot take stays where it was. Before a side world stops or is reset it sends everything,
 * so a reset loses nothing. (Taking main's things out on a side world goes through AE2's bridge.)
 */
public final class QioLayer implements Layer {
    private static final int EVERY = 10, TYPES_PER_GIVE = 64;
    private static final long FRESH_NS = 5_000_000_000L;

    private final Field managers;
    /** main's frequencies that have room, as the other side last heard. */
    private Set<String> mainHas = Set.of();
    private long mainAt;
    private int ticks;
    private long moved;

    private QioLayer(Field managers) {
        this.managers = managers;
    }

    public static QioLayer create() {
        try {
            Field m = FrequencyManager.class.getDeclaredField("managers");
            m.setAccessible(true);
            return new QioLayer(m);
        } catch (ReflectiveOperationException | RuntimeException e) {
            KronwerkeCore.LOGGER.warn("Shared network: Mekanism's QIO is not shared ({})", e.toString());
            return null;
        }
    }

    @Override
    public String name() {
        return "mek.qio";
    }

    @SuppressWarnings("unchecked")
    private List<QIOFrequency> all() {
        List<QIOFrequency> out = new ArrayList<>();
        try {
            for (FrequencyManager<?> m : (Collection<FrequencyManager<?>>) managers.get(null)) {
                if (m.getType() != FrequencyType.QIO) continue;
                for (Frequency f : m.getFrequencies()) {
                    if (f instanceof QIOFrequency q && !q.isRemoved()) out.add(q);
                }
            }
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
        return out;
    }

    private static String key(Frequency f) {
        return f.getSecurity().name() + "/" + (f.getSecurity() == SecurityMode.PUBLIC || f.getOwner() == null ? "" : f.getOwner()) + "/" + f.getName();
    }

    private QIOFrequency find(String key) {
        for (QIOFrequency f : all()) if (key(f).equals(key)) return f;
        return null;
    }

    private static String mainPeer() {
        for (String p : Share.peers()) {
            var q = de.kronwerke.core.link.NetworkSync.peer(p);
            if (q != null && q.role().equals("main")) return p;
        }
        return null;
    }

    @Override
    public void tick(MinecraftServer server) {
        if (++ticks % EVERY != 0) return;
        if (Role.main()) {
            // which frequencies here can take something
            JsonArray room = new JsonArray();
            for (QIOFrequency f : all()) {
                if (f.getTotalItemCount() < f.getTotalItemCountCapacity()) room.add(key(f));
            }
            JsonObject s = new JsonObject();
            s.add("room", room);
            Share.state(this, s);
            return;
        }
        String main = mainPeer();
        if (main == null || System.nanoTime() - mainAt > FRESH_NS) return;
        for (QIOFrequency f : all()) {
            String key = key(f);
            if (mainHas.contains(key) && !Share.busy(this, main, key)) sendHome(server, main, f, key, TYPES_PER_GIVE);
        }
    }

    /** Takes up to that many kinds of things out of this frequency and gives them to main. */
    private void sendHome(MinecraftServer server, String main, QIOFrequency f, String key, int types) {
        if (f.getTotalItemCount() <= 0 || !Share.canGive(main)) return;
        HolderLookup.Provider reg = server.registryAccess();
        List<ItemStack> kinds = new ArrayList<>();
        List<Long> counts = new ArrayList<>();
        f.forAllStored((st, n) -> {
            if (kinds.size() < types && n > 0) {
                kinds.add(st.copyWithCount(1));
                counts.add(n);
            }
        });
        JsonArray items = new JsonArray();
        List<ItemStack> took = new ArrayList<>();
        List<Long> tookN = new ArrayList<>();
        for (int i = 0; i < kinds.size(); i++) {
            long got = f.massExtract(kinds.get(i), counts.get(i), Action.EXECUTE);
            if (got <= 0) continue;
            took.add(kinds.get(i));
            tookN.add(got);
            JsonArray one = new JsonArray();
            one.add(kinds.get(i).save(reg).toString());
            one.add(got);
            items.add(one);
        }
        if (items.isEmpty()) return;
        JsonObject what = new JsonObject();
        what.addProperty("f", key);
        what.add("items", items);
        if (!Share.give(this, main, key, what)) {
            for (int i = 0; i < took.size(); i++) f.massInsert(took.get(i), tookN.get(i), Action.EXECUTE);
            return;
        }
        for (long n : tookN) moved += n;
    }

    @Override
    public void onState(String from, JsonObject state) {
        if (Role.main() || !(state.get("room") instanceof JsonArray a)) return;
        Set<String> s = new HashSet<>();
        for (JsonElement e : a) s.add(e.getAsString());
        mainHas = s;
        mainAt = System.nanoTime();
    }

    /** Into that frequency, whatever fits; the rest is handed back. */
    private JsonObject put(MinecraftServer server, JsonObject data) {
        QIOFrequency f = find(data.get("f").getAsString());
        if (f == null) return data;
        HolderLookup.Provider reg = server.registryAccess();
        List<ItemStack> stacks = new ArrayList<>();
        List<Long> counts = new ArrayList<>();
        for (JsonElement el : data.getAsJsonArray("items")) {
            JsonArray a = el.getAsJsonArray();
            try {
                stacks.add(ItemStack.parse(reg, TagParser.parseTag(a.get(0).getAsString())).orElseThrow());
            } catch (com.mojang.brigadier.exceptions.CommandSyntaxException | java.util.NoSuchElementException e) {
                throw new IllegalArgumentException("unreadable item: " + e.getMessage(), e);
            }
            counts.add(a.get(1).getAsLong());
        }
        JsonArray rest = new JsonArray();
        JsonArray in = data.getAsJsonArray("items");
        for (int i = 0; i < stacks.size(); i++) {
            long put = f.massInsert(stacks.get(i), counts.get(i), Action.EXECUTE);
            if (put < counts.get(i)) {
                JsonArray one = new JsonArray();
                one.add(in.get(i).getAsJsonArray().get(0));
                one.add(counts.get(i) - put);
                rest.add(one);
            }
        }
        if (rest.isEmpty()) return null;
        JsonObject back = new JsonObject();
        back.addProperty("f", data.get("f").getAsString());
        back.add("items", rest);
        return back;
    }

    @Override
    public JsonObject receive(MinecraftServer server, String from, JsonObject data) {
        return put(server, data);
    }

    @Override
    public JsonObject refund(MinecraftServer server, JsonObject data) {
        return put(server, data);
    }

    @Override
    public void onStopping(MinecraftServer server) {
        if (Role.main()) return;
        String main = mainPeer();
        if (main == null) return;
        for (QIOFrequency f : all()) {
            String key = key(f);
            if (mainHas.contains(key)) sendHome(server, main, f, key, Integer.MAX_VALUE);
        }
    }

    @Override
    public String describe() {
        return all().size() + " frequencies here, " + (Role.main() ? "" : mainHas.size() + " on main with room, ") + moved + " items sent home since start";
    }
}
