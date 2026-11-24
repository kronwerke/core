package de.kronwerke.core.client;

import de.kronwerke.core.client.ui.Brand;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;

/**
 * The picture half of the server list entry. The server half is {@code status.StatusBrand}:
 * it answers a ping with KRONWERKE in the logo's colours, a tag after it and a second line.
 * A client with Core draws the lettering itself in place of the entry's name and that first
 * line, the tag beside it, and leaves the second line where the game puts it. A client
 * without Core, or a server list on the web, shows the coloured text.
 */
public final class ServerListBrand {

    private ServerListBrand() {
    }

    private static final String NAME = "KRONWERKE";
    /** The lettering reaches from the top of the entry to the foot of the first text line. */
    private static final int HEIGHT = 20;
    private static final int SNOW = 0xFFEAF6FF;

    /** Whether the entry's text is a Kronwerke server's answer (not "Pinging..." or an error). */
    public static boolean is(ServerData data) {
        Component motd = data.motd;
        return motd != null && data.state() == ServerData.State.SUCCESSFUL && motd.getString().startsWith(NAME);
    }

    public static void draw(GuiGraphics g, ServerData data, int left, int top, int width) {
        int x = left + 35;
        Brand.draw(g, x, top, HEIGHT);
        // what the server wrote after the name on its first line
        String first = data.motd.getString();
        int end = first.indexOf('\n');
        String tag = (end < 0 ? first : first.substring(0, end)).substring(NAME.length()).strip();
        if (tag.isEmpty()) return;
        Font font = Minecraft.getInstance().font;
        int tx = x + Brand.width(HEIGHT) + 6;
        // the player count sits on the first row at the right; the tag takes the second row and what is left of it
        if (tx + font.width(tag) <= left + width - 4) g.drawString(font, tag, tx, top + 12, SNOW, false);
    }
}
