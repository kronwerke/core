package de.kronwerke.core.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import de.kronwerke.core.portal.Travel;
import de.kronwerke.core.share.mek.FrequencyLayer;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.energy.IEnergyContainer;
import mekanism.common.tile.TileEntityTeleporter;
import mekanism.common.util.StorageUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Mekanism's portable teleporter to a teleporter on another server of the network: as
 * {@link MekTeleporterRemoteMixin}, with the energy taken from the item.
 */
@Pseudo
@Mixin(targets = "mekanism.common.network.to_server.PacketPortableTeleporterTeleport", remap = false)
public abstract class MekPortableTeleporterRemoteMixin {
    @WrapOperation(method = "handle", require = 0, at = @At(value = "INVOKE",
            target = "Lmekanism/common/util/WorldUtils;getTileEntity(Ljava/lang/Class;Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/entity/BlockEntity;"))
    private BlockEntity kronwerke$remoteTeleporter(Class<?> type, BlockGetter world, BlockPos pos, Operation<BlockEntity> original,
                                                   @Local ServerPlayer player, @Local ItemStack stack, @Local GlobalPos coords) {
        BlockEntity found = original.call(type, world, pos);
        if (found != null) return found;
        FrequencyLayer.Remote remote = FrequencyLayer.remote(coords);
        if (remote == null || Travel.frozen(player)) return null;
        IEnergyContainer energy = null;
        long cost = 0;
        if (!player.isCreative()) {
            cost = TileEntityTeleporter.calculateEnergyCost(player, world instanceof Level l ? l : player.level(), coords);
            energy = StorageUtils.getEnergyContainer(stack, 0);
            if (energy == null || energy.extract(cost, Action.SIMULATE, AutomationType.MANUAL) < cost) return null;
        }
        player.closeContainer();
        if (player.isPassenger()) player.stopRiding();
        if (!Travel.moveTo(player, remote.peer(), FrequencyLayer.arrival(remote, player.getYRot()))) return null;
        if (energy != null) energy.extract(cost, Action.EXECUTE, AutomationType.MANUAL);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_TELEPORT, SoundSource.PLAYERS);
        return null;
    }
}
