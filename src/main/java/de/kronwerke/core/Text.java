package de.kronwerke.core;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Map;

/**
 * Player facing text. Every message is a translatable component with a German fallback, so
 * the client shows its own language where Core ships one and German everywhere else. Items
 * and tags are shown by name, never by id.
 */
public final class Text {
    private static final Map<String, String> TAG_NAMES = Map.of(
            "c:cobblestones", "Bruchstein (alle Arten)",
            "c:ingots/steel", "Stahlbarren (alle Mods)",
            "c:ingots/brass", "Messingbarren",
            "c:dusts/draconium", "Draconiumstaub");

    private Text() {
    }

    public static MutableComponent t(String key, String fallback, Object... args) {
        return Component.translatableWithFallback("kronwerke." + key, fallback, args);
    }

    /** The name of an item id or a #tag, as the player sees it in game. */
    public static MutableComponent item(String id) {
        if (id.startsWith("#")) {
            String tag = id.substring(1);
            ResourceLocation rl = ResourceLocation.tryParse(tag);
            String fallback = TAG_NAMES.get(tag);
            if (fallback == null && rl != null) {
                fallback = BuiltInRegistries.ITEM.getTag(TagKey.create(Registries.ITEM, rl))
                        .flatMap(set -> set.stream().findFirst())
                        .map(h -> new ItemStack(h.value()).getHoverName().getString() + " (alle Arten)")
                        .orElse(tag);
            }
            return Component.translatableWithFallback("kronwerke.tag." + tag.replace(':', '.').replace('/', '.'), fallback == null ? tag : fallback)
                    .withStyle(ChatFormatting.AQUA);
        }
        ResourceLocation rl = ResourceLocation.tryParse(id);
        Item item = rl == null ? null : BuiltInRegistries.ITEM.getOptional(rl).orElse(null);
        if (item == null) return Component.literal(id).withStyle(ChatFormatting.AQUA);
        return new ItemStack(item).getHoverName().copy().withStyle(ChatFormatting.AQUA);
    }

    public static String number(long n) {
        String s = Long.toString(n);
        StringBuilder b = new StringBuilder();
        int c = 0;
        for (int i = s.length() - 1; i >= 0; i--) {
            b.append(s.charAt(i));
            if (++c % 3 == 0 && i > 0) b.append(' ');
        }
        return b.reverse().toString();
    }
}
