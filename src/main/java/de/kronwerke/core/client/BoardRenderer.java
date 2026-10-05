package de.kronwerke.core.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import de.kronwerke.core.obelisk.ObeliskBoardBlock;
import de.kronwerke.core.obelisk.ObeliskBoardBlockEntity;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;

/**
 * The leaderboard carved into the wall: gold letters with a dark edge below and to the right,
 * so they read as cut into the stone. Drawn by the anchor slab across the whole wall.
 */
public class BoardRenderer implements BlockEntityRenderer<ObeliskBoardBlockEntity> {
    private static final int GOLD = 0xFFd8ad55;
    private static final int GOLD_SOFT = 0xFFb89048;
    private static final int CUT = 0xFF0d0b10;
    private static final int RIM = 0x55ffe7a8;
    private final Font font;

    public BoardRenderer(BlockEntityRendererProvider.Context context) {
        this.font = context.getFont();
    }

    @Override
    public void render(ObeliskBoardBlockEntity be, float partial, PoseStack pose, MultiBufferSource buffer, int light, int overlay) {
        if (be.lines().isEmpty()) return;
        Direction facing = be.getBlockState().getValue(ObeliskBoardBlock.FACING);
        int w = be.width(), h = be.height();
        pose.pushPose();
        // origin at the centre of the anchor, turned so the front of the wall is +Z and the viewer's right is +X
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        pose.translate(-0.5, h, 0.5);
        float margin = 0.35f;
        float inner = w - 2 * margin;
        int lines = be.lines().size();
        // the heading is twice the size of a row; everything has to fit the height
        float rowH = Math.min(0.32f, (h - 2 * margin) / (lines + 2.2f));
        float scale = rowH / 10f;

        float y = margin;
        String head = be.lines().get(0);
        drawCentered(pose, buffer, head, w / 2f, y, Math.min(scale * 2f, inner / Math.max(1, font.width(head))), GOLD, 0.004f);
        y += rowH * 2.2f;
        if (lines > 1 && !be.lines().get(1).isEmpty()) {
            String sub = be.lines().get(1);
            drawCentered(pose, buffer, sub, w / 2f, y, Math.min(scale, inner / Math.max(1, font.width(sub))), GOLD_SOFT, 0.004f);
        }
        y += rowH * 1.3f;
        for (int i = 2; i < lines; i++) {
            String[] parts = be.lines().get(i).split("\t", 2);
            float rw = parts.length > 1 ? font.width(parts[1]) * scale : 0;
            int room = (int) ((inner - rw) / scale) - 8;
            String left = font.width(parts[0]) <= room ? parts[0] : font.plainSubstrByWidth(parts[0], Math.max(0, room - font.width("..."))) + "...";
            draw(pose, buffer, left, margin, y, scale, GOLD, 0.004f);
            if (rw > 0) draw(pose, buffer, parts[1], margin + inner - rw, y, scale, GOLD, 0.004f);
            y += rowH;
        }
        pose.popPose();
    }

    private void drawCentered(PoseStack pose, MultiBufferSource buffer, String s, float cx, float y, float scale, int colour, float z) {
        draw(pose, buffer, s, cx - font.width(s) * scale / 2f, y, scale, colour, z);
    }

    /** One carved line: the cut (dark, lower right), the rim (light, upper left), then the gold. */
    private void draw(PoseStack pose, MultiBufferSource buffer, String s, float x, float y, float scale, int colour, float z) {
        pose.pushPose();
        pose.translate(x, -y, z);
        pose.scale(scale, -scale, scale);
        Matrix4f m = pose.last().pose();
        font.drawInBatch(s, 0.6f, 0.6f, CUT, false, m, buffer, Font.DisplayMode.POLYGON_OFFSET, 0, LightTexture.FULL_BRIGHT);
        pose.translate(0, 0, 0.02f / scale * 0.05f);
        m = pose.last().pose();
        font.drawInBatch(s, -0.35f, -0.35f, RIM, false, m, buffer, Font.DisplayMode.POLYGON_OFFSET, 0, LightTexture.FULL_BRIGHT);
        pose.translate(0, 0, 0.02f / scale * 0.05f);
        m = pose.last().pose();
        font.drawInBatch(s, 0, 0, colour, false, m, buffer, Font.DisplayMode.POLYGON_OFFSET, 0, LightTexture.FULL_BRIGHT);
        pose.popPose();
    }

    @Override
    public AABB getRenderBoundingBox(ObeliskBoardBlockEntity be) {
        BlockPos p = be.getBlockPos();
        int r = Math.max(be.width(), be.height()) + 1;
        return new AABB(p.getX() - r, p.getY() - 1, p.getZ() - r, p.getX() + r + 1, p.getY() + be.height() + 1, p.getZ() + r + 1);
    }

    @Override
    public int getViewDistance() {
        return 64;
    }
}
