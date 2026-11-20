package de.kronwerke.core.probe;

import com.mojang.blaze3d.platform.NativeImage;
import de.kronwerke.core.KronwerkeCore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Item icons exactly as the game draws them in an inventory, for the quest book on the website.
 * The probe line {@code icons LIST SIZE} opens this screen; it draws the items of LIST (one id
 * per line) in a grid of SIZE pixel cells, once on black and once on white, and works out each
 * pixel's colour and transparency from the two pictures. Every icon lands in
 * {@code probe/icons/<namespace>/<path>.png}; ids that are no item are listed in missing.txt.
 */
final class IconExport extends Screen {
    private final List<ItemStack> stacks = new ArrayList<>();
    private final List<String> ids = new ArrayList<>();
    private final Path out;
    private final int size;
    private int start, phase, frames;
    private NativeImage black;
    /** Items whose renderer throws (most want a world): left out, and the batch is drawn again. */
    private final java.util.Set<Integer> bad = new java.util.HashSet<>();
    private boolean dirty;
    private final long began = System.currentTimeMillis();

    IconExport(Path list, Path out, int size) throws Exception {
        super(Component.literal("icons"));
        this.out = out;
        this.size = size;
        List<String> missing = new ArrayList<>();
        for (String line : Files.readAllLines(list)) {
            String id = line.strip();
            if (id.isEmpty()) continue;
            ResourceLocation rl = ResourceLocation.tryParse(id);
            Item item = rl == null ? Items.AIR : BuiltInRegistries.ITEM.get(rl);
            if (item == Items.AIR) {
                missing.add(id);
                continue;
            }
            ids.add(id);
            stacks.add(new ItemStack(item));
        }
        Files.createDirectories(out);
        Files.write(out.resolve("missing.txt"), missing);
    }

    private int cols() {
        return Math.max(1, Minecraft.getInstance().getWindow().getWidth() / size);
    }

    private int perBatch() {
        return cols() * Math.max(1, Minecraft.getInstance().getWindow().getHeight() / size);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        Minecraft mc = Minecraft.getInstance();
        double gs = mc.getWindow().getGuiScale();
        g.fill(0, 0, width, height, phase == 0 ? 0xFF000000 : 0xFFFFFFFF);
        int cols = cols(), end = Math.min(stacks.size(), start + perBatch());
        float scale = size / 16f;
        for (int i = start; i < end; i++) {
            if (bad.contains(i)) continue;
            int n = i - start;
            g.pose().pushPose();
            // window pixels, then one item per cell
            g.pose().scale((float) (1 / gs), (float) (1 / gs), 1);
            g.pose().translate((n % cols) * size, (n / cols) * size, 0);
            g.pose().scale(scale, scale, 1);
            try {
                g.renderItem(stacks.get(i), 0, 0);
            } catch (Throwable t) {
                // the pose stack and the buffers are in an unknown state now: this frame is lost
                KronwerkeCore.LOGGER.warn("icon {}: {}", ids.get(i), t.toString());
                bad.add(i);
                dirty = true;
                return;
            }
            g.pose().popPose();
        }
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partial) {
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** After each frame: wait a few frames per picture so textures and animations settle. */
    void frame() {
        if (dirty) {
            dirty = false;
            frames = 0;
            phase = 0;
            if (black != null) black.close();
            black = null;
            return;
        }
        if (++frames < 4) return;
        frames = 0;
        Minecraft mc = Minecraft.getInstance();
        NativeImage shot = Screenshot.takeScreenshot(mc.getMainRenderTarget());
        if (phase == 0) {
            black = shot;
            phase = 1;
            return;
        }
        try (NativeImage white = shot; NativeImage dark = black) {
            int cols = cols(), end = Math.min(stacks.size(), start + perBatch());
            for (int i = start; i < end; i++) {
                int n = i - start, ox = (n % cols) * size, oy = (n / cols) * size;
                boolean any = false;
                try (NativeImage icon = new NativeImage(size, size, true)) {
                    for (int y = 0; y < size; y++) {
                        for (int x = 0; x < size; x++) {
                            int b = dark.getPixelRGBA(ox + x, oy + y), w = white.getPixelRGBA(ox + x, oy + y);
                            int br = b & 0xFF, bg = (b >> 8) & 0xFF, bb = (b >> 16) & 0xFF;
                            int wr = w & 0xFF, wg = (w >> 8) & 0xFF, wb = (w >> 16) & 0xFF;
                            int diff = ((wr - br) + (wg - bg) + (wb - bb)) / 3;
                            int a = Math.max(0, Math.min(255, 255 - diff));
                            if (a == 0) {
                                icon.setPixelRGBA(x, y, 0);
                                continue;
                            }
                            any = true;
                            int r = Math.min(255, br * 255 / a), gg = Math.min(255, bg * 255 / a), bl = Math.min(255, bb * 255 / a);
                            icon.setPixelRGBA(x, y, (a << 24) | (bl << 16) | (gg << 8) | r);
                        }
                    }
                    if (!any) continue;
                    String id = ids.get(i);
                    int c = id.indexOf(':');
                    Path file = out.resolve(id.substring(0, c)).resolve(id.substring(c + 1) + ".png");
                    Files.createDirectories(file.getParent());
                    icon.writeToFile(file);
                }
            }
        } catch (Exception e) {
            KronwerkeCore.LOGGER.warn("icons: {}", e.toString());
        }
        black = null;
        phase = 0;
        start += perBatch();
        if (start >= stacks.size()) {
            try {
                List<String> failed = new ArrayList<>();
                for (int i : bad) failed.add(ids.get(i));
                Files.write(out.resolve("failed.txt"), failed);
                Files.writeString(out.resolve("done.txt"), stacks.size() + " icons in " + (System.currentTimeMillis() - began) / 1000 + " s\n");
            } catch (Exception ignored) {
            }
            mc.setScreen(null);
        }
    }
}
