package de.kronwerke.core.admin;

import de.kronwerke.core.KronwerkeCore;
import de.kronwerke.core.goal.GoalManager;
import de.kronwerke.core.net.KwNetwork;
import de.kronwerke.core.season.Season;
import net.minecraft.commands.CommandSource;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * What the buttons of the admin panel do. Each button sends one operation with its
 * arguments; most run the matching /kw command as the player, so permissions, checks and
 * messages stay in one place, and the last line the command printed comes back to the
 * panel. Arguments are checked against a strict pattern before they go into a command.
 */
public final class AdminActions {
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");
    private static final Pattern ID = Pattern.compile("[a-z0-9_]{1,40}");

    private AdminActions() {
    }

    /** A command source that keeps what the command says. */
    private static final class Capture implements CommandSource {
        final List<String> lines = new ArrayList<>();
        boolean failed;

        @Override
        public void sendSystemMessage(Component message) {
            String s = message.getString();
            if (!s.isBlank()) lines.add(s);
        }

        @Override
        public boolean acceptsSuccess() {
            return true;
        }

        @Override
        public boolean acceptsFailure() {
            failed = true;
            return true;
        }

        @Override
        public boolean shouldInformAdmins() {
            return false;
        }
    }

    public static void run(ServerPlayer p, String raw) {
        if (!p.hasPermissions(2)) return;
        String[] a = raw.split("\\|");
        String op = a[0];
        String arg = a.length > 1 ? a[1] : "";
        try {
            switch (op) {
                case "season_start" -> {
                    Season.get().start();
                    KwNetwork.sendAdmin(p, "Season " + Season.get().number() + " läuft. Alle Ziele sind zurückgesetzt.", false);
                }
                case "season_pause" -> {
                    Season.get().pause();
                    KwNetwork.sendAdmin(p, "Zurück in der Vorbereitung. Der Fortschritt bleibt.", false);
                }
                case "goal_open", "goal_complete", "goal_reset", "goal_rescale" -> {
                    if (!ID.matcher(arg).matches()) return;
                    command(p, "kw admin goal " + op.substring(5) + " " + arg);
                }
                case "goals_reload" -> command(p, "kw admin goal reload");
                case "obelisk_here" -> {
                    BlockPos at = p.blockPosition();
                    command(p, "kw admin obelisk build " + at.getX() + " " + at.getY() + " " + at.getZ());
                }
                case "obelisk_info" -> command(p, "kw admin obelisk info");
                case "board_here" -> command(p, "kw admin obelisk board");
                case "tw_go" -> command(p, "kw testworld");
                case "tw_rebuild" -> command(p, "kw testworld rebuild");
                case "tw_back" -> command(p, "kw testworld back");
                case "tp" -> {
                    if (!NAME.matcher(arg).matches()) return;
                    command(p, "tp " + arg);
                }
                case "bypass" -> {
                    if (!NAME.matcher(arg).matches()) return;
                    ServerPlayer target = p.getServer().getPlayerList().getPlayerByName(arg);
                    if (target == null) return;
                    boolean on = GoalManager.get().progressData().hasBypass(target.getUUID());
                    command(p, "kw admin bypass " + (on ? "off " : "on ") + arg);
                }
                case "mode" -> {
                    if (!NAME.matcher(arg).matches()) return;
                    ServerPlayer target = p.getServer().getPlayerList().getPlayerByName(arg);
                    if (target == null) return;
                    command(p, "gamemode " + (target.isCreative() ? "survival " : "creative ") + arg);
                }
                case "spawn" -> {
                    if (!NAME.matcher(arg).matches()) return;
                    BlockPos s = p.getServer().overworld().getSharedSpawnPos();
                    command(p, "execute in minecraft:overworld run tp " + arg + " " + (s.getX() + 0.5) + " " + s.getY() + " " + (s.getZ() + 0.5));
                }
                case "refresh" -> KwNetwork.sendAdmin(p, "", false);
                default -> {
                }
            }
        } catch (Exception e) {
            KronwerkeCore.LOGGER.warn("Admin panel action {} failed", raw, e);
            KwNetwork.sendAdmin(p, "Das hat nicht geklappt: " + e.getMessage(), true);
        }
    }

    private static void command(ServerPlayer p, String cmd) {
        Capture c = new Capture();
        var source = p.createCommandSourceStack().withSource(c);
        p.getServer().getCommands().performPrefixedCommand(source, cmd);
        String last = c.lines.isEmpty() ? "Erledigt." : c.lines.get(c.lines.size() - 1);
        KwNetwork.sendAdmin(p, last, c.failed && c.lines.size() <= 1);
    }
}
