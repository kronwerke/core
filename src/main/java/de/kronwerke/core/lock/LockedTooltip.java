package de.kronwerke.core.lock;

import com.gabinx.chapters.stage.ClientStageCache;
import com.gabinx.chapters.stage.ClientStageIndices;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import java.util.Set;

/** Client only: a line under the name of a locked item, saying which stage it belongs to. */
public final class LockedTooltip {
    private LockedTooltip() {}

    public static void onTooltip(ItemTooltipEvent event) {
        if (event.getItemStack().isEmpty() || event.getEntity() == null) return;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(event.getItemStack().getItem());
        Set<ResourceLocation> stages = ClientStageIndices.itemsView().get(id);
        if (stages == null || stages.isEmpty()) return;
        Set<ResourceLocation> has = ClientStageCache.snapshot();
        ResourceLocation missing = null;
        for (ResourceLocation s : stages) {
            if (has.contains(s)) return;
            if (missing == null || s.compareTo(missing) < 0) missing = s;
        }
        Component stage = LockedItems.stageName(missing).copy().withStyle(ChatFormatting.YELLOW);
        event.getToolTip().add(1, Component.translatableWithFallback("kronwerke.locked.tooltip",
                "Gesperrt bis %s. Einstecken geht, benutzen noch nicht.", stage).withStyle(ChatFormatting.RED));
    }
}
