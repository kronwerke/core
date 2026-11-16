package de.kronwerke.core.share.rs;

import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.refinedmods.refinedstorage.common.api.RefinedStorageApi;
import com.refinedmods.refinedstorage.common.api.storage.SerializableStorage;
import com.refinedmods.refinedstorage.common.api.storage.StorageRepository;
import de.kronwerke.core.KronwerkeCore;
import de.kronwerke.core.portal.Travel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.lang.reflect.Field;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Refined Storage keeps what is on a storage disk (and in a storage block taken down, and in a
 * portable grid) in the world's storage repository, under a uuid; the item only carries the uuid.
 * On another server of the network the uuid named nothing and the disk was empty there. Now every
 * disk a travelling player has with them (inventory, ender chest, Curios, inside a backpack, in a
 * disk drive carried with Carry On) takes its contents along, the same as backpacks, and so does
 * a disk thrown through a portal. The server it comes from keeps its copy until the disk comes
 * back and replaces it. Nothing here runs without Refined Storage.
 */
public final class RsDisks {
    private static final String REFERENCE = "refinedstorage:storage_reference";
    private static Field codecField;

    private RsDisks() {
    }

    @SuppressWarnings("unchecked")
    private static Codec<Map<UUID, SerializableStorage>> codec(StorageRepository repo) throws ReflectiveOperationException {
        if (codecField == null) {
            codecField = repo.getClass().getDeclaredField("codec");
            codecField.setAccessible(true);
        }
        return (Codec<Map<UUID, SerializableStorage>>) codecField.get(repo);
    }

    private static StorageRepository repo(MinecraftServer server) {
        return RefinedStorageApi.INSTANCE.getStorageRepository(server.overworld());
    }

    /** The contents of every disk named in the tag, by uuid; null when there is none. */
    public static JsonObject collect(CompoundTag self, String what) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return null;
        Set<UUID> ids = new LinkedHashSet<>();
        find(self, ids);
        if (ids.isEmpty()) return null;
        try {
            StorageRepository repo = repo(server);
            Codec<Map<UUID, SerializableStorage>> codec = codec(repo);
            RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, server.registryAccess());
            JsonObject out = new JsonObject();
            for (UUID id : ids) {
                SerializableStorage s = repo.get(id).orElse(null);
                if (s == null) continue;
                Tag t = codec.encodeStart(ops, Map.of(id, s)).getOrThrow();
                if (t instanceof CompoundTag c) out.addProperty(id.toString(), Travel.encode(c));
            }
            return out.size() == 0 ? null : out;
        } catch (ReflectiveOperationException | RuntimeException e) {
            KronwerkeCore.LOGGER.warn("Refined Storage disks did not travel with {}: {}", what, e.toString());
            return null;
        }
    }

    /** Puts what came along in place of what this server had under the same uuids. */
    public static void restore(MinecraftServer server, JsonObject disks) {
        try {
            StorageRepository repo = repo(server);
            Codec<Map<UUID, SerializableStorage>> codec = codec(repo);
            RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, server.registryAccess());
            int n = 0;
            for (String k : disks.keySet()) {
                Map<UUID, SerializableStorage> m = codec.parse(ops, Travel.decode(disks.get(k).getAsString())).getOrThrow();
                for (Map.Entry<UUID, SerializableStorage> e : m.entrySet()) {
                    if (repo.get(e.getKey()).isPresent()) repo.remove(e.getKey());
                    repo.set(e.getKey(), e.getValue());
                    n++;
                }
            }
            repo.markAsChanged();
            KronwerkeCore.LOGGER.info("Refined Storage: {} disks came with their contents", n);
        } catch (Exception e) {
            KronwerkeCore.LOGGER.warn("Refined Storage disks could not be unpacked: {}", e.toString());
        }
    }

    /** Every storage uuid in a tag, wherever it sits. */
    private static void find(Tag t, Set<UUID> out) {
        if (t instanceof CompoundTag c) {
            for (String k : c.getAllKeys()) {
                Tag v = c.get(k);
                if (k.equals(REFERENCE) && v instanceof IntArrayTag a && a.size() == 4) out.add(NbtUtils.loadUUID(a));
                else find(v, out);
            }
        } else if (t instanceof ListTag l) {
            for (Tag v : l) find(v, out);
        }
    }
}
