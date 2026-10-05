package de.kronwerke.core.client;

import com.mojang.authlib.GameProfile;
import de.kronwerke.core.net.KwNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The streamer menu: the slots as a row of heads, the players in them with name, online
 * dot and a remove button, and a field to invite the next one. Opened by /kw invite without
 * a name, or /kw menu. Heads come from the skin service the way the tab list draws them.
 */
public final class InviteScreen extends Screen {
    private static final int ROW_H = 24;
    private static final int W = 260;
    private static final Map<UUID, PlayerSkin> SKINS = new HashMap<>();
    private static final Map<UUID, Boolean> PENDING = new HashMap<>();

    private KwNetwork.SlotsPayload data;
    private EditBox name;
    private Button invite;
    private final List<Button> removes = new ArrayList<>();
    private int left, top;

    private InviteScreen(KwNetwork.SlotsPayload data) {
        super(Component.literal("Whitelist"));
        this.data = data;
    }

    /** Called with every slots packet: opens the menu or refreshes the open one. */
    public static void receive(KwNetwork.SlotsPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof InviteScreen s) {
            s.data = payload;
            s.rebuildWidgets();
        } else if (payload.open()) {
            mc.setScreen(new InviteScreen(payload));
        }
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = Math.max(20, (height - height()) / 2);
        name = new EditBox(font, left + 8, top + 48 + ROW_H * Math.max(1, data.entries().size()) + 10, W - 16 - 84, 20, Component.literal("Name"));
        name.setMaxLength(16);
        name.setHint(Component.literal("Minecraft-Name").withStyle(ChatFormatting.DARK_GRAY));
        name.setResponder(s -> invite.active = !s.isBlank() && data.used() < data.total());
        addRenderableWidget(name);
        invite = Button.builder(Component.literal("Einladen"), b -> send("invite", name.getValue())).bounds(left + W - 8 - 80, name.getY(), 80, 20).build();
        invite.active = false;
        addRenderableWidget(invite);
        addRenderableWidget(Button.builder(Component.literal("Schließen"), b -> onClose()).bounds(left + W - 8 - 80, name.getY() + 30, 80, 20).build());
        rebuild();
        setInitialFocus(name);
    }

    private int height() {
        return 48 + ROW_H * Math.max(1, data.entries().size()) + 10 + 20 + 30 + 20 + 16;
    }

    private void rebuild() {
        removes.forEach(this::removeWidget);
        removes.clear();
        int y = top + 48;
        for (KwNetwork.Entry e : data.entries()) {
            Button b = Button.builder(Component.literal("Entfernen"), bt -> send("revoke", e.name())).bounds(left + W - 8 - 70, y + 2, 70, 20).build();
            removes.add(b);
            addRenderableWidget(b);
            y += ROW_H;
            fetchSkin(e);
        }
        if (invite != null) invite.active = name != null && !name.getValue().isBlank() && data.used() < data.total();
    }

    private void send(String action, String who) {
        if (who == null || who.isBlank()) return;
        PacketDistributor.sendToServer(new KwNetwork.ActionPayload(action, who.trim()));
        if (action.equals("invite") && name != null) name.setValue("");
    }

    private static void fetchSkin(KwNetwork.Entry e) {
        if (SKINS.containsKey(e.id()) || PENDING.containsKey(e.id())) return;
        PENDING.put(e.id(), true);
        SkullBlockEntity.fetchGameProfile(e.name()).thenAccept(opt -> {
            GameProfile profile = opt.orElse(new GameProfile(e.id(), e.name()));
            Minecraft.getInstance().execute(() -> {
                Minecraft.getInstance().getSkinManager().getOrLoad(profile).thenAccept(skin -> Minecraft.getInstance().execute(() -> {
                    SKINS.put(e.id(), skin);
                    PENDING.remove(e.id());
                }));
            });
        });
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partial) {
        // Screen.render draws this first and the buttons after it, so the panel lives here
        renderTransparentBackground(g);
        drawPanel(g);
    }

    private void drawPanel(GuiGraphics g) {
        int h = height();
        g.fill(left - 1, top - 1, left + W + 1, top + h + 1, 0xFF3a3146);
        g.fill(left, top, left + W, top + h, 0xF0120f18);
        g.drawString(font, Component.literal("Deine Whitelist").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), left + 8, top + 8, 0xFFFFFF);
        // the slots as a row of squares, when they fit next to the title
        int titleEnd = left + 8 + font.width(Component.literal("Deine Whitelist").withStyle(ChatFormatting.BOLD)) + 10;
        int step = data.total() <= 0 ? 10 : Math.min(10, (left + W - 8 - titleEnd) / data.total());
        if (step >= 4) {
            int sx = left + W - 8 - data.total() * step;
            for (int i = 0; i < data.total(); i++) {
                int c = i < data.used() ? 0xFFd4a24a : 0xFF3a3146;
                g.fill(sx + i * step, top + 9, sx + i * step + step - 3, top + 16, c);
            }
        }
        g.drawString(font, Component.literal(data.used() + " von " + data.total() + " Plätzen vergeben").withStyle(ChatFormatting.GRAY), left + 8, top + 24, 0xFFFFFF);
        int y = top + 48;
        if (data.entries().isEmpty()) {
            g.drawWordWrap(font, Component.literal("Noch niemand eingetragen. Name unten eingeben, Einladen, fertig.").withStyle(ChatFormatting.DARK_GRAY), left + 8, y + 3, W - 16, 0xFFFFFF);
        }
        for (KwNetwork.Entry e : data.entries()) {
            PlayerSkin skin = SKINS.get(e.id());
            if (skin != null) {
                PlayerFaceRenderer.draw(g, skin, left + 8, y + 2, 20);
            } else {
                g.fill(left + 8, y + 2, left + 28, y + 22, 0xFF2a2435);
            }
            String shown = de.kronwerke.core.client.ui.Ui.fit(font, e.name(), W - 8 - 70 - 34 - 20);
            g.drawString(font, shown, left + 34, y + 8, 0xFFFFFF);
            int dot = e.online() ? 0xFF55ff55 : 0xFF555555;
            g.fill(left + 34 + font.width(shown) + 6, y + 10, left + 34 + font.width(shown) + 11, y + 15, dot);
            y += ROW_H;
        }
        if (!data.message().isEmpty()) {
            g.drawWordWrap(font, Component.literal(data.message()).withStyle(data.error() ? ChatFormatting.RED : ChatFormatting.GREEN), left + 8, name.getY() + 30 + 5, W - 16 - 90, 0xFFFFFF);
        }
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if ((key == 257 || key == 335) && name.isFocused() && invite.active) {
            send("invite", name.getValue());
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
