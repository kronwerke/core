package de.kronwerke.core.perf;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

/** One answer of Explosion.getSeenPercent, kept for the second identical question (see ExplosionSeenMixin). */
public record ExplosionSeen(long explosion, Entity entity, AABB box, double x, double y, double z, float value) {
}
