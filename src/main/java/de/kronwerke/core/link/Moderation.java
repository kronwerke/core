package de.kronwerke.core.link;

import com.google.gson.JsonObject;
import de.kronwerke.core.KronwerkeCore;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.event.CommandEvent;

import java.util.Set;

/**
 * Bans, kicks and operators count on every server of the network. A /ban typed in game on
 * main used to stay on main, so a griefer in the Nether played on until they logged out
 * (review R3-05). After such a command runs here, every other server runs it too.
 * Selectors (@a, @p) are never passed on: on another server they would mean other people.
 */
public final class Moderation {
    static final String TOPIC = "kw.mod";
    private static final Set<String> ROOTS = Set.of("ban", "pardon", "kick", "op", "deop", "ban-ip", "pardon-ip");

    private static MinecraftServer server;
    private static boolean mirroring;

    private Moderation() {
    }

    public static void start(MinecraftServer s) {
        server = s;
    }

    public static void onCommand(CommandEvent e) {
        if (mirroring || e.isCanceled() || server == null || !NetworkSync.on()) return;
        var parse = e.getParseResults();
        if (!parse.getExceptions().isEmpty() || parse.getReader().canRead()) return; // will fail anyway
        String input = parse.getReader().getString().trim();
        if (input.startsWith("/")) input = input.substring(1);
        String cmd = input;
        if (!ROOTS.contains(rootOf(cmd)) || cmd.contains("@")) return;
        if (!parse.getContext().getSource().hasPermission(3)) return;
        // after the command ran here, so a refused one is not passed on as done
        server.execute(() -> {
            JsonObject d = new JsonObject();
            d.addProperty("cmd", cmd);
            NetworkSync.send("*", TOPIC, d, null);
        });
    }

    static void onMessage(String from, JsonObject d) {
        if (server == null || !d.has("cmd")) return;
        String cmd = d.get("cmd").getAsString();
        if (!ROOTS.contains(rootOf(cmd)) || cmd.contains("@")) return;
        server.execute(() -> {
            mirroring = true;
            try {
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), cmd);
                KronwerkeCore.LOGGER.info("From {}: {}", from, cmd);
            } finally {
                mirroring = false;
            }
        });
    }

    private static String rootOf(String cmd) {
        int sp = cmd.indexOf(' ');
        return (sp < 0 ? cmd : cmd.substring(0, sp)).toLowerCase();
    }
}
