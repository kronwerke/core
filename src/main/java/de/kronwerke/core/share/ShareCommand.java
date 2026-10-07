package de.kronwerke.core.share;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/** /kw share (op): the shared network's state on this server. */
public final class ShareCommand {
    private ShareCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("kw").then(Commands.literal("share").requires(s -> s.hasPermission(2)).executes(c -> {
            for (String line : Share.describe()) c.getSource().sendSuccess(() -> Component.literal(line).withStyle(ChatFormatting.GRAY), false);
            return 1;
        })));
    }
}
