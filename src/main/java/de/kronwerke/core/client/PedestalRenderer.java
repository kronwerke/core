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
        if (be.championId() != null) champion(be, time, pose, buffer, light);
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

    private final java.util.Map<java.util.UUID, net.minecraft.world.item.component.ResolvableProfile> profiles = new java.util.HashMap<>();
    private final net.minecraft.client.model.SkullModelBase skull = new net.minecraft.client.model.SkullModel(Minecraft.getInstance().getEntityModels().bakeLayer(net.minecraft.client.model.geom.ModelLayers.PLAYER_HEAD));

    /** The head of the pillar's champion, turning slowly above the lines, with their name under it. */
    private void champion(ObeliskPedestalBlockEntity be, float time, PoseStack pose, MultiBufferSource buffer, int light) {
        java.util.UUID id = be.championId();
        net.minecraft.world.item.component.ResolvableProfile profile = profiles.get(id);
        if (profile == null) {
            profile = new net.minecraft.world.item.component.ResolvableProfile(new com.mojang.authlib.GameProfile(id, be.champion().isEmpty() ? "" : be.champion()));
            profiles.put(id, profile);
            // the skin arrives in the background; until then the head wears the default
            profile.resolve().thenAccept(r -> Minecraft.getInstance().execute(() -> profiles.put(id, r)));
            profile = profiles.get(id);
        }
        pose.pushPose();
        pose.translate(0.5, 3.3 + Math.sin(time / 15.0 + 1) * 0.04, 0.5);
        pose.scale(0.6f, 0.6f, 0.6f);
        pose.translate(-0.5, 0, -0.5);
        net.minecraft.client.renderer.RenderType type = net.minecraft.client.renderer.blockentity.SkullBlockRenderer.getRenderType(net.minecraft.world.level.block.SkullBlock.Types.PLAYER, profile);
        net.minecraft.client.renderer.blockentity.SkullBlockRenderer.renderSkull(null, (time * 1.5f) % 360, 0f, pose, buffer, LightTexture.FULL_BRIGHT, skull, type);
        pose.popPose();
        if (!be.champion().isEmpty()) {
            pose.pushPose();
            pose.translate(0.5, 3.17, 0.5);
            pose.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
            pose.scale(0.028f, -0.028f, 0.028f);
            Matrix4f m = pose.last().pose();
            String s = be.champion();
            font.drawInBatch(s, -font.width(s) / 2f, 0, 0xFFF6D68C, false, m, buffer, Font.DisplayMode.NORMAL, 0x60000000, LightTexture.FULL_BRIGHT);
            pose.popPose();
        }
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
        // a hollow column: eight sheets standing on a ring around the item and the lines, each bright
        // along its middle, so the text in the centre stays readable through them
        float r = 0.46f, w = 0.26f;
        for (int k = 0; k < 8; k++) {
            float a = k * Mth.TWO_PI / 8 + time / 120f;
            float cx = Mth.cos(a) * r, cz = Mth.sin(a) * r;
            float tx = -Mth.sin(a) * w, tz = Mth.cos(a) * w;
            sheet(vc, m, cx - tx, cz - tz, cx + tx, cz + tz, cx, cz, height, c, alpha * 0.6f);
        }
        // the cap: a brighter ring where the column ends
        float capA = alpha * 0.9f;
        for (int k = 0; k < 16; k++) {
            float a0 = k * Mth.TWO_PI / 16, a1 = (k + 1) * Mth.TWO_PI / 16;
            vc.addVertex(m, Mth.cos(a0) * (r - 0.1f), height, Mth.sin(a0) * (r - 0.1f)).setColor(c[0], c[1], c[2], 0f);
            vc.addVertex(m, Mth.cos(a1) * (r - 0.1f), height, Mth.sin(a1) * (r - 0.1f)).setColor(c[0], c[1], c[2], 0f);
            vc.addVertex(m, Mth.cos(a1) * r, height, Mth.sin(a1) * r).setColor(c[0], c[1], c[2], capA);
            vc.addVertex(m, Mth.cos(a0) * r, height, Mth.sin(a0) * r).setColor(c[0], c[1], c[2], capA);
        }
        pose.popPose();
    }

    private static void sheet(VertexConsumer vc, Matrix4f m, float x0, float z0, float x1, float z1, float mx, float mz, float h, float[] c, float a) {
        // left half: edge clear, middle bright
        vc.addVertex(m, x0, 0, z0).setColor(c[0], c[1], c[2], 0f);
        vc.addVertex(m, mx, 0, mz).setColor(c[0], c[1], c[2], a);
        vc.addVertex(m, mx, h, mz).setColor(c[0], c[1], c[2], a * 0.15f);
        vc.addVertex(m, x0, h, z0).setColor(c[0], c[1], c[2], 0f);
        // right half
        vc.addVertex(m, mx, 0, mz).setColor(c[0], c[1], c[2], a);
        vc.addVertex(m, x1, 0, z1).setColor(c[0], c[1], c[2], 0f);
        vc.addVertex(m, x1, h, z1).setColor(c[0], c[1], c[2], 0f);
        vc.addVertex(m, mx, h, mz).setColor(c[0], c[1], c[2], a * 0.15f);
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
