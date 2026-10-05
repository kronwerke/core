package de.kronwerke.core.client;

import com.mojang.authlib.GameProfile;
import de.kronwerke.core.client.ui.FlatButton;
import de.kronwerke.core.client.ui.KwScreen;
import de.kronwerke.core.client.ui.Ui;
import de.kronwerke.core.net.KwNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The streamer menu: the slots as a row of heads, the players in them with name, online
 * dot and a remove button, and a field to invite the next one. Opened by /kw invite without
 * a name, or /kw menu. Heads come from the skin service the way the tab list draws them.
 */
public final class InviteScreen extends KwScreen {
    private static final int ROW_H = 26;
    private static final Map<UUID, PlayerSkin> SKINS = new HashMap<>();
    private static final Map<UUID, Boolean> PENDING = new HashMap<>();

    private KwNetwork.SlotsPayload data;
    private EditBox name;
    private FlatButton invite;

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
    protected int panelWidth() {
        return 300;
    }

    @Override
    protected int panelHeight() {
        return HEAD + 40 + ROW_H * Math.max(1, data.entries().size()) + 12 + 18 + 8 + 14 + 12;
    }

    private int listY() {
        return top + HEAD + 40;
    }

    @Override
    protected void init() {
        super.init();
        int fy = listY() + ROW_H * Math.max(1, data.entries().size()) + 12;
        name = new EditBox(font, left + 16, fy + 5, w - 24 - 90 - 8, 10, Component.literal("Name"));
        name.setBordered(false);
        name.setTextColor(Ui.TEXT);
        name.setMaxLength(16);
        name.setHint(Component.literal("Minecraft-Name").withStyle(ChatFormatting.DARK_GRAY));
        name.setResponder(s -> invite.active = !s.isBlank() && data.used() < data.total());
        addRenderableWidget(name);
        invite = FlatButton.primary(left + w - 12 - 84, fy, 84, "Einladen", () -> send("invite", name.getValue())).icon(new ItemStack(Items.NAME_TAG));
        invite.tip(data.used() < data.total() ? "Trägt den Namen in deinen nächsten freien Platz ein" : "Alle Plätze sind vergeben");
        invite.active = false;
        addRenderableWidget(invite);
        int y = listY();
        for (KwNetwork.Entry e : data.entries()) {
            addRenderableWidget(FlatButton.confirm(left + w - 12 - 76, y + 3, 76, "Entfernen", "Sicher?", FlatButton.Style.DANGER, () -> send("revoke", e.name())).tip("Nimmt " + e.name() + " von der Whitelist", "Der Platz wird wieder frei"));
            y += ROW_H;
            fetchSkin(e);
        }
        setInitialFocus(name);
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
    protected void drawHeaderRight(GuiGraphics g, int x1, int y) {
        String chip = data.used() + " / " + data.total();
        Ui.chip(g, font, chip, x1 - font.width(chip) - 8, y, data.used() < data.total() ? 0xFF2f6e3a : 0xFF8a6420);
    }

    @Override
    protected void drawContent(GuiGraphics g, int mouseX, int mouseY, float partial) {
        g.drawString(font, "Deine Plätze", left + 12, top + HEAD + 8, Ui.MUTED, false);
        // the slots as a row of small frames, filled ones in brass
        int sx = left + 12 + font.width("Deine Plätze") + 10;
        int step = data.total() <= 0 ? 10 : Math.min(12, (left + w - 12 - sx) / Math.max(1, data.total()));
        if (step >= 5) {
            for (int i = 0; i < data.total(); i++) {
                int x = sx + i * step;
                boolean used = i < data.used();
                g.fill(x, top + HEAD + 7, x + step - 3, top + HEAD + 16, used ? Ui.GOLD : 0xFF2a2435);
                if (used) g.fill(x + 1, top + HEAD + 8, x + step - 4, top + HEAD + 9, Ui.GOLD_LIGHT);
                if (in(mouseX, mouseY, x, top + HEAD + 7, x + step - 3, top + HEAD + 16)) hover(used ? "Platz " + (i + 1) + ", vergeben" : "Platz " + (i + 1) + ", frei");
            }
        }
        g.drawString(font, data.used() + " von " + data.total() + " Plätzen vergeben", left + 12, top + HEAD + 20, Ui.MUTED, false);
        Ui.ornament(g, left + 12, top + HEAD + 33, left + w - 12);
        int y = listY();
        int rows = Math.max(1, data.entries().size());
        Ui.inset(g, left + 10, y - 2, left + w - 10, y + ROW_H * rows + 2);
        if (data.entries().isEmpty()) {
            g.drawWordWrap(font, Component.literal("Noch niemand eingetragen. Name unten eingeben, Einladen, fertig.").withStyle(ChatFormatting.DARK_GRAY), left + 14, y + 7, w - 28, 0xFFFFFF);
        }
        for (KwNetwork.Entry e : data.entries()) {
            PlayerSkin skin = SKINS.get(e.id());
            Ui.slot(g, left + 14, y + 2);
            if (skin != null) {
                PlayerFaceRenderer.draw(g, skin, left + 15, y + 3, 16);
            } else {
                g.fill(left + 15, y + 3, left + 31, y + 19, 0xFF1a1620);
            }
            String shown = Ui.fit(font, e.name(), w - 12 - 76 - 44 - 20);
            g.drawString(font, shown, left + 38, y + 7, Ui.TEXT, false);
            int dx = left + 38 + font.width(shown) + 6;
            g.fill(dx, y + 9, dx + 5, y + 14, e.online() ? 0xFF55ff55 : Ui.DIM);
            if (in(mouseX, mouseY, left + 14, y, dx + 6, y + ROW_H)) hover(e.name(), e.online() ? "Online" : "Offline");
            y += ROW_H;
        }
        // the frame of the name field; the field itself has no border of its own
        Ui.inset(g, left + 12, name.getY() - 5, left + 12 + w - 24 - 90, name.getY() + 13);
        if (name.isFocused()) g.fill(left + 13, name.getY() + 12, left + 12 + w - 24 - 90 - 1, name.getY() + 13, Ui.GOLD);
        if (!data.message().isEmpty()) {
            g.drawWordWrap(font, Component.literal(data.message()).withStyle(data.error() ? ChatFormatting.RED : ChatFormatting.GREEN), left + 12, name.getY() + 19, w - 24, 0xFFFFFF);
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
}
