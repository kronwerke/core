package de.kronwerke.core.compat;

import com.gabinx.chapters.stage.ClientStageCache;
import com.gabinx.chapters.stage.ClientStageIndices;
import de.kronwerke.core.KronwerkeCore;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.registry.EmiStackList;
import dev.emi.emi.runtime.EmiReloadManager;
import dev.emi.emi.screen.EmiScreenManager;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.material.Fluid;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The stage locks in EMI's item list. With EMI in the pack JEI still runs underneath (73 mods
 * only have a JEI plugin), but it shows no list of its own, and what Chapters hides in JEI
 * would be thrown out of EMI's list for good at EMI's next reload (its JEI layer drops every
 * stack JEI calls invisible). So with EMI Chapters' changes to JEI's list are dropped
 * (JeiBatch) and the list is cut here instead, the same way as before: items of the next
 * stage stay, drawn veiled as "???" (LockedDecorator), items of later stages are out, and so
 * are locked fluids and chemicals. Recipes are not touched, as with JEI.
 * <p>
 * EMI keeps the whole list and derives the shown one from it ({@code bakeFiltered}), in a
 * tenth of a second; EmiStackListMixin calls {@link #filter()} at the end of that. When a
 * stage opens the shown list is derived again, nothing is reloaded.
 */
public final class EmiLocks {
    private EmiLocks() {}

    private static int lastHidden = -1;

    /** At the end of EMI's bakeFiltered, on EMI's reload thread or on the client's. */
    public static void filter() {
        try {
            List<EmiStack> all = EmiStackList.filteredStacks;
            Set<ResourceLocation> has = ClientStageCache.snapshot();
            ResourceLocation next = nextStage(has);
            List<EmiStack> keep = new ArrayList<>(all.size());
            for (EmiStack stack : all) {
                if (!hidden(stack, has, next)) keep.add(stack);
            }
            int hidden = all.size() - keep.size();
            if (hidden > 0) EmiStackList.filteredStacks = List.copyOf(keep);
            if (hidden != lastHidden) {
                lastHidden = hidden;
                KronwerkeCore.LOGGER.info("Stage locks in EMI: {} of {} stacks hidden, next stage {}", hidden, all.size(), next);
            }
        } catch (RuntimeException e) {
            // the stages changed under the reload thread's hands: again on the client's thread
            KronwerkeCore.LOGGER.warn("Stage locks in EMI not applied, trying again: {}", e.toString());
            Minecraft.getInstance().execute(EmiLocks::refresh);
        }
    }

    private static boolean hidden(EmiStack stack, Set<ResourceLocation> has, ResourceLocation next) {
        Object key = stack.getKey();
        if (key instanceof Item) {
            ResourceLocation stage = locking(ClientStageIndices.itemsView(), stack.getId(), has);
            return stage != null && !stage.equals(next);
        }
        if (key instanceof Fluid) return locking(ClientStageIndices.fluidsView(), stack.getId(), has) != null;
        Map<ResourceLocation, Set<ResourceLocation>> chemicals = ClientStageIndices.chemicalsView();
        return !chemicals.isEmpty() && key != null && locking(chemicals, stack.getId(), has) != null;
    }

    /** The lowest stage the player does not have. Worked out here and not taken from ClientLocks, whose answer is the client thread's. */
    private static ResourceLocation nextStage(Set<ResourceLocation> has) {
        ResourceLocation next = null;
        for (Set<ResourceLocation> stages : ClientStageIndices.itemsView().values()) {
            for (ResourceLocation s : stages) {
                if (!has.contains(s) && (next == null || s.compareTo(next) < 0)) next = s;
            }
        }
        return next;
    }

    /** The first stage of the thing the player does not have, or null when it is open. */
    private static ResourceLocation locking(Map<ResourceLocation, Set<ResourceLocation>> index, ResourceLocation id,
                                            Set<ResourceLocation> has) {
        if (id == null) return null;
        Set<ResourceLocation> stages = index.get(id);
        if (stages == null || stages.isEmpty()) return null;
        ResourceLocation first = null;
        for (ResourceLocation s : stages) {
            if (has.contains(s)) return null;
            if (first == null || s.compareTo(first) < 0) first = s;
        }
        return first;
    }

    /** The player's stages changed (ClientLocks, on the client's thread): derive the shown list again. */
    public static void refresh() {
        // while EMI reloads there is no list yet; the reload ends with a bakeFiltered of its own
        if (!EmiReloadManager.isLoaded()) return;
        EmiStackList.bakeFiltered();
        EmiScreenManager.forceRecalculate();
    }
}
