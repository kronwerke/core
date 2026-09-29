package de.kronwerke.core.goal;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * One community goal, defined in config/kronwerke/goals.json.
 *
 * <pre>
 * {
 *   "id": "age1_cobble",
 *   "title": "Foundation of the Kronwerk",
 *   "description": "Deposit cobblestone at the spawn obelisk.",
 *   "item": "#c:cobblestones",          // item id or #tag
 *   "amount": 10000,
 *   "requires": [],                     // goal ids that must be complete first
 *   "onComplete": [                     // console commands, run once
 *     "chapters grant @a age1",
 *     "title @a title {\"text\":\"Age 1 unlocked\"}"
 *   ]
 * }
 * </pre>
 */
public record Goal(String id, String title, String description, String item, long amount,
                   List<String> requires, List<String> onComplete) {

    public boolean isTag() {
        return item.startsWith("#");
    }

    public boolean matches(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (isTag()) {
            TagKey<Item> tag = TagKey.create(Registries.ITEM, ResourceLocation.parse(item.substring(1)));
            return stack.is(tag);
        }
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(item);
    }
}
