package de.kronwerke.core.client;

import com.mojang.blaze3d.vertex.PoseStack;
import de.kronwerke.core.obelisk.ObeliskTopBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/** The beam above the obelisk's crystal, drawn like a beacon's. */
public class ObeliskBeamRenderer implements BlockEntityRenderer<ObeliskTopBlockEntity> {
    private static final int COLOR = 0x7FE6FF;

    public ObeliskBeamRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(ObeliskTopBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight, int packedOverlay) {
        long gameTime = be.getLevel() == null ? 0 : be.getLevel().getGameTime();
        poseStack.pushPose();
        poseStack.translate(0.0, 2.0, 0.0);
        BeaconRenderer.renderBeaconBeam(poseStack, buffer, BeaconRenderer.BEAM_LOCATION, partialTick, 1.0f, gameTime, 0, 1024, COLOR, 0.18f, 0.22f);
        poseStack.popPose();
    }

    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox(ObeliskTopBlockEntity be) {
        BlockPos p = be.getBlockPos();
        return new net.minecraft.world.phys.AABB(p.getX(), p.getY(), p.getZ(), p.getX() + 1, 1024, p.getZ() + 1);
    }

    @Override
    public boolean shouldRenderOffScreen(ObeliskTopBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 256;
    }

    @Override
    public boolean shouldRender(ObeliskTopBlockEntity be, Vec3 cameraPos) {
        BlockPos p = be.getBlockPos();
        return cameraPos.distanceToSqr(p.getX(), cameraPos.y, p.getZ()) < 256 * 256;
    }
}
