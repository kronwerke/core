package de.kronwerke.core.portal;

import com.mojang.brigadier.CommandDispatcher;
import de.kronwerke.core.Text;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;

/** /kw move <players> (op): through the portal without the portal, with everything they carry. */
public final class MoveCommand {
    private MoveCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("kw").then(Commands.literal("move").requires(s -> s.hasPermission(2))
                .then(Commands.argument("players", EntityArgument.players()).executes(c -> {
                    int n = 0;
                    for (ServerPlayer p : EntityArgument.getPlayers(c, "players")) {
                        if (Travel.move(p)) n++;
                    }
                    int moved = n;
                    if (moved == 0) {
                        c.getSource().sendFailure(Text.t("portal.nowhere", "Die andere Welt ist gerade nicht erreichbar."));
                        return 0;
                    }
                    c.getSource().sendSuccess(() -> Text.t("portal.moving", "%s Spieler auf dem Weg.", moved).withStyle(ChatFormatting.GRAY), true);
                    return moved;
                }))));
    }
}
