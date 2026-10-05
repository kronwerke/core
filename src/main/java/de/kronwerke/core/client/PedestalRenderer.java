package de.kronwerke.core.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import de.kronwerke.core.obelisk.ObeliskPedestalBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Matrix4f;

/**
 * The item on a pedestal, turning slowly above the plate, with a short swell when a new item
 * arrives, and the two lines above it that always face the viewer.
 */
public class PedestalRenderer implements BlockEntityRenderer<ObeliskPedestalBlockEntity> {
    private final Font font;

    public PedestalRenderer(BlockEntityRendererProvider.Context context) {
        this.font = context.getFont();
    }

    @Override
    public void render(ObeliskPedestalBlockEntity be, float partial, PoseStack pose, MultiBufferSource buffer, int light, int overlay) {
        if (be.getLevel() == null) return;
        float time = be.getLevel().getGameTime() + partial;
        if (!be.item().isEmpty()) {
            float since = time - be.changedAt();
            float swell = since < 20 ? 1.0f + 0.5f * (1.0f - since / 20.0f) : 1.0f;
            pose.pushPose();
            pose.translate(0.5, 1.35 + Math.sin(time / 12.0) * 0.06, 0.5);
            pose.mulPose(Axis.YP.rotationDegrees(time * 2.0f % 360));
            pose.scale(0.75f * swell, 0.75f * swell, 0.75f * swell);
            Minecraft.getInstance().getItemRenderer().renderStatic(be.item(), ItemDisplayContext.FIXED, LightTexture.FULL_BRIGHT, overlay, pose, buffer, be.getLevel(), 0);
            pose.popPose();
        }
        if (be.title().isEmpty() && be.line().isEmpty()) return;
        pose.pushPose();
        pose.translate(0.5, 2.4, 0.5);
        pose.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
        pose.scale(0.03f, -0.03f, 0.03f);
        Matrix4f m = pose.last().pose();
        if (!be.title().isEmpty()) {
            float x = -font.width(be.title()) / 2f;
            font.drawInBatch(be.title(), x, -10, 0xFFd4a24a, false, m, buffer, Font.DisplayMode.NORMAL, 0x60000000, LightTexture.FULL_BRIGHT);
        }
        if (!be.line().isEmpty()) {
            float x = -font.width(be.line()) / 2f;
            font.drawInBatch(be.line(), x, 1, 0xFFEDE6F5, false, m, buffer, Font.DisplayMode.NORMAL, 0x60000000, LightTexture.FULL_BRIGHT);
        }
        pose.popPose();
    }

    @Override
    public int getViewDistance() {
        return 48;
    }
}
