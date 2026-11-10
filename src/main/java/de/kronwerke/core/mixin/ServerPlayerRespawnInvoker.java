package de.kronwerke.core.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Optional;

/** Vanilla's check of a bed or anchor, for a player who respawns on another server. */
@Mixin(ServerPlayer.class)
public interface ServerPlayerRespawnInvoker {
    @Invoker("findRespawnAndUseSpawnBlock")
    static Optional<ServerPlayer.RespawnPosAngle> kronwerke$find(ServerLevel level, BlockPos pos, float angle, boolean forced, boolean keepInventory) {
        throw new AssertionError();
    }
}
