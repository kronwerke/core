package de.kronwerke.core.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

import de.kronwerke.core.perf.ExplosionSeen;
import de.kronwerke.core.config.KronwerkeConfig;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * An explosion asks how much of each entity in reach it can see twice: once for the damage
 * (inside the damage calculator) and once for the knockback, and each time casts up to 27 rays
 * per entity. Between the two questions only the damage happens. The second answer is now the
 * first one, kept while it is certainly the same question: the same explosion, the same entity
 * with the same bounding box (an entity that moved has a new one). The result is exactly
 * vanilla; a heap of TNT just casts half the rays. Primed TNT as the target is asked with one
 * ray instead of 27 (cheapTntExposure).
 */
@Mixin(Explosion.class)
public abstract class ExplosionSeenMixin {

    @Unique
    private static volatile ExplosionSeen kronwerke$last;
    @Unique
    private static long kronwerke$explosions;

    @Inject(method = "explode", at = @At("HEAD"))
    private void kronwerke$newExplosion(CallbackInfo ci) {
        kronwerke$explosions++;
        kronwerke$last = null;
    }

    @WrapMethod(method = "getSeenPercent")
    private static float kronwerke$askOnce(Vec3 from, Entity entity, Operation<Float> original) {
        long explosion = kronwerke$explosions;
        AABB box = entity.getBoundingBox();
        ExplosionSeen s = kronwerke$last;
        if (s != null && s.explosion() == explosion && s.entity() == entity && s.box() == box
                && s.x() == from.x && s.y() == from.y && s.z() == from.z) {
            return s.value();
        }
        float value = entity instanceof PrimedTnt && KronwerkeConfig.CHEAP_TNT_EXPOSURE.get()
                ? kronwerke$centerOnly(from, entity)
                : original.call(from, entity);
        kronwerke$last = new ExplosionSeen(explosion, entity, box, from.x, from.y, from.z, value);
        return value;
    }

    /**
     * Primed TNT only takes knockback from this answer (it cannot be hurt). One ray from its
     * centre instead of 27 over its box: fully open or fully covered TNT gets the vanilla answer,
     * half covered TNT is pushed fully or not at all. In a heap of thousands of TNT this is where
     * almost all of an explosion's time went.
     */
    @Unique
    private static float kronwerke$centerOnly(Vec3 from, Entity entity) {
        Vec3 center = entity.getBoundingBox().getCenter();
        return entity.level().clip(new ClipContext(center, from, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity)).getType() == HitResult.Type.MISS ? 1.0F : 0.0F;
    }
}
