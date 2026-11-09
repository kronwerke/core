package de.kronwerke.core.perf;

import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import org.slf4j.Logger;

/**
 * An Industrial Foregoing Infinity Nuke that was saved while exploding goes off again every
 * time its chunk loads, and it forgets who placed it. In the load tests two such nukes in the
 * season world each blew a 173 block crater whenever a test player came near, and their
 * calculation stalled the server for seconds. A nuke loaded from disk while already exploding
 * is dropped and its position logged. Nukes a player places and arms are not touched.
 */
public final class StaleNukes {

    private static final Logger LOG = LogUtils.getLogger();
    private static final ResourceLocation NUKE = ResourceLocation.fromNamespaceAndPath("industrialforegoing", "infinity_nuke");

    private StaleNukes() {
    }

    public static void onJoin(EntityJoinLevelEvent event) {
        if (!event.loadedFromDisk() || event.getLevel().isClientSide()) {
            return;
        }
        Entity entity = event.getEntity();
        if (!NUKE.equals(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()))) {
            return;
        }
        CompoundTag tag = entity.saveWithoutId(new CompoundTag());
        if (tag.getBoolean("Exploding")) {
            LOG.warn("[Kronwerke] Dropped an Infinity Nuke that was saved while exploding: {} {} {} {} (radius {})",
                    event.getLevel().dimension().location(), entity.getBlockX(), entity.getBlockY(), entity.getBlockZ(), tag.getInt("Radius"));
            event.setCanceled(true);
        }
    }
}
