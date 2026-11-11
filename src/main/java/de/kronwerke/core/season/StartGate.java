package de.kronwerke.core.season;

import com.mojang.authlib.GameProfile;
import de.kronwerke.core.Text;
import de.kronwerke.core.config.KronwerkeConfig;
import de.kronwerke.core.link.Role;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

/**
 * Keeps the server closed until the season starts. Streamers link their names and give out
 * their slots weeks before the start, so the whitelist fills early; the gate turns away
 * everyone on it with the start date until the season runs. Operators and the names on the
 * gate list (testers) always pass; during the beta also everyone with a place of their own
 * (streamers and Season 1 players), while the players they invited wait for the start.
 *
 * Only on the main server of a dedicated setup: side worlds already take only the players
 * main sends them, and a singleplayer world is never gated.
 */
public final class StartGate {
    private StartGate() {
    }

    /** The reason to refuse this login, or null to let it through. */
    public static Component refuse(MinecraftServer server, GameProfile profile) {
        if (!server.isDedicatedServer() || !Role.main() || !Season.available()) return null;
        Season s = Season.get();
        if (!s.gateClosed()) return null;
        if (server.getPlayerList().isOp(profile) || s.gateAllows(profile)) return null;
        if (s.beta() && de.kronwerke.core.slot.SlotManager.get().hasOwnPlace(profile.getId())) return null;
        String start = KronwerkeConfig.SEASON_START.get().trim();
        if (passed(start)) {
            // the team starts a little late: no date in the past on the screen (review R3-15)
            return Component.empty()
                    .append(Component.literal(KronwerkeConfig.SEASON_NAME.get()).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
                    .append("\n\n")
                    .append(Text.t("gate.soon", "Gleich geht es los. Versuch es in ein paar Minuten noch einmal.").withStyle(ChatFormatting.WHITE));
        }
        String[] when = when(start);
        return Component.empty()
                .append(Component.literal(KronwerkeConfig.SEASON_NAME.get()).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
                .append("\n\n")
                .append(Text.t("gate.start", "Startet " + when[0] + ".", when[0], when[1]).withStyle(ChatFormatting.WHITE))
                .append("\n")
                .append(Text.t("gate.listed", "Du stehst auf der Whitelist, bis dann!").withStyle(ChatFormatting.GRAY));
    }

    static boolean passed(String iso) {
        try {
            return !LocalDateTime.parse(iso).atZone(KronwerkeConfig.zone()).toInstant().isAfter(java.time.Instant.now());
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    private static final DateTimeFormatter DE = DateTimeFormatter.ofPattern("'am' EEEE, d. MMMM yyyy, 'um' H 'Uhr'", Locale.GERMAN);
    private static final DateTimeFormatter EN = DateTimeFormatter.ofPattern("'on' EEEE, d MMMM yyyy, 'at' h a", Locale.ENGLISH);

    /** The start in German and in English, for the two lang files; English with the zone's short name of that day (CET or CEST). */
    static String[] when(String iso) {
        try {
            LocalDateTime t = LocalDateTime.parse(iso);
            String de = DE.format(t);
            if (t.getMinute() != 0) de = de.replace(" Uhr", ":" + String.format("%02d", t.getMinute()) + " Uhr");
            String zone = DateTimeFormatter.ofPattern("zzz", Locale.ENGLISH).format(t.atZone(KronwerkeConfig.zone()));
            return new String[]{de, EN.format(t).replace("AM", "am").replace("PM", "pm") + " " + zone};
        } catch (DateTimeParseException e) {
            // no date set: say so instead of guessing one
            return new String[]{"bald", "soon"};
        }
    }
}
