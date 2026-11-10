package de.kronwerke.core.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.mojang.authlib.GameProfile;

import de.kronwerke.core.queue.JoinQueue;
import net.minecraft.server.players.PlayerList;

/** While the join queue is on, vanilla's max-players turns nobody away; the queue is the limit. */
@Mixin(PlayerList.class)
public abstract class PlayerListQueueMixin {

    @Inject(method = "canBypassPlayerLimit", at = @At("HEAD"), cancellable = true)
    private void kronwerke$queueIsTheLimit(GameProfile profile, CallbackInfoReturnable<Boolean> cir) {
        if (JoinQueue.on()) cir.setReturnValue(true);
    }
}
