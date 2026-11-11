package de.kronwerke.core.mixin;

import java.util.List;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import de.kronwerke.core.perf.TagIndex;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;

/**
 * A selector with a positive tag looks only at the entities that carry it instead of walking
 * every entity of the dimension. The type, the box and the full predicate (which includes the
 * tag test) are applied as in vanilla, and the result limit too.
 */
@Mixin(EntitySelector.class)
public abstract class SelectorTagIndexMixin implements TagIndex.Required {

    @Shadow
    @Final
    private EntityTypeTest<Entity, ?> type;

    @Shadow
    protected abstract int getResultLimit();

    @Unique
    private List<String> kronwerke$tags = List.of();

    @Inject(method = "addEntities", at = @At("HEAD"), cancellable = true)
    private void kronwerke$byTag(List<Entity> entities, ServerLevel level, @Nullable AABB box, Predicate<Entity> predicate, CallbackInfo ci) {
        if (kronwerke$tags.isEmpty() || !TagIndex.enabled()) return;
        ci.cancel();
        int limit = getResultLimit();
        if (entities.size() >= limit) return;
        for (Entity e : TagIndex.candidates(level, kronwerke$tags)) {
            if (type.tryCast(e) == null) continue;
            if (box != null && !e.getBoundingBox().intersects(box)) continue;
            if (!predicate.test(e)) continue;
            entities.add(e);
            if (entities.size() >= limit) return;
        }
    }

    @Override
    public List<String> kronwerke$requiredTags() {
        return kronwerke$tags;
    }

    @Override
    public void kronwerke$requiredTags(List<String> tags) {
        kronwerke$tags = tags;
    }
}
