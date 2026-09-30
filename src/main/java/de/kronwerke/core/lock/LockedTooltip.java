package de.kronwerke.core.lock;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import java.util.List;

/**
 * Client only: a locked item has no name yet. Its tooltip says "???" and which stage
 * opens it, and whether it can be carried in the meantime.
 */
public final class LockedTooltip {
    private LockedTooltip() {}

    public static void onTooltip(ItemTooltipEvent event) {
        if (event.getItemStack().isEmpty() || event.getEntity() == null) return;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(event.getItemStack().getItem());
        ResourceLocation missing = ClientLocks.lockingStage(id);
        if (missing == null) return;
        Component stage = LockedItems.stageName(missing).copy().withStyle(ChatFormatting.YELLOW);
        List<Component> lines = event.getToolTip();
        lines.clear();
        lines.add(Component.translatableWithFallback("kronwerke.locked.name", "???").withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatableWithFallback("kronwerke.locked.opens", "Öffnet in %s.", stage).withStyle(ChatFormatting.RED));
        lines.add(Component.translatableWithFallback("kronwerke.locked.carry", "Einstecken geht, benutzen noch nicht.")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
