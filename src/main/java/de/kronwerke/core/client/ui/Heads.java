package de.kronwerke.core.client.ui;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.world.level.block.entity.SkullBlockEntity;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Player heads by name for lists: asked from the skin service once per name, the way the tab
 * list gets them, and drawn as soon as they are there. Until then the place stays a dark
 * square, so a list never jumps.
 */
public final class Heads {
    private static final Map<String, PlayerSkin> SKINS = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> ASKED = new ConcurrentHashMap<>();

    private Heads() {
    }

    public static void draw(GuiGraphics g, String name, int x, int y, int size) {
        g.fill(x - 1, y - 1, x + size + 1, y + size + 1, Ui.SHADOW);
        PlayerSkin skin = SKINS.get(name);
        if (skin != null) {
            PlayerFaceRenderer.draw(g, skin, x, y, size);
            return;
        }
        g.fill(x, y, x + size, y + size, Ui.HOVER);
        if (name.isBlank() || name.length() > 16 || ASKED.putIfAbsent(name, true) != null) return;
        SkullBlockEntity.fetchGameProfile(name).thenAccept(opt -> {
            if (opt.isEmpty()) return;
            GameProfile profile = opt.get();
            Minecraft.getInstance().execute(() -> Minecraft.getInstance().getSkinManager().getOrLoad(profile)
                    .thenAccept(s -> Minecraft.getInstance().execute(() -> SKINS.put(name, s))));
        });
    }
}
