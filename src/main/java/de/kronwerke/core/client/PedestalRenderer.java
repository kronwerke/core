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
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/**
 * The item on a pedestal, turning slowly above the plate, with a short swell when a new item
 * arrives, and the two lines above it that always face the viewer. Behind the item stands the
 * gauge: a column of light in the pillar's colour, as tall as the pillar is full, so the
 * balance of the three pillars can be read from across the square. The pillar furthest behind
 * flickers.
 */
public class PedestalRenderer implements BlockEntityRenderer<ObeliskPedestalBlockEntity> {
    private final Font font;

    public PedestalRenderer(BlockEntityRendererProvider.Context context) {
        this.font = context.getFont();
    }

    private static final float[][] PILLAR_COLOURS = {{0.60f, 0.63f, 0.66f}, {0.83f, 0.64f, 0.29f}, {0.60f, 0.44f, 0.84f}};

    @Override
    public void render(ObeliskPedestalBlockEntity be, float partial, PoseStack pose, MultiBufferSource buffer, int light, int overlay) {
        if (be.getLevel() == null) return;
        float time = be.getLevel().getGameTime() + partial;
        if (be.pillar() >= 0 && be.pillar() < 3) gauge(be, time, pose, buffer);
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
        pose.scale(0.038f, -0.038f, 0.038f);
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

    /** Four thin sheets of light around the item, crossing in the middle, that rise with the pillar's fill. */
    private void gauge(ObeliskPedestalBlockEntity be, float time, PoseStack pose, MultiBufferSource buffer) {
        float fill = Math.min(1f, be.percent() / 100f);
        if (fill <= 0.005f) return;
        float[] c = PILLAR_COLOURS[be.pillar()];
        float height = 0.6f + 3.0f * fill;
        float breathe = 0.85f + 0.15f * (float) Math.sin(time / 9f + be.pillar());
        float flicker = be.lowest() ? 0.55f + 0.45f * (float) Math.abs(Math.sin(time * 1.7f) * Math.sin(time * 0.37f)) : 1f;
        float alpha = 1.0f * breathe * flicker;
        VertexConsumer vc = buffer.getBuffer(RenderType.lightning());
        pose.pushPose();
        pose.translate(0.5, 1.02, 0.5);
        Matrix4f m = pose.last().pose();
        float w = 0.5f;
        // four crossing sheets, each bright along its middle and clear at its edges, fading towards the top
        for (int k = 0; k < 4; k++) {
            float a = k * Mth.PI / 4;
            float dx = Mth.cos(a) * w, dz = Mth.sin(a) * w;
            sheet(vc, m, -dx, -dz, dx, dz, height, c, alpha * (k % 2 == 0 ? 1f : 0.7f));
        }
        // the cap: a brighter sliver where the column ends
        float capA = alpha * 1.6f;
        vc.addVertex(m, -w, height, -w).setColor(c[0], c[1], c[2], 0f);
        vc.addVertex(m, w, height, -w).setColor(c[0], c[1], c[2], 0f);
        vc.addVertex(m, w, height, w).setColor(c[0], c[1], c[2], capA);
        vc.addVertex(m, -w, height, w).setColor(c[0], c[1], c[2], capA);
        pose.popPose();
    }

    private static void sheet(VertexConsumer vc, Matrix4f m, float x0, float z0, float x1, float z1, float h, float[] c, float a) {
        // left half: edge clear, middle bright
        vc.addVertex(m, x0, 0, z0).setColor(c[0], c[1], c[2], 0f);
        vc.addVertex(m, 0, 0, 0).setColor(c[0], c[1], c[2], a);
        vc.addVertex(m, 0, h, 0).setColor(c[0], c[1], c[2], a * 0.15f);
        vc.addVertex(m, x0, h, z0).setColor(c[0], c[1], c[2], 0f);
        // right half
        vc.addVertex(m, 0, 0, 0).setColor(c[0], c[1], c[2], a);
        vc.addVertex(m, x1, 0, z1).setColor(c[0], c[1], c[2], 0f);
        vc.addVertex(m, x1, h, z1).setColor(c[0], c[1], c[2], 0f);
        vc.addVertex(m, 0, h, 0).setColor(c[0], c[1], c[2], a * 0.15f);
    }

    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox(ObeliskPedestalBlockEntity be) {
        net.minecraft.core.BlockPos p = be.getBlockPos();
        return new net.minecraft.world.phys.AABB(p.getX() - 1, p.getY(), p.getZ() - 1, p.getX() + 2, p.getY() + 5, p.getZ() + 2);
    }

    @Override
    public int getViewDistance() {
        return 48;
    }
}
