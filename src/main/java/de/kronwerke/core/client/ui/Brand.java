package de.kronwerke.core.client.ui;

import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * The logo's lettering as a picture, for the places of the game that carry the name: the
 * server list entry, the join screen, the header of Core's windows.
 *
 * <p>The logo is painted, not pixel art, so it is drawn smaller than its file with smoothing
 * (the textures' .mcmeta says blur). Smoothing without mipmaps only looks right down to about
 * half size, which is why there are two files and the smaller one is taken when it suffices.
 */
public final class Brand {

    private Brand() {
    }

    private static final ResourceLocation BIG = ResourceLocation.fromNamespaceAndPath("kronwerke", "textures/gui/brand/wordmark.png");
    private static final ResourceLocation SMALL = ResourceLocation.fromNamespaceAndPath("kronwerke", "textures/gui/brand/wordmark_small.png");
    private static final int BIG_W = 664, BIG_H = 113, SMALL_W = 334, SMALL_H = 58;

    /** The lettering's width in the units of a height. */
    public static int width(int height) {
        return Math.round(height * BIG_W / (float) BIG_H);
    }

    /** Draws the lettering height units tall, in GUI pixels at the game's GUI scale. */
    public static void draw(GuiGraphics g, int x, int y, int height) {
        draw(g, x, y, height, Minecraft.getInstance().getWindow().getGuiScale(), 1f);
    }

    /**
     * @param pixelsPerUnit screen pixels per unit of the pose it is drawn in (the GUI scale, times
     *                      whatever the caller scaled the pose by)
     */
    public static void draw(GuiGraphics g, int x, int y, int height, double pixelsPerUnit, float alpha) {
        if (alpha <= 0.01f) return;
        int w = width(height);
        boolean small = w * pixelsPerUnit <= SMALL_W;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.setColor(1f, 1f, 1f, Math.min(1f, alpha));
        if (small) g.blit(SMALL, x, y, w, height, 0f, 0f, SMALL_W, SMALL_H, SMALL_W, SMALL_H);
        else g.blit(BIG, x, y, w, height, 0f, 0f, BIG_W, BIG_H, BIG_W, BIG_H);
        g.setColor(1f, 1f, 1f, 1f);
    }
}
