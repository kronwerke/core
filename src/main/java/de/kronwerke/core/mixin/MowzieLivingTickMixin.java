package de.kronwerke.core.mixin;

import com.bobmowzie.mowziesmobs.server.capability.DataHandler;
import com.bobmowzie.mowziesmobs.server.entity.MowzieGeckoEntity;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;

/**
 * Mowzie's Mobs fetches three data attachments for every living entity on every tick, which also
 * creates and saves all three on every cow and zombie. For ordinary mobs two of them do nothing
 * when ticked (no abilities, an empty living tick) and the frost data only matters once something
 * froze the mob. So on the server, mobs that are not players and not Mowzie's own mobs only tick
 * the frost data when they already have it or carry an effect, and the other two are not touched.
 * The ticks below skip a missing (null) data object.
 */
@Pseudo
@Mixin(targets = "com.bobmowzie.mowziesmobs.server.ServerEventHandler", remap = false)
public abstract class MowzieLivingTickMixin {

    @WrapOperation(method = "onLivingTick", require = 0, at = @At(value = "INVOKE", target = "Lcom/bobmowzie/mowziesmobs/server/capability/DataHandler;getData(Lnet/minecraft/world/entity/Entity;Lnet/neoforged/neoforge/registries/DeferredHolder;)Ljava/lang/Object;"))
    private Object kronwerke$onlyExistingData(Entity entity, DeferredHolder<AttachmentType<?>, ?> type, Operation<Object> original) {
        if (entity.level().isClientSide || entity instanceof Player || entity instanceof MowzieGeckoEntity) {
            return original.call(entity, type);
        }
        if (type == DataHandler.FROZEN_DATA) {
            boolean relevant = entity.hasData(DataHandler.FROZEN_DATA.get())
                || (entity instanceof LivingEntity living && !living.getActiveEffectsMap().isEmpty());
            return relevant ? original.call(entity, type) : null;
        }
        return null; // living data tick is empty, and non Mowzie mobs have no abilities
    }

    @WrapOperation(method = "onLivingTick", require = 0, at = @At(value = "INVOKE", target = "Lcom/bobmowzie/mowziesmobs/server/capability/FrozenData;tick(Lnet/minecraft/world/entity/LivingEntity;)V"))
    private void kronwerke$frozenTick(com.bobmowzie.mowziesmobs.server.capability.FrozenData data, LivingEntity entity, Operation<Void> original) {
        if (data != null) original.call(data, entity);
    }

    @WrapOperation(method = "onLivingTick", require = 0, at = @At(value = "INVOKE", target = "Lcom/bobmowzie/mowziesmobs/server/capability/LivingData;tick(Lnet/minecraft/world/entity/LivingEntity;)V"))
    private void kronwerke$livingTick(com.bobmowzie.mowziesmobs.server.capability.LivingData data, LivingEntity entity, Operation<Void> original) {
        if (data != null) original.call(data, entity);
    }

    @WrapOperation(method = "onLivingTick", require = 0, at = @At(value = "INVOKE", target = "Lcom/bobmowzie/mowziesmobs/server/capability/AbilityData;tick(Lnet/minecraft/world/entity/LivingEntity;)V"))
    private void kronwerke$abilityTick(com.bobmowzie.mowziesmobs.server.capability.AbilityData data, LivingEntity entity, Operation<Void> original) {
        if (data != null) original.call(data, entity);
    }
}
