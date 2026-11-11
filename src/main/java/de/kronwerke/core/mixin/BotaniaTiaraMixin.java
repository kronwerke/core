package de.kronwerke.core.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

import de.kronwerke.core.perf.TiaraWearers;
import net.minecraft.world.entity.player.Player;

/** Skips Botania's per tick tiara check for players without a tiara and without its flight (see TiaraWearers). */
@Pseudo
@Mixin(targets = "vazkii.botania.common.item.equipment.bauble.FlugelTiaraItem", remap = false)
public abstract class BotaniaTiaraMixin {

    @Shadow
    @Final
    private static List<String> playersWithFlight;

    @WrapMethod(method = "updatePlayerFlyStatus")
    private static void kronwerke$onlyWearers(Player player, Operation<Void> original) {
        if (TiaraWearers.ready() && !player.level().isClientSide()) {
            // Botania's own key for a player: name, colon, client side or not
            boolean flight = playersWithFlight.contains(player.getGameProfile().getName() + ":false");
            if (TiaraWearers.skip(player, flight)) return;
        }
        original.call(player);
    }
}
