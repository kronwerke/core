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
 *       { "item": "kronwerke:rune_core", "base": 8, "fixed": true, "weight": 100 }
 *     ]}
 *   ],
 *   "stages": ["kronwerke:stage2"],
 *   "onComplete": ["say Stage 2 is open."],
 *   "starterKit": [ { "item": "create:brass_ingot", "count": 16 } ]
 * }
 * </pre>
 *
 * Stages are Chapters stage ids. They are granted to every online player when the goal completes and
 * to every other player when they next log in. An item can be an id or a #tag. Base amounts are multiplied by the activity factor when the goal
 * becomes active (see {@link GoalManager#activate}) unless scale is false for the goal or fixed is
 * true for the item. Progress is counted in points: every item counts its weight (default 1), so a
 * handful of milestone items can carry as much of the bar as thousands of ingots. At holdAt
 * (fraction of the points) the goal stops accepting deposits until an admin opens it with
 * /kw admin goal open.
 */
public record Goal(String id, String title, String description, List<String> requires, Double holdAt,
                   Boolean scale, List<Pillar> pillars, List<String> stages, List<String> onComplete,
                   List<KitItem> starterKit) {

    public record Pillar(String id, String title, List<PillarItem> items) {}

    public record PillarItem(String item, long base, Boolean fixed, Integer weight) {

        public PillarItem(String item, long base) {
            this(item, base, null, null);
        }

        /** False for items whose amount stays the same whatever the player count. */
        public boolean scales() {
            return fixed == null || !fixed;
        }

        /** Points one item is worth on the bar. */
        public long points() {
            return weight == null || weight < 1 ? 1 : weight;
        }

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
