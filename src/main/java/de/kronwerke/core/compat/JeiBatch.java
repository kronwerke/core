package de.kronwerke.core.compat;

import de.kronwerke.core.KronwerkeCore;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.runtime.IIngredientManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Collects the ingredient changes Chapters makes in JEI while it applies the stage locks,
 * and hands them to JEI in as few calls as possible.
 *
 * Chapters hides every locked item with its own call to removeIngredientsAtRuntime. Each
 * call rebuilds JEI's ingredient list, which takes up to two seconds with this pack, and
 * the stages lock about six thousand items: joining took ten minutes on the render thread
 * and the server timed the player out long before. The same happened in reverse when a
 * stage opened. Batched, it is one removal and one addition per ingredient type.
 *
 * Chapters also scans JEI's whole item list once per locked item to find its stacks.
 * While a batch runs, that list is grouped by item once and each scan gets only the
 * stacks of its own item.
 */
public final class JeiBatch {
    private JeiBatch() {}

    private record Op(IIngredientManager manager, IIngredientType<?> type, boolean add, List<Object> items) {}

    private static boolean active;
    private static final List<Op> ops = new ArrayList<>();
    private static ResourceLocation currentItem;
    private static IIngredientManager indexed;
    private static Map<ResourceLocation, List<ItemStack>> byItem;

    /** Chapters is about to look up the stacks of this item. */
    public static void lookingFor(ResourceLocation item) {
        currentItem = item;
    }

    /** What JEI's item list is for the item being looked up. */
    public static Collection<ItemStack> itemStacks(IIngredientManager manager) {
        if (!active || currentItem == null) return manager.getAllItemStacks();
        if (indexed != manager || byItem == null) {
            byItem = new HashMap<>();
            for (ItemStack stack : manager.getAllItemStacks()) {
                if (stack.isEmpty()) continue;
                byItem.computeIfAbsent(BuiltInRegistries.ITEM.getKey(stack.getItem()), k -> new ArrayList<>()).add(stack);
            }
            indexed = manager;
        }
        return byItem.getOrDefault(currentItem, List.of());
    }

    public static void begin() {
        if (active) flush(); // a previous run ended with an exception
        active = true;
    }

    public static void end() {
        active = false;
        currentItem = null;
        indexed = null;
        byItem = null;
        flush();
    }

    public static void remove(IIngredientManager manager, IIngredientType<?> type, Collection<?> items) {
        if (!active) {
            call(manager, type, false, items);
            return;
        }
        queue(manager, type, false, items);
    }

    public static void add(IIngredientManager manager, IIngredientType<?> type, Collection<?> items) {
        if (!active) {
            call(manager, type, true, items);
            return;
        }
        queue(manager, type, true, items);
    }

    private static void queue(IIngredientManager manager, IIngredientType<?> type, boolean add, Collection<?> items) {
        // consecutive changes of the same kind are merged; the order between kinds is kept
        if (!ops.isEmpty()) {
            Op last = ops.get(ops.size() - 1);
            if (last.manager == manager && last.type == type && last.add == add) {
                last.items.addAll(items);
                return;
            }
        }
        ops.add(new Op(manager, type, add, new ArrayList<>(items)));
    }

    private static void flush() {
        if (ops.isEmpty()) return;
        long start = System.nanoTime();
        int n = 0;
        for (Op op : ops) {
            n += op.items.size();
            try {
                call(op.manager, op.type, op.add, op.items);
            } catch (RuntimeException e) {
                KronwerkeCore.LOGGER.warn("JEI rejected a batch of {} ingredients: {}", op.items.size(), e.toString());
            }
        }
        KronwerkeCore.LOGGER.info("Stage locks: {} ingredient changes in {} JEI calls, {} ms",
                n, ops.size(), (System.nanoTime() - start) / 1_000_000);
        ops.clear();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void call(IIngredientManager manager, IIngredientType type, boolean add, Collection items) {
        if (items.isEmpty()) return;
        if (add) {
            manager.addIngredientsAtRuntime(type, items);
        } else {
            manager.removeIngredientsAtRuntime(type, items);
        }
    }
}
