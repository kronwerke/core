package de.kronwerke.core.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import de.kronwerke.core.portal.Travel;
import de.kronwerke.core.share.mek.FrequencyLayer;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.common.capabilities.energy.MachineEnergyContainer;
import mekanism.common.tile.TileEntityTeleporter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * A Mekanism teleporter whose frequency's other end stands on another server of the network
 * (see {@link FrequencyLayer}): the teleporter finds no block there, as that world only exists
 * here as a copy nobody visits. The players in its frame are moved to that server instead and
 * come out in front of the teleporter there; the energy is taken as for any teleport.
 */
@Pseudo
@Mixin(targets = "mekanism.common.tile.TileEntityTeleporter", remap = false)
public abstract class MekTeleporterRemoteMixin {
    @Shadow
    private MachineEnergyContainer<TileEntityTeleporter> energyContainer;
    @Shadow
    public Set<UUID> didTeleport;
    @Shadow
    public int teleDelay;

    @Shadow
    private List<Entity> getToTeleport(boolean sameDimension, Level destinationLevel) {
        throw new AssertionError();
    }

    @WrapOperation(method = "teleport", require = 0, at = @At(value = "INVOKE",
            target = "Lmekanism/common/util/WorldUtils;getTileEntity(Ljava/lang/Class;Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/entity/BlockEntity;",
            ordinal = 0))
    private BlockEntity kronwerke$remoteTeleporter(Class<?> type, BlockGetter world, BlockPos pos, Operation<BlockEntity> original) {
        BlockEntity found = original.call(type, world, pos);
        if (found != null || !(world instanceof Level target)) return found;
        GlobalPos place = GlobalPos.of(target.dimension(), pos);
        FrequencyLayer.Remote remote = FrequencyLayer.remote(place);
        if (remote == null) return null;
        Level here = ((BlockEntity) (Object) this).getLevel();
        if (here == null) return null;
        for (Entity e : getToTeleport(here == target, target)) {
            if (!(e instanceof ServerPlayer p) || Travel.frozen(p)) continue;
            long cost = TileEntityTeleporter.calculateEnergyCost(p, target, place);
            if (energyContainer.extract(cost, Action.SIMULATE, AutomationType.INTERNAL) < cost) continue;
            if (!Travel.moveTo(p, remote.peer(), FrequencyLayer.arrival(remote, p.getYRot()))) continue;
            energyContainer.extract(cost, Action.EXECUTE, AutomationType.INTERNAL);
            didTeleport.add(p.getUUID());
            teleDelay = 5;
        }
        return null;
    }
}
