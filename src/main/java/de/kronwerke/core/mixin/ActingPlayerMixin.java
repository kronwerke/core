package de.kronwerke.core.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

import de.kronwerke.core.lock.ActingContext;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

/** A player's clicks in menus and uses on blocks (altars, rituals): the one told about a stage lock. */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ActingPlayerMixin {

    @Shadow
    public ServerPlayer player;

    @WrapMethod(method = "handleUseItemOn")
    private void kronwerke$useOn(ServerboundUseItemOnPacket packet, Operation<Void> original) {
        kronwerke$as(() -> original.call(packet));
    }

    @WrapMethod(method = "handleUseItem")
    private void kronwerke$use(ServerboundUseItemPacket packet, Operation<Void> original) {
        kronwerke$as(() -> original.call(packet));
    }

    @WrapMethod(method = "handleContainerClick")
    private void kronwerke$click(ServerboundContainerClickPacket packet, Operation<Void> original) {
        kronwerke$as(() -> original.call(packet));
    }

    @org.spongepowered.asm.mixin.Unique
    private void kronwerke$as(Runnable r) {
        // the handlers first run on the network thread and hand themselves to the server thread
        if (!this.player.server.isSameThread()) {
            r.run();
            return;
        }
        ServerPlayer before = ActingContext.player(this.player, false);
        try {
            r.run();
        } finally {
            ActingContext.restorePlayer(before);
        }
    }
}
