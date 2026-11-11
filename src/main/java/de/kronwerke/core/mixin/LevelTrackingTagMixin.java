package de.kronwerke.core.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import de.kronwerke.core.perf.TagIndex;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

/**
 * The tag index holds the entities the level tracks, the same ones the level's entity lookup
 * (and with it every command selector) sees.
 */
@Mixin(targets = "net.minecraft.server.level.ServerLevel$EntityCallbacks")
public abstract class LevelTrackingTagMixin {

    @Inject(method = "onTrackingStart(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"))
    private void kronwerke$indexTags(Entity entity, CallbackInfo ci) {
        if (entity.level() instanceof ServerLevel level) TagIndex.track(entity, level);
    }

    @Inject(method = "onTrackingEnd(Lnet/minecraft/world/entity/Entity;)V", at = @At("TAIL"))
    private void kronwerke$unindexTags(Entity entity, CallbackInfo ci) {
        TagIndex.untrack(entity);
    }
}
