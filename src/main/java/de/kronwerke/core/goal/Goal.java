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
 *   "id": "stage2",
 *   "title": "The Brass Engine",
 *   "description": "Brass, mana and the Nether.",
 *   "requires": ["stage1"],
 *   "holdAt": 0.98,
 *   "scale": true,
 *   "pillars": [
 *     { "id": "tech", "title": "Tech", "items": [
 *       { "item": "create:brass_ingot", "base": 4000 },
 *       { "item": "create:precision_mechanism", "base": 300 }
 *     ]},
 *     { "id": "magic", "title": "Magic", "items": [
 *       { "item": "botania:mana_pearl", "base": 1500 },
 *       { "item": "botania:terrasteel_ingot", "base": 100 }
 *     ]}
 *   ],
 *   "onComplete": ["chapters grant @a stage2"],
 *   "starterKit": [ { "item": "create:brass_ingot", "count": 16 } ]
 * }
 * </pre>
 *
 * An item can be an id or a #tag. Base amounts are multiplied by the activity factor when the goal
 * becomes active (see {@link GoalManager#activate}) unless scale is false. At holdAt (fraction of
 * the total) the goal stops accepting deposits until an admin opens it with /kw admin goal open.
 */
public record Goal(String id, String title, String description, List<String> requires, Double holdAt,
                   Boolean scale, List<Pillar> pillars, List<String> onComplete, List<KitItem> starterKit) {

    public record Pillar(String id, String title, List<PillarItem> items) {}

    public record PillarItem(String item, long base) {

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

    public record KitItem(String item, int count) {}

    public double holdFraction() {
        return holdAt == null ? 1.0 : holdAt;
    }

    public boolean scales() {
        return scale == null || scale;
    }

    /** Every item of every pillar, in order. */
    public List<PillarItem> allItems() {
        return pillars.stream().flatMap(p -> p.items().stream()).toList();
    }
}
