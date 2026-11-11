package de.kronwerke.core.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;

import de.kronwerke.core.net.LockHintPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Shows why a machine, altar or ritual did not craft, where the player looks: a banner over
 * the machine's own window when it is open, otherwise text floating over the block for a few
 * seconds. Which window belongs to which block: the block the player right clicked just
 * before the window opened.
 */
public final class LockHints {

    private static final long SHOW_MS = 5000;

    private record Hint(Component text, long until) {}

    private static final Map<BlockPos, Hint> WORLD = new HashMap<>();
    private static Hint screenHint;
    private static BlockPos clicked, screenPos;
    private static long clickedAt;

    private LockHints() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener((PlayerInteractEvent.RightClickBlock e) -> {
            if (e.getLevel().isClientSide()) {
                clicked = e.getPos().immutable();
                clickedAt = System.currentTimeMillis();
            }
        });
        NeoForge.EVENT_BUS.addListener((ScreenEvent.Opening e) -> {
            if (e.getNewScreen() instanceof AbstractContainerScreen<?>) {
                screenPos = clicked != null && System.currentTimeMillis() - clickedAt < 2000 ? clicked : null;
                screenHint = null;
            }
        });
        NeoForge.EVENT_BUS.addListener((ScreenEvent.Closing e) -> {
            if (e.getScreen() instanceof AbstractContainerScreen<?>) {
                screenPos = null;
                screenHint = null;
            }
        });
        NeoForge.EVENT_BUS.addListener(LockHints::drawScreen);
        NeoForge.EVENT_BUS.addListener(LockHints::drawWorld);
    }

    public static void receive(LockHintPayload p) {
        Minecraft mc = Minecraft.getInstance();
        long until = System.currentTimeMillis() + SHOW_MS;
        boolean window = mc.screen instanceof AbstractContainerScreen<?>;
        if (p.screen() || (window && p.pos().equals(screenPos))) {
            if (window) {
                screenHint = new Hint(p.text(), until);
                return;
            }
            if (p.screen()) {
                if (mc.player != null) mc.player.displayClientMessage(p.text(), true);
                return;
            }
        }
        WORLD.put(p.pos(), new Hint(p.text(), until));
    }

    // ---- over the machine's window ----

    private static void drawScreen(ScreenEvent.Render.Post e) {
        if (screenHint == null || !(e.getScreen() instanceof AbstractContainerScreen<?> s)) return;
        long left = screenHint.until() - System.currentTimeMillis();
        if (left <= 0) {
            screenHint = null;
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        int width = Math.max(176, s.getXSize());
        List<FormattedCharSequence> lines = font.split(screenHint.text(), width - 16);
        int h = lines.size() * (font.lineHeight + 1) + 9;
        int x = s.getGuiLeft() + (s.getXSize() - width) / 2;
        int y = s.getGuiTop() - h - 4;
        if (y < 2) y = s.getGuiTop() + s.getYSize() + 4; // no room above: below the window
        float fade = Mth.clamp(Math.min(left, SHOW_MS - left) / 250f, 0f, 1f);
        int a = (int) (fade * 255);
        GuiGraphics g = e.getGuiGraphics();
        g.pose().pushPose();
        g.pose().translate(0, 0, 400); // over items and tooltips of the window
        g.fill(x, y, x + width, y + h, (Math.min(a, 235) << 24) | 0x1A1410);
        int border = (a << 24) | 0xC9533F;
        g.fill(x, y, x + width, y + 1, border);
        g.fill(x, y + h - 1, x + width, y + h, border);
        g.fill(x, y, x + 2, y + h, border);
        int ty = y + 5;
        for (FormattedCharSequence line : lines) {
            g.drawString(font, line, x + 8, ty, (Math.max(a, 4) << 24) | 0xFFFFFF, false);
            ty += font.lineHeight + 1;
        }
        g.pose().popPose();
    }

    // ---- floating over the block ----

    private static void drawWorld(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || WORLD.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        long now = System.currentTimeMillis();
        Vec3 cam = e.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        Font font = mc.font;
        List<BlockPos> done = new ArrayList<>();
        for (var entry : WORLD.entrySet()) {
            long left = entry.getValue().until() - now;
            if (left <= 0) {
                done.add(entry.getKey());
                continue;
            }
            BlockPos at = entry.getKey();
            double x = at.getX() + 0.5, y = at.getY() + 1.6, z = at.getZ() + 0.5;
            if (cam.distanceToSqr(x, y, z) > 24 * 24) continue;
            float fade = Mth.clamp(Math.min(left, SHOW_MS - left) / 250f, 0f, 1f);
            float rise = (SHOW_MS - left) / (float) SHOW_MS * 0.25f;
            PoseStack pose = new PoseStack();
            pose.mulPose(e.getModelViewMatrix());
            pose.translate(x - cam.x, y - cam.y + rise, z - cam.z);
            pose.mulPose(e.getCamera().rotation());
            pose.scale(0.025f, -0.025f, 0.025f);
            Matrix4f m = pose.last().pose();
            List<FormattedCharSequence> lines = font.split(entry.getValue().text(), 200);
            int bg = ((int) (fade * 0.55f * 255) << 24) | 0x1A1410;
            int fg = ((int) (Math.max(fade, 0.02f) * 255) << 24) | 0xFFFFFF;
            float ty = -lines.size() * (font.lineHeight + 1);
            for (FormattedCharSequence line : lines) {
                float w = font.width(line);
                font.drawInBatch(line, -w / 2, ty, fg, false, m, buffers, Font.DisplayMode.NORMAL, bg, LightTexture.FULL_BRIGHT);
                ty += font.lineHeight + 1;
            }
        }
        buffers.endBatch();
        done.forEach(WORLD::remove);
    }
}
