package de.kronwerke.core.probe;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import de.kronwerke.core.KronwerkeCore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Quaternionf;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Mobs as the game draws them, cut out cleanly, for the website. The probe line
 * {@code entities LIST SIZE} (in a world) opens this screen. Each line of LIST is
 * {@code name entity_id [yaw] [tilt] [snbt]}: the mob stands in a SIZE pixel cell, turned yaw
 * degrees from facing the viewer and seen tilt degrees from above, once on black and once on
 * white, and each pixel's colour and transparency come from the two pictures. The picture is
 * cropped to the mob and written to {@code probe/entities/<name>.png}.
 */
final class EntityExport extends Screen {
    private record Shot(String name, Entity entity, float yaw, float tilt) {
    }

    private final List<Shot> shots = new ArrayList<>();
    private final Path out;
    private final int size;
    private int index, phase, frames;
    private NativeImage black;

    EntityExport(Path list, Path out, int size) throws Exception {
        super(Component.literal("entities"));
        this.out = out;
        this.size = size;
        Minecraft mc = Minecraft.getInstance();
        List<String> missing = new ArrayList<>();
        for (String line : Files.readAllLines(list)) {
            String[] p = line.strip().split(" +", 5);
            if (p.length < 2 || p[0].startsWith("#")) continue;
            try {
                EntityType<?> type = EntityType.byString(p[1]).orElseThrow();
                Entity e = type.create(mc.level);
                if (p.length > 4) {
                    CompoundTag tag = e.saveWithoutId(new CompoundTag());
                    tag.merge(TagParser.parseTag(p[4]));
                    e.load(tag);
                }
                e.setPos(mc.player.position());
                shots.add(new Shot(p[0], e, p.length > 2 ? Float.parseFloat(p[2]) : 25, p.length > 3 ? Float.parseFloat(p[3]) : 8));
            } catch (Throwable t) {
                missing.add(p[0] + " " + t);
            }
        }
        Files.createDirectories(out);
        Files.write(out.resolve("missing.txt"), missing);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        Minecraft mc = Minecraft.getInstance();
        double gs = mc.getWindow().getGuiScale();
        g.fill(0, 0, width, height, phase == 0 ? 0xFF000000 : 0xFFFFFFFF);
        if (index >= shots.size()) return;
        Shot s = shots.get(index);
        Entity e = s.entity;
        float h = Math.max(0.4f, e.getBbHeight()), w = Math.max(0.4f, e.getBbWidth());
        // room for wings, tails and arms beside the hit box
        float scale = size * 0.62f / Math.max(h, w * 1.5f);
        float yaw = 180 + s.yaw;
        e.setYRot(yaw);
        e.yRotO = yaw;
        e.setXRot(0);
        e.xRotO = 0;
        if (e instanceof LivingEntity l) {
            l.yBodyRot = yaw;
            l.yBodyRotO = yaw;
            l.yHeadRot = yaw;
            l.yHeadRotO = yaw;
        }
        Quaternionf pose = new Quaternionf().rotateZ((float) Math.PI).mul(new Quaternionf().rotateX((float) Math.toRadians(-s.tilt)));
        g.pose().pushPose();
        g.pose().scale((float) (1 / gs), (float) (1 / gs), 1);
        // far in front of the background, so no part of a large mob ends up behind it
        g.pose().translate(size / 2f, size / 2f + h * scale / 2f, 4000);
        g.pose().scale(scale, scale, -scale);
        g.pose().mulPose(pose);
        Lighting.setupForEntityInInventory();
        EntityRenderDispatcher d = mc.getEntityRenderDispatcher();
        d.overrideCameraOrientation(new Quaternionf(pose).conjugate().rotateY((float) Math.PI));
        d.setRenderShadow(false);
        try {
            RenderSystem.runAsFancy(() -> d.render(e, 0, 0, 0, 0, 1, g.pose(), g.bufferSource(), 15728880));
            g.flush();
        } catch (Throwable t) {
            KronwerkeCore.LOGGER.warn("entity {}: {}", s.name, t.toString());
        }
        d.setRenderShadow(true);
        g.pose().popPose();
        Lighting.setupFor3DItems();
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partial) {
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    void frame() {
        if (index >= shots.size()) {
            Minecraft.getInstance().setScreen(null);
            return;
        }
        if (++frames < 3) return;
        frames = 0;
        Minecraft mc = Minecraft.getInstance();
        NativeImage shot = Screenshot.takeScreenshot(mc.getMainRenderTarget());
        if (phase == 0) {
            black = shot;
            phase = 1;
            return;
        }
        int n = Math.min(size, Math.min(shot.getWidth(), shot.getHeight()));
        try (NativeImage white = shot; NativeImage dark = black; NativeImage full = new NativeImage(n, n, true)) {
            int x0 = n, y0 = n, x1 = -1, y1 = -1;
            for (int y = 0; y < n; y++) {
                for (int x = 0; x < n; x++) {
                    int b = dark.getPixelRGBA(x, y), w = white.getPixelRGBA(x, y);
                    int br = b & 0xFF, bg = (b >> 8) & 0xFF, bb = (b >> 16) & 0xFF;
                    int wr = w & 0xFF, wg = (w >> 8) & 0xFF, wb = (w >> 16) & 0xFF;
                    int a = Math.max(0, Math.min(255, 255 - Math.min(wr - br, Math.min(wg - bg, wb - bb))));
                    if (a < 6) {
                        full.setPixelRGBA(x, y, 0);
                        continue;
                    }
                    int r = Math.min(255, br * 255 / a), gg = Math.min(255, bg * 255 / a), bl = Math.min(255, bb * 255 / a);
                    full.setPixelRGBA(x, y, (a << 24) | (bl << 16) | (gg << 8) | r);
                    x0 = Math.min(x0, x);
                    y0 = Math.min(y0, y);
                    x1 = Math.max(x1, x);
                    y1 = Math.max(y1, y);
                }
            }
            if (x1 >= x0) {
                try (NativeImage crop = new NativeImage(x1 - x0 + 1, y1 - y0 + 1, true)) {
                    for (int y = y0; y <= y1; y++) for (int x = x0; x <= x1; x++) crop.setPixelRGBA(x - x0, y - y0, full.getPixelRGBA(x, y));
                    crop.writeToFile(out.resolve(shots.get(index).name + ".png"));
                }
            }
        } catch (Exception e) {
            KronwerkeCore.LOGGER.warn("entities: {}", e.toString());
        }
        black = null;
        phase = 0;
        index++;
        if (index >= shots.size()) {
            try {
                Files.writeString(out.resolve("done.txt"), shots.size() + " entities\n");
            } catch (Exception ignored) {
            }
        }
    }
}
