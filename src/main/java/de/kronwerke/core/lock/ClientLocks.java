package de.kronwerke.core.lock;

import com.gabinx.chapters.stage.ClientStageCache;
import com.gabinx.chapters.stage.ClientStageIndices;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Client only: which items the local player has not unlocked yet, and which stage comes
 * next. Asked for every item drawn on screen, so the answers are cached until the stages
 * change; {@link #tick()} checks that twice a second.
 */
public final class ClientLocks {
    private ClientLocks() {}

    private static final Map<Item, Boolean> locked = new IdentityHashMap<>();
    private static Set<ResourceLocation> seenStages = Set.of();
    private static int seenIndexSize = -1;
    private static ResourceLocation next;
    private static int ticks;
    private static Runnable onChange = () -> {};

    /** Called after the stages the player has, or the stage index, changed. */
    public static void onChange(Runnable r) {
        onChange = r;
    }

    public static void tick() {
        if (++ticks % 10 != 0) return;
        Set<ResourceLocation> now = ClientStageCache.snapshot();
        int size = ClientStageIndices.itemsView().size();
        if (now.equals(seenStages) && size == seenIndexSize) return;
        seenStages = now;
        seenIndexSize = size;
        locked.clear();
        next = null;
        onChange.run();
    }

    public static boolean isLocked(Item item) {
        Boolean b = locked.get(item);
        if (b == null) {
            b = lockingStage(BuiltInRegistries.ITEM.getKey(item)) != null;
            locked.put(item, b);
        }
        return b;
    }

    /** The first stage of this item the player does not have, or null when it is open. */
    public static ResourceLocation lockingStage(ResourceLocation id) {
        Set<ResourceLocation> stages = ClientStageIndices.itemsView().get(id);
        if (stages == null || stages.isEmpty()) return null;
        Set<ResourceLocation> has = seenStages.isEmpty() ? ClientStageCache.snapshot() : seenStages;
        ResourceLocation first = null;
        for (ResourceLocation s : stages) {
            if (has.contains(s)) return null;
            if (first == null || s.compareTo(first) < 0) first = s;
        }
        return first;
    }

    /** The lowest stage the player does not have yet. */
    public static ResourceLocation nextStage() {
        if (next != null) return next;
        Set<ResourceLocation> has = ClientStageCache.snapshot();
        TreeSet<ResourceLocation> all = new TreeSet<>();
        for (Set<ResourceLocation> s : ClientStageIndices.itemsView().values()) all.addAll(s);
        for (ResourceLocation s : all) {
            if (!has.contains(s)) {
                next = s;
                break;
            }
        }
        return next;
    }

    /** Locked, and it opens with the next stage. */
    public static boolean opensNext(ResourceLocation id) {
        ResourceLocation stage = lockingStage(id);
        return stage != null && stage.equals(nextStage());
    }
}
