package de.kronwerke.core.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;

import de.kronwerke.core.camera.CameraClient;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;

/**
 * The camera does not draw the signs of the screenshot world either, nor the trapped chests
 * that hold the materials for the hand work in a scene.
 */
@Mixin(BlockEntityRenderDispatcher.class)
public class CameraSignMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private <E extends BlockEntity> void kronwerke$noSigns(E be, float partial, PoseStack pose, MultiBufferSource buffers, CallbackInfo ci) {
        if (CameraClient.ON && (be instanceof SignBlockEntity || be instanceof net.minecraft.world.level.block.entity.TrappedChestBlockEntity)) ci.cancel();
    }
}
