package de.kronwerke.core.portal;

import com.google.gson.JsonObject;
import de.kronwerke.core.KronwerkeCore;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * What travels with a player besides the player file, for mods that keep a player's things
 * in world data. Through reflection, so Core runs without them.
 * <p>
 * Sophisticated Backpacks keeps a backpack's contents in world storage under the backpack's
 * uuid; the item only carries the uuid. Every backpack in the player file (inventory, ender
 * chest, Curios slots) and every backpack inside those travels along.
 */
final class Compat {
    private static final String STORAGE_UUID = "sophisticatedcore:storage_uuid";

    private Compat() {
    }

    // ---- Sophisticated Backpacks ----

    private static Object backpackStorage() {
        try {
            Class<?> c = Class.forName("net.p3pp3rf1y.sophisticatedbackpacks.backpack.BackpackStorage");
            return c.getMethod("get").invoke(null);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            return null;
        }
    }

    /** The extra data that goes with the player: backpack contents by uuid. Null when there is none. */
    static JsonObject collect(ServerPlayer p) {
        Object storage = backpackStorage();
        if (storage == null) return null;
        try {
            Method get = storage.getClass().getMethod("getBackpackContents", UUID.class);
            CompoundTag self = p.saveWithoutId(new CompoundTag());
            Set<UUID> seen = new LinkedHashSet<>();
            List<UUID> todo = new java.util.ArrayList<>();
            find(self, todo);
            JsonObject packs = new JsonObject();
            while (!todo.isEmpty()) {
                UUID id = todo.remove(0);
                if (!seen.add(id) || seen.size() > 256) continue;
                @SuppressWarnings("unchecked")
                Optional<CompoundTag> c = (Optional<CompoundTag>) get.invoke(storage, id);
                if (c.isEmpty()) continue;
                packs.addProperty(id.toString(), Travel.encode(c.get()));
                find(c.get(), todo); // backpacks in backpacks
            }
            if (packs.size() == 0) return null;
            JsonObject extra = new JsonObject();
            extra.add("backpacks", packs);
            return extra;
        } catch (ReflectiveOperationException | RuntimeException e) {
            KronwerkeCore.LOGGER.warn("Backpacks did not travel with {}: {}", p.getGameProfile().getName(), e.toString());
            return null;
        }
    }

    /** Every storage uuid in a tag, wherever it sits. */
    private static void find(Tag t, List<UUID> out) {
        if (t instanceof CompoundTag c) {
            for (String k : c.getAllKeys()) {
                Tag v = c.get(k);
                if (k.equals(STORAGE_UUID) && v instanceof IntArrayTag a && a.size() == 4) out.add(NbtUtils.loadUUID(a));
                else find(v, out);
            }
        } else if (t instanceof ListTag l) {
            for (Tag v : l) find(v, out);
        }
    }

    static void restore(MinecraftServer server, JsonObject extra) {
        if (!(extra.get("backpacks") instanceof JsonObject packs)) return;
        Object storage = backpackStorage();
        if (storage == null) return;
        try {
            Method set = storage.getClass().getMethod("setBackpackContents", UUID.class, CompoundTag.class);
            for (String k : packs.keySet()) set.invoke(storage, UUID.fromString(k), Travel.decode(packs.get(k).getAsString()));
        } catch (Exception e) {
            KronwerkeCore.LOGGER.warn("Backpacks could not be unpacked: {}", e.toString());
        }
    }

    // ---- NeoOrigins ----

    private static Method powers;

    /** Whether the player has a power whose id contains the text (Origins' Tiefgräber, say). */
    static boolean hasPower(ServerPlayer p, String part) {
        try {
            if (powers == null) powers = Class.forName("com.cyberday1.neoorigins.api.NeoOriginsAPI").getMethod("powers", ServerPlayer.class);
            for (Object h : (List<?>) powers.invoke(null, p)) {
                Object id = h.getClass().getMethod("id").invoke(h);
                if (String.valueOf(id).contains(part)) return true;
            }
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            return false;
        }
        return false;
    }
}
