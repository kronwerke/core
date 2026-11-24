package de.kronwerke.core.status;

import java.io.InputStream;
import java.util.Optional;

import de.kronwerke.core.KronwerkeCore;
import de.kronwerke.core.config.KronwerkeConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.protocol.status.ServerStatus;

/**
 * What the server list shows of Kronwerke: the crown of the logo as the icon, and the name
 * in the logo's colours as the first line. Every server of the network runs Core, so the
 * four of them answer alike without a server-icon.png or a motd line to keep in step.
 *
 * <p>A server list entry has no room for a picture besides the 64 pixel icon; the name is
 * therefore written with one colour per letter, gold for KRON and ice blue for WERKE, the
 * way the lettering is drawn.
 */
public final class StatusBrand {

    private StatusBrand() {
    }

    /** One colour per letter, read off the logo's lettering from left to right. */
    private static final int[] GOLD = {0xFFF03A, 0xFFE032, 0xFFCF2A, 0xFFBE22};
    private static final int[] ICE = {0x55F1FF, 0x4CDDFD, 0x43C8FB, 0x3AB3F8, 0x319EF5};
    private static final int SNOW = 0xEAF6FF;
    private static final int FROST = 0x9FB7DC;

    private static Optional<ServerStatus.Favicon> icon;
    private static String builtFor;
    private static Component built;

    public static ServerStatus apply(ServerStatus s) {
        if (!KronwerkeConfig.STATUS_BRAND.get()) return s;
        return new ServerStatus(description(), s.players(), s.version(), icon().or(s::favicon), s.enforcesSecureChat(), s.isModded());
    }

    /** The two lines. Built once per text, the status is rebuilt every few seconds. */
    static Component description() {
        String tag = KronwerkeConfig.STATUS_TAG.get();
        String line = KronwerkeConfig.STATUS_LINE.get();
        String key = tag + "\n" + line;
        Component c = built;
        if (c == null || !key.equals(builtFor)) {
            MutableComponent out = Component.empty();
            letters(out, "KRON", GOLD);
            letters(out, "WERKE", ICE);
            if (!tag.isBlank()) out.append(Component.literal("  " + tag.strip()).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(SNOW))));
            if (!line.isBlank()) out.append(Component.literal("\n" + line.strip()).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(FROST))));
            built = c = out;
            builtFor = key;
        }
        return c;
    }

    private static void letters(MutableComponent out, String word, int[] colours) {
        for (int i = 0; i < word.length(); i++) {
            out.append(Component.literal(String.valueOf(word.charAt(i)))
                    .withStyle(Style.EMPTY.withBold(true).withColor(TextColor.fromRgb(colours[Math.min(i, colours.length - 1)]))));
        }
    }

    /** The icon from the mod's own files; empty when it cannot be read, then the server's own stays. */
    private static Optional<ServerStatus.Favicon> icon() {
        Optional<ServerStatus.Favicon> i = icon;
        if (i == null) {
            i = Optional.empty();
            try (InputStream in = StatusBrand.class.getResourceAsStream("/assets/kronwerke/textures/gui/brand/server_icon.png")) {
                if (in != null) i = Optional.of(new ServerStatus.Favicon(in.readAllBytes()));
            } catch (Exception e) {
                KronwerkeCore.LOGGER.warn("The server icon could not be read: {}", e.toString());
            }
            icon = i;
        }
        return i;
    }
}
