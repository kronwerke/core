package de.kronwerke.core.client;

import de.kronwerke.core.Text;
import de.kronwerke.core.client.ui.FlatButton;
import de.kronwerke.core.client.ui.KwScreen;
import de.kronwerke.core.client.ui.Ui;
import de.kronwerke.core.queue.QueuePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * The join queue, while the connection waits in its configuration phase: the place in the
 * queue as a big number, how full the network is, how long the player has waited, and a button
 * to leave. The server sends the place every two seconds and 0 when it is the player's turn;
 * then the join goes on by itself and Minecraft's loading screen takes over.
 */
public final class QueueScreen extends KwScreen {

    private static QueueScreen open;

    private QueuePayload state;
    private final Connection connection;
    private final long since = System.currentTimeMillis();
    private long turnAt;

    private QueueScreen(QueuePayload state, Connection connection) {
        super(Text.t("queue.title", "Warteschlange"));
        this.state = state;
        this.connection = connection;
    }

    public static void show(QueuePayload p, Connection connection) {
        Minecraft mc = Minecraft.getInstance();
        if (open != null && mc.screen == open) {
            open.update(p);
            return;
        }
        if (p.position() <= 0) return; // let in right away: nothing to show
        open = new QueueScreen(p, connection);
        mc.setScreen(open);
    }

    private void update(QueuePayload p) {
        state = p;
        if (p.position() <= 0 && turnAt == 0) turnAt = System.currentTimeMillis();
    }

    @Override
    protected int panelWidth() {
        return 360;
    }

    @Override
    protected int panelHeight() {
        return 200;
    }

    @Override
    protected boolean closable() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    protected void init() {
        super.init();
        addRenderableWidget(FlatButton.of(left + w / 2 - 60, top + h - 30, 120, Text.t("queue.leave", "Warteschlange verlassen").getString(), () -> {
            connection.disconnect(Text.t("queue.left", "Du hast die Warteschlange verlassen."));
        }));
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partial) {
        // no world behind the queue: an opaque ground first
        g.fill(0, 0, width, height, 0xFF0E0B12);
        super.renderBackground(g, mouseX, mouseY, partial);
    }

    @Override
    protected void drawContent(GuiGraphics g, int mouseX, int mouseY, float partial) {
        int cx = left + w / 2;
        int y = top + HEAD + 14;
        if (state.position() <= 0) {
            g.drawCenteredString(font, Text.t("queue.turn", "Du bist dran, gleich geht es los."), cx, y + 22, Ui.GREEN);
            return;
        }
        g.drawCenteredString(font, Text.t("queue.full", "Kronwerke ist gerade voll."), cx, y, Ui.MUTED);
        // the place, big and gold, breathing gently
        float pulse = 0.9f + 0.1f * Mth.sin((System.currentTimeMillis() - since) / 600f);
        String place = "#" + state.position();
        g.pose().pushPose();
        g.pose().translate(cx, y + 16, 0);
        g.pose().scale(3f * pulse, 3f * pulse, 1);
        g.drawCenteredString(font, place, 0, 0, Ui.GOLD);
        g.pose().popPose();
        g.drawCenteredString(font, Text.t("queue.place", "Platz %s von %s in der Warteschlange", state.position(), state.waiting()), cx, y + 50, Ui.TEXT);
        g.drawCenteredString(font, Text.t("queue.online", "%s von %s Spielern im Netzwerk", state.online(), state.limit()), cx, y + 63, Ui.MUTED);
        long s = (System.currentTimeMillis() - since) / 1000;
        int eta = state.etaSeconds();
        Component waitLine = eta <= 0 ? Text.t("queue.waited", "Du wartest seit %s:%s.", s / 60, String.format("%02d", s % 60))
                : Text.t("queue.eta", "Du wartest seit %s:%s, noch etwa %s Minuten.", s / 60, String.format("%02d", s % 60), Math.max(1, Math.round(eta / 60.0)));
        g.drawCenteredString(font, waitLine, cx, y + 80, Ui.DIM);
        g.drawCenteredString(font, Text.t("queue.auto", "Sobald ein Platz frei ist, geht es von selbst weiter."), cx, y + 92, Ui.DIM);
        g.drawCenteredString(font, Text.t("queue.why", "Streamer und das Team kommen ohne Warten rein."), cx, y + 104, Ui.DIM);
    }

    @Override
    public void removed() {
        if (open == this) open = null;
    }
}
