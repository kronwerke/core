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
        return HEAD + 40 + ROW_H * Math.max(1, data.entries().size()) + 12 + 18 + 8 + 14 + 12 + (confirming() ? ROW_H + 6 : 0);
    }

    private boolean confirming() {
        return !data.extra().confirm().isEmpty();
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
        name.setHint(Component.literal(tr("invite.hint", "Minecraft-Name")).withStyle(ChatFormatting.DARK_GRAY));
        name.setResponder(s -> invite.active = !s.isBlank() && data.used() < data.total());
        invite = FlatButton.primary(left + w - 12 - 84, fy, 84, tr("invite.check", "Prüfen"), () -> send("invite", name.getValue())).icon(new ItemStack(Items.NAME_TAG));
        invite.tip(data.used() < data.total() ? tr("invite.check_tip", "Zeigt das Konto zu dem Namen, eingeladen wird erst nach deinem Ja") : tr("invite.all_taken", "Alle Plätze sind vergeben"));
        invite.active = false;
        if (data.extra().canInvite()) {
            addRenderableWidget(name);
            addRenderableWidget(invite);
        }
        if (confirming()) {
            KwNetwork.Entry c = data.extra().confirm().get(0);
            fetchSkin(c);
            int cy = fy + 18 + 8 + 14;
            addRenderableWidget(FlatButton.primary(left + w - 12 - 84 - 6 - 76, cy + 3, 84, tr("invite.yes", "Ja, einladen"), () -> send("invite_confirm", c.id().toString())));
            addRenderableWidget(FlatButton.of(left + w - 12 - 76, cy + 3, 76, tr("invite.cancel", "Abbrechen"), () -> send("invite_cancel", "x")));
        }
        int y = listY();
        for (KwNetwork.Entry e : data.entries()) {
            // removing goes through Discord when inviting does (review R3-07)
            if (data.extra().canInvite()) addRenderableWidget(FlatButton.confirm(left + w - 12 - 76, y + 3, 76, tr("invite.remove", "Entfernen"), tr("invite.sure", "Sicher?"), FlatButton.Style.DANGER, () -> send("revoke", e.name())).tip(tr("invite.remove_tip", "Nimmt %s von der Whitelist", e.name()), tr("invite.remove_tip2", "Der Platz wird wieder frei")));
            y += ROW_H;
            fetchSkin(e);
        }
        if (data.extra().canInvite()) setInitialFocus(name);
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
        String yours = tr("invite.yours", "Deine Plätze");
        g.drawString(font, yours, left + 12, top + HEAD + 8, Ui.MUTED, false);
        // the slots as a row of small frames, filled ones in brass
        int sx = left + 12 + font.width(yours) + 10;
        int step = data.total() <= 0 ? 10 : Math.min(12, (left + w - 12 - sx) / Math.max(1, data.total()));
        if (step >= 5) {
            for (int i = 0; i < data.total(); i++) {
                int x = sx + i * step;
                boolean used = i < data.used();
                g.fill(x, top + HEAD + 7, x + step - 3, top + HEAD + 16, used ? Ui.GOLD : 0xFF2a2435);
                if (used) g.fill(x + 1, top + HEAD + 8, x + step - 4, top + HEAD + 9, Ui.GOLD_LIGHT);
                if (in(mouseX, mouseY, x, top + HEAD + 7, x + step - 3, top + HEAD + 16)) hover(used ? tr("invite.slot_used", "Platz %s, vergeben", i + 1) : tr("invite.slot_free", "Platz %s, frei", i + 1));
            }
        }
        g.drawString(font, tr("invite.count", "%s von %s Plätzen vergeben", data.used(), data.total()), left + 12, top + HEAD + 20, Ui.MUTED, false);
        Ui.ornament(g, left + 12, top + HEAD + 33, left + w - 12);
        int y = listY();
        int rows = Math.max(1, data.entries().size());
        Ui.inset(g, left + 10, y - 2, left + w - 10, y + ROW_H * rows + 2);
        if (data.entries().isEmpty()) {
            g.drawWordWrap(font, Component.literal(data.extra().canInvite() ? tr("invite.empty", "Noch niemand eingetragen. Name unten eingeben, Prüfen, Ja, fertig.") : tr("invite.empty_discord", "Noch niemand eingetragen.")).withStyle(ChatFormatting.DARK_GRAY), left + 14, y + 7, w - 28, 0xFFFFFF);
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
            if (in(mouseX, mouseY, left + 14, y, dx + 6, y + ROW_H)) hover(e.name(), e.online() ? tr("invite.online", "Online") : tr("invite.offline", "Offline"));
            y += ROW_H;
        }
        if (data.extra().canInvite()) {
            // the frame of the name field; the field itself has no border of its own
            Ui.inset(g, left + 12, name.getY() - 5, left + 12 + w - 24 - 90, name.getY() + 13);
            if (name.isFocused()) g.fill(left + 13, name.getY() + 12, left + 12 + w - 24 - 90 - 1, name.getY() + 13, Ui.GOLD);
        } else {
            g.drawWordWrap(font, Component.literal(tr("invite.discord", "Einladen geht über Discord: /whitelist add im Kronwerke-Discord. Hier siehst du deine Plätze und kannst sie freigeben."))
                    .withStyle(ChatFormatting.GRAY), left + 12, name.getY() - 4, w - 24, 0xFFFFFF);
        }
        if (!data.message().getString().isEmpty()) {
            g.drawWordWrap(font, data.message().copy().withStyle(data.error() ? ChatFormatting.RED : confirming() ? ChatFormatting.GOLD : ChatFormatting.GREEN),
                    left + 12, name.getY() + 19, w - 24, 0xFFFFFF);
        }
        if (confirming()) {
            KwNetwork.Entry c = data.extra().confirm().get(0);
            int cy = name.getY() - 5 + 18 + 8 + 14;
            Ui.inset(g, left + 10, cy - 2, left + w - 10, cy + ROW_H);
            PlayerSkin skin = SKINS.get(c.id());
            if (skin != null) PlayerFaceRenderer.draw(g, skin, left + 15, cy + 3, 16);
            else g.fill(left + 15, cy + 3, left + 31, cy + 19, 0xFF1a1620);
            g.drawString(font, Ui.fit(font, c.name(), w - 12 - 84 - 6 - 76 - 44), left + 38, cy + 7, Ui.GOLD, false);
        }
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if ((key == 257 || key == 335) && data.extra().canInvite() && name.isFocused() && invite.active) {
            send("invite", name.getValue());
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }
}
