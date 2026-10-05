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
 * header carries the season, the footer the running goal.
 */
public final class TabList {
    private static final String[] RANKS = {"owner", "admin", "streamer", "member"};
    private static final String[] GLYPHS = {"", "", "", ""};
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
            team.setPlayerPrefix(Component.literal(GLYPHS[i] + " ").withStyle(ChatFormatting.WHITE));
            team.setColor(COLOURS[i]);
            team.setDisplayName(Component.literal(RANKS[i]));
        }
    }

    public static String rankOf(ServerPlayer player) {
        String name = player.getGameProfile().getName();
        for (String owner : KronwerkeConfig.OWNERS.get()) {
            if (owner.equalsIgnoreCase(name)) return "owner";
        }
        if (player.hasPermissions(2)) return "admin";
        SlotData.StreamerEntry e = SlotManager.get().entryByName(name);
        if (e != null && SlotManager.get().allowance(e) > 2) return "streamer";
        return "member";
    }

    public static void apply(ServerPlayer player) {
        if (server == null) return;
        String rank = rankOf(player);
        Scoreboard board = server.getScoreboard();
        for (int i = 0; i < RANKS.length; i++) {
            if (RANKS[i].equals(rank)) {
                PlayerTeam team = board.getPlayerTeam("kw_" + (char) ('a' + i) + "_" + RANKS[i]);
                if (team != null && board.getPlayersTeam(player.getScoreboardName()) != team) {
                    board.addPlayerToTeam(player.getScoreboardName(), team);
                }
            }
        }
        sendHeader(player);
    }

    public static void onJoin(ServerPlayer player) {
        apply(player);
    }

    public static void onTick(ServerTickEvent.Post event) {
        if (server == null || ++ticks < 100) return;
        ticks = 0;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) apply(p);
    }

    private static void sendHeader(ServerPlayer player) {
        MutableComponent header = Component.literal("Kronwerke Season 2").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                .append(Component.literal("\n" + KronwerkeConfig.TAB_LINE.get()).withStyle(ChatFormatting.GRAY));
        MutableComponent footer = Component.empty();
        List<Goal> active = GoalManager.get().activeGoals();
        if (!active.isEmpty()) {
            Goal g = active.get(0);
            double f = GoalManager.get().fraction(g);
            String state = GoalManager.get().isHeld(g) ? "wartet auf das Event" : String.format(Locale.ROOT, "%d%%", Math.round(f * 100));
            footer.append(Component.literal(g.title()).withStyle(ChatFormatting.GOLD))
                    .append(Component.literal("  " + state).withStyle(ChatFormatting.WHITE))
                    .append(Component.literal("\n"));
        }
        footer.append(Component.literal(server.getPlayerList().getPlayerCount() + " online").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("   /kw goals   /kw deposit").withStyle(ChatFormatting.DARK_GRAY));
        player.connection.send(new ClientboundTabListPacket(header, footer));
    }
}
