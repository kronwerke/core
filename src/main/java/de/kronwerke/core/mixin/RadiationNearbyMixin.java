package de.kronwerke.core.mixin;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Nuclear Radiation lets every living entity look for every other living entity within 64
 * blocks and scan their inventories for radioactive items, which grows with the square of
 * the mobs around. Only players carry radioactive items in practice (dropped items are their
 * own sources), so only players are looked at as carriers. Players still irradiate mobs and
 * each other as before.
 */
@Pseudo
@Mixin(targets = "igentuman.nr.radiation.simulation.entity.EntityDoseProcessor", remap = false)
public abstract class RadiationNearbyMixin {

    @Redirect(method = "computeNearbyEntities", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;getEntitiesOfClass(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"),
            require = 0)
    private static List<?> kronwerke$onlyPlayers(ServerLevel level, Class<?> type, AABB box) {
        List<ServerPlayer> found = new ArrayList<>(4);
        for (ServerPlayer player : level.players()) {
            if (player.isAlive() && box.intersects(player.getBoundingBox())) {
                found.add(player);
            }
        }
        return found;
    }
}
