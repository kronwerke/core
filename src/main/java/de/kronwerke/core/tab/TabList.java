package de.kronwerke.core.tab;

import de.kronwerke.core.config.KronwerkeConfig;
import de.kronwerke.core.goal.Goal;
import de.kronwerke.core.goal.GoalManager;
import de.kronwerke.core.slot.SlotData;
import de.kronwerke.core.slot.SlotManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundTabListPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.List;
import java.util.Locale;

/**
 * Ranks in the tab list, above name tags and in chat, drawn by the Nautical Ranks resource
 * pack the pack ships: owner, admin, streamer and member. The rank is a scoreboard team
 * with the pack's glyph as prefix, so vanilla does the rendering. Streamers are the players
 * with whitelist slots, owners come from the config, admins are the operators. The tab list
 * header carries the season, the footer the running goal and the live TPS, tick time and ping.
 */
public final class TabList {
    private static final String[] RANKS = {"owner", "admin", "streamer", "member"};
    /** The Nautical Ranks icon of each automatic rank, see {@link Tags}. */
    private static final String[] ICONS = {"owner", "admin", "twitch", "member"};
    private static final ChatFormatting[] COLOURS = {ChatFormatting.GOLD, ChatFormatting.RED, ChatFormatting.LIGHT_PURPLE, ChatFormatting.WHITE};

    private static MinecraftServer server;
    private static int ticks;

    private TabList() {
    }

    public static void init(MinecraftServer s) {
        server = s;
        ticks = 0;
        Scoreboard board = s.getScoreboard();
        for (int i = 0; i < RANKS.length; i++) {
            String name = "kw_" + (char) ('a' + i) + "_" + RANKS[i];
            PlayerTeam team = board.getPlayerTeam(name);
            if (team == null) team = board.addPlayerTeam(name);
            team.setPlayerPrefix(Tags.get(ICONS[i]).badge());
            team.setColor(COLOURS[i]);
            team.setDisplayName(Component.literal(RANKS[i]));
        }
        Tags.load();
    }

    /** The rank shown: a tag set by hand with /kwtag, else the automatic rank. */
    public static String rankOf(ServerPlayer player) {
        String tag = Tags.of(player.getUUID());
        return tag != null ? tag : autoRank(player);
    }

    /** owner, admin, streamer or member, from the config, the operator list and the slots. */
    public static String autoRank(ServerPlayer player) {
        String name = player.getGameProfile().getName();
        for (String owner : KronwerkeConfig.OWNERS.get()) {
            if (owner.equalsIgnoreCase(name) || owner.equalsIgnoreCase(player.getUUID().toString())) return "owner"; // a uuid survives a rename
        }
        if (player.hasPermissions(2)) return "admin";
        if (!de.kronwerke.core.link.Role.main()) return de.kronwerke.core.link.NetworkSync.streamer(name) ? "streamer" : "member";
        if (SlotManager.get().isStreamer(player.getUUID())) return "streamer";
        return "member";
    }

    public static void apply(ServerPlayer player) {
        if (server == null) return;
        String rank = autoRank(player);
        Scoreboard board = server.getScoreboard();
        PlayerTeam team = null;
        for (int i = 0; i < RANKS.length; i++) {
            if (!RANKS[i].equals(rank)) continue;
            String tag = Tags.of(player.getUUID());
            // a hand-set tag sorts with the player's automatic rank, so the tab list keeps its order
            team = tag == null ? board.getPlayerTeam("kw_" + (char) ('a' + i) + "_" + RANKS[i])
                    : tagTeam(board, "kw_" + (char) ('a' + i) + "_t_" + tag, Tags.get(tag));
        }
        if (team != null && board.getPlayersTeam(player.getScoreboardName()) != team) {
            board.addPlayerToTeam(player.getScoreboardName(), team);
        }
        sendHeader(player);
    }

    private static PlayerTeam tagTeam(Scoreboard board, String name, Tags.Tag tag) {
        PlayerTeam team = board.getPlayerTeam(name);
        if (team == null) {
            team = board.addPlayerTeam(name);
            team.setDisplayName(Component.literal(tag.id()));
        }
        // every change goes out to all players, so only when it differs (this runs every two seconds)
        if (!team.getPlayerPrefix().equals(tag.badge())) team.setPlayerPrefix(tag.badge());
        if (team.getColor() != tag.colour()) team.setColor(tag.colour());
        return team;
    }

    /** After a tag changed: everyone here at once, and the other servers' tab lists. */
    public static void reapply() {
        if (server == null) return;
        server.execute(() -> {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) apply(p);
            de.kronwerke.core.link.NetworkSync.refreshPlayers();
        });
    }

    public static void onJoin(ServerPlayer player) {
        apply(player);
    }

    public static void onTick(ServerTickEvent.Post event) {
        if (server == null || ++ticks < 40) return;
        ticks = 0;
        long now = System.nanoTime();
        if (windowStart != 0) {
            double seconds = (now - windowStart) / 1e9;
            tps = Math.min(20.0, 40 / seconds);
        }
        windowStart = now;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) apply(p);
    }

    /** Ticks per second over the last two seconds, measured, not estimated from tick time. */
    private static double tps = 20.0;
    private static long windowStart;

    /**
     * The live server state for everyone, so a player who lags can see whether it is the server
     * or their own connection: TPS, how long a tick takes, and their own ping.
     */
    private static MutableComponent health(ServerPlayer player) {
        double mspt = server.getAverageTickTimeNanos() / 1e6;
        ChatFormatting tpsColour = tps >= 19.5 ? ChatFormatting.GREEN : tps >= 17 ? ChatFormatting.YELLOW : ChatFormatting.RED;
        ChatFormatting msptColour = mspt <= 40 ? ChatFormatting.GREEN : mspt <= 50 ? ChatFormatting.YELLOW : ChatFormatting.RED;
        int ping = player.connection.latency();
        ChatFormatting pingColour = ping <= 80 ? ChatFormatting.GREEN : ping <= 200 ? ChatFormatting.YELLOW : ChatFormatting.RED;
        return Component.literal("TPS ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(String.format(Locale.ROOT, "%.1f", tps)).withStyle(tpsColour))
                .append(Component.literal("   Tick ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(String.format(Locale.ROOT, "%.0f ms", mspt)).withStyle(msptColour))
                .append(Component.literal("   Ping ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(ping + " ms").withStyle(pingColour));
    }

    /** A rank's or tag's icon, for names that are not on a team here (other servers, network chat). */
    public static MutableComponent badge(String rank) {
        if ("streamer".equals(rank)) rank = "twitch";
        Tags.Tag t = Tags.get(rank);
        return (t != null ? t : Tags.get("member")).badge();
    }

    /** The running goal as one line ("id|title|state", state "held" or a percentage), or empty. main only; side worlds get it from main. */
    public static String goalLine() {
        if (!de.kronwerke.core.link.Role.main()) return de.kronwerke.core.link.NetworkSync.goalLine();
        List<Goal> active = GoalManager.get().activeGoals();
        if (active.isEmpty()) return "";
        Goal g = active.get(0);
        double f = GoalManager.get().fraction(g);
        String state = GoalManager.get().isHeld(g) ? "held" : String.format(Locale.ROOT, "%d%%", Math.round(f * 100));
        return g.id() + "|" + g.title() + "|" + state;
    }

    private static void sendHeader(ServerPlayer player) {
        MutableComponent header = Component.literal(de.kronwerke.core.config.KronwerkeConfig.SEASON_NAME.get()).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                .append(Component.literal("\n" + KronwerkeConfig.TAB_LINE.get()).withStyle(ChatFormatting.GRAY));
        MutableComponent footer = Component.empty();
        String goal = goalLine();
        String[] parts = goal.split("\\|", 3);
        if (parts.length == 3) {
            Component state = parts[2].equals("held") ? de.kronwerke.core.Text.t("goal.state.held", "wartet auf das Event") : Component.literal(parts[2]);
            footer.append(de.kronwerke.core.Text.t("goal." + parts[0] + ".title", parts[1]).withStyle(ChatFormatting.GOLD))
                    .append(Component.literal("  ").append(state).withStyle(ChatFormatting.WHITE))
                    .append(Component.literal("\n"));
        }
        int here = server.getPlayerList().getPlayerCount(), elsewhere = de.kronwerke.core.link.NetworkSync.remoteCount();
        footer.append((elsewhere > 0 ? de.kronwerke.core.Text.t("tab.online_here", "%s online, %s hier", here + elsewhere, here)
                        : de.kronwerke.core.Text.t("tab.online", "%s online", here)).withStyle(ChatFormatting.GRAY))
                .append(Component.literal(de.kronwerke.core.link.Role.main() ? "   /kw goals   /kw deposit" : "").withStyle(ChatFormatting.DARK_GRAY));
        long reset = de.kronwerke.core.link.NetworkSync.resetAt();
        if (!de.kronwerke.core.link.Role.main() && reset > System.currentTimeMillis()) {
            long min = (reset - System.currentTimeMillis()) / 60_000;
            String left = min >= 1440 ? (min / 1440) + " d " + (min % 1440) / 60 + " h" : min >= 60 ? (min / 60) + " h " + (min % 60) + " min" : min + " min";
            footer.append(Component.literal("\n").append(de.kronwerke.core.Text.t("network.reset_in", "Diese Welt wird zurückgesetzt in %s", left)
                    .withStyle(min < 60 ? ChatFormatting.RED : ChatFormatting.YELLOW)));
        }
        footer.append(Component.literal("\n")).append(health(player));
        player.connection.send(new ClientboundTabListPacket(header, footer));
    }
}
