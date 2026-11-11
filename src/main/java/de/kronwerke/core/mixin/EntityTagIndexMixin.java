package de.kronwerke.core.mixin;

import java.util.Set;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import de.kronwerke.core.perf.TagIndex;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

/** Every entity's tag set reports its changes to the tag index (see TagIndex). */
@Mixin(Entity.class)
public abstract class EntityTagIndexMixin implements TagIndex.Tracked {

    @Shadow
    @Final
    @Mutable
    private Set<String> tags;

    @Unique
    private ServerLevel kronwerke$tagLevel;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void kronwerke$trackedTags(CallbackInfo ci) {
        Set<String> plain = this.tags;
        TagIndex.Tags tracked = new TagIndex.Tags((Entity) (Object) this);
        tracked.addAll(plain); // empty after the constructor, kept in case a mod filled it
        this.tags = tracked;
    }

    @Override
    public ServerLevel kronwerke$tagLevel() {
        return kronwerke$tagLevel;
    }

    @Override
    public void kronwerke$tagLevel(ServerLevel level) {
        kronwerke$tagLevel = level;
    }
}
