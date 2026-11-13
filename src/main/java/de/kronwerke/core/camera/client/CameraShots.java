package de.kronwerke.core.camera.client;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.imageio.ImageIO;

import com.mojang.blaze3d.platform.NativeImage;

import de.kronwerke.core.KronwerkeCore;
import de.kronwerke.core.camera.CameraClient;
import de.kronwerke.core.camera.ShotPartPayload;
import de.kronwerke.core.camera.ShotPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.Screenshot;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The camera client's side: waits until the world around it has rendered, takes the frame
 * without HUD, scales it down (the frame is rendered larger, so the scaling smooths the
 * edges), and sends it to the server as PNG. Also keeps the camera connected.
 */
public final class CameraShots {
    private static ShotPayload job;
    private static int waited;
    private static int settledAt;
    private static boolean optionsSet;
    private static long offlineSince;
    private static String lastScreen = "";

    private CameraShots() {
    }

    public static void request(ShotPayload p) {
        if (!CameraClient.ON) return;
        job = p;
        waited = 0;
        settledAt = 0;
        Minecraft mc = Minecraft.getInstance();
        mc.options.hideGui = true;
        mc.options.fov().set(Math.max(30, Math.min(110, p.fov())));
    }

    private static boolean plainRender;

    /**
     * The camera and the test player draw without a graphics card. Flywheel's own drawing
     * (what Create uses for everything that turns) loses parts there: a shaft or a cogwheel
     * that turns was gone from the picture, a belt showed only its ends. With its backend off
     * Create draws the plain way, which is right everywhere. Once per world, after joining.
     */
    private static void plainRendering(Minecraft mc) {
        if (mc.player == null) {
            plainRender = false;
            return;
        }
        if (plainRender) return;
        plainRender = true;
        if (!net.neoforged.fml.ModList.get().isLoaded("flywheel")) return;
        try {
            net.neoforged.neoforge.client.ClientCommandHandler.runCommand("flywheel backend flywheel:off");
            KronwerkeCore.LOGGER.info("Camera: Flywheel backend off");
        } catch (RuntimeException e) {
            KronwerkeCore.LOGGER.warn("Camera: could not turn Flywheel's backend off: {}", e.toString());
        }
    }

    /** Every client tick, camera only. */
    public static void tick() {
        if (CameraClient.HEADLESS) plainRendering(Minecraft.getInstance());
        if (!CameraClient.ON) return;
        Minecraft mc = Minecraft.getInstance();
        if (!optionsSet) {
            optionsSet = true;
            mc.options.pauseOnLostFocus = false;
            mc.options.hideGui = true;
            mc.options.gamma().set(1.0); // "Bright": the grey boxes are lit evenly, no dark corners
        }
        String screen = mc.screen == null ? "-" : mc.screen.getClass().getSimpleName();
        if (!screen.equals(lastScreen)) {
            lastScreen = screen;
            KronwerkeCore.LOGGER.info("Camera screen: {}", screen);
        }
        reconnect(mc);
        closeScreens(mc);
        if (mc.level != null && mc.level.getGameTime() % 20 == 0) openAllStages();
        // always noon and dry in the camera's eyes: a run of ten minutes went from afternoon
        // into the night, and its pictures from light to dark
        if (mc.level != null) {
            mc.level.setDayTime(6000);
            mc.level.setRainLevel(0f);
            mc.level.setThunderLevel(0f);
        }
        if (job == null || mc.level == null || mc.player == null) return;
        waited++;
        // the sections around the camera have to be built first; at most 30 seconds
        boolean ready = around(mc) && mc.levelRenderer.hasRenderedAllSections() && waited >= job.settleTicks();
        if (!ready && waited < 600) return;
        // what arrived last (a belt that only now knows its length) gets its frames too
        if (ready && settledAt == 0) settledAt = waited;
        if (ready && waited - settledAt < 10) return;
        ShotPayload p = job;
        job = null;
        try {
            send(p, png(capture(mc), p.width(), p.height()));
        } catch (IOException | RuntimeException e) {
            KronwerkeCore.LOGGER.warn("The camera could not take shot {}: {}", p.id(), e.toString());
        }
    }

    /**
     * Whether the chunks around the camera are there. Right after a long jump nothing is being
     * built yet, so "all sections rendered" is true over an empty world: the first picture of
     * a run showed sky.
     */
    private static boolean around(Minecraft mc) {
        int cx = mc.player.chunkPosition().x, cz = mc.player.chunkPosition().z;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (!mc.level.getChunkSource().hasChunk(cx + dx, cz + dz)) return false;
            }
        }
        return true;
    }

    /**
     * Nothing of the HUD belongs in a picture. hideGui covers the game's own layers, not
     * those of mods: an area title drew "???" over two pictures.
     */
    public static void register() {
        if (!CameraClient.ON) return;
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.client.event.RenderGuiLayerEvent.Pre e) -> e.setCanceled(true));
    }

    private static BufferedImage capture(Minecraft mc) {
        try (NativeImage img = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            int w = img.getWidth(), h = img.getHeight();
            BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int abgr = img.getPixelRGBA(x, y);
                    int r = abgr & 0xFF, g = (abgr >> 8) & 0xFF, b = (abgr >> 16) & 0xFF;
                    out.setRGB(x, y, (r << 16) | (g << 8) | b);
                }
            }
            return out;
        }
    }

    /** Crops to the wanted aspect, scales down in halving steps, and encodes as PNG. */
    static byte[] png(BufferedImage src, int width, int height) throws IOException {
        double want = (double) width / height;
        int w = src.getWidth(), h = src.getHeight();
        int cw = w, ch = h;
        if ((double) w / h > want) cw = (int) Math.round(h * want);
        else ch = (int) Math.round(w / want);
        BufferedImage img = src.getSubimage((w - cw) / 2, (h - ch) / 2, cw, ch);
        while (img.getWidth() / 2 >= width && img.getHeight() / 2 >= height) img = scale(img, img.getWidth() / 2, img.getHeight() / 2);
        if (img.getWidth() != width || img.getHeight() != height) img = scale(img, width, height);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    private static BufferedImage scale(BufferedImage in, int w, int h) {
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(in, 0, 0, w, h, null);
        g.dispose();
        return out;
    }

    private static void send(ShotPayload p, byte[] png) {
        int total = (png.length + ShotPartPayload.MAX - 1) / ShotPartPayload.MAX;
        for (int i = 0; i < total; i++) {
            int from = i * ShotPartPayload.MAX;
            byte[] part = java.util.Arrays.copyOfRange(png, from, Math.min(png.length, from + ShotPartPayload.MAX));
            PacketDistributor.sendToServer(new ShotPartPayload(p.id(), i, total, part));
        }
    }

    /**
     * Screens a player would see in the world (the Origins choice on first join, a guide book,
     * a welcome window) would cover the picture: the camera closes them. Loading screens stay.
     */
    private static void closeScreens(Minecraft mc) {
        if (mc.level == null || mc.player == null || mc.screen == null) return;
        if (mc.screen instanceof net.minecraft.client.gui.screens.ReceivingLevelScreen
                || mc.screen instanceof net.minecraft.client.gui.screens.LevelLoadingScreen
                || mc.screen instanceof net.minecraft.client.gui.screens.ProgressScreen
                || mc.screen instanceof net.minecraft.client.gui.screens.GenericMessageScreen
                || mc.screen instanceof ConnectScreen
                || mc.screen instanceof DisconnectedScreen) return;
        mc.setScreen(null);
    }

    /**
     * The pictures show what a stage looks like once it is open: the camera sees every item
     * as unlocked (no "???" veils, no hidden names), whatever the server has opened so far.
     * Only on the camera's own client; the server's locks stay as they are.
     */
    private static void openAllStages() {
        if (!net.neoforged.fml.ModList.get().isLoaded("chapters")) return;
        java.util.Set<net.minecraft.resources.ResourceLocation> all = new java.util.HashSet<>(com.gabinx.chapters.stage.ClientStageCache.snapshot());
        int before = all.size();
        for (var m : java.util.List.of(com.gabinx.chapters.stage.ClientStageIndices.itemsView(), com.gabinx.chapters.stage.ClientStageIndices.fluidsView(),
                com.gabinx.chapters.stage.ClientStageIndices.chemicalsView(), com.gabinx.chapters.stage.ClientStageIndices.recipesView())) {
            for (var stages : m.values()) all.addAll(stages);
        }
        if (all.size() != before) com.gabinx.chapters.stage.ClientStageCache.set(all);
    }

    /** Back onto the server after a restart or a kick, every 30 seconds. */
    private static void reconnect(Minecraft mc) {
        boolean out = mc.level == null && (mc.screen instanceof TitleScreen || mc.screen instanceof DisconnectedScreen);
        if (!out) {
            offlineSince = 0;
            return;
        }
        long now = System.currentTimeMillis();
        if (offlineSince == 0) offlineSince = now;
        if (now - offlineSince < 30_000) return;
        offlineSince = now;
        String addr = System.getProperty("kronwerke.camera.server", "127.0.0.1:25565");
        ConnectScreen.startConnecting(new TitleScreen(), mc, ServerAddress.parseString(addr), new ServerData("Kronwerke", addr, ServerData.Type.OTHER), false, null);
    }
}
