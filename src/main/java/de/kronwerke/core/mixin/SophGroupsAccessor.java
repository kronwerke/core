package de.kronwerke.core.mixin;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.p3pp3rf1y.sophisticatedcore.linkedstorage.LinkedStorageGroupsSavedData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Map;
import java.util.UUID;

/** The group records of Sophisticated Core's linked storage, to move one between servers whole. */
@Mixin(value = LinkedStorageGroupsSavedData.class, remap = false)
public interface SophGroupsAccessor {
    /** The records by group id; the record class is not public, so they are plain objects here. */
    @Accessor("groups")
    Map<UUID, Object> kronwerke$groups();

    @Invoker("load")
    static LinkedStorageGroupsSavedData kronwerke$load(CompoundTag tag, HolderLookup.Provider registries) {
        throw new AssertionError();
    }
}
