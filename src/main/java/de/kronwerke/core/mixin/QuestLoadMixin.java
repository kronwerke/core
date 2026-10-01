package de.kronwerke.core.mixin;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectCollection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * FTB Quests walks its object map while reading data, and reading a reward table puts the
 * table's entries into that same map. When those entries push the map over its fill limit
 * it grows mid-walk and loading fails with "this.wrapped is null". With about twelve
 * thousand objects the Kronwerke book sits right at that limit. Walking a copy of the
 * values makes the size irrelevant.
 */
@Pseudo
@Mixin(targets = "dev.ftb.mods.ftbquests.quest.BaseQuestFile", remap = false)
public abstract class QuestLoadMixin {

    @Redirect(method = "readDataFull", at = @At(value = "INVOKE",
            target = "Lit/unimi/dsi/fastutil/longs/Long2ObjectOpenHashMap;values()Lit/unimi/dsi/fastutil/objects/ObjectCollection;"),
            require = 0)
    private ObjectCollection<?> kronwerke$copyValues(Long2ObjectOpenHashMap<?> map) {
        return new ObjectArrayList<>(map.values());
    }
}
