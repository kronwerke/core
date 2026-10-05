package de.kronwerke.core.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import de.kronwerke.core.obelisk.Obelisk;
import de.kronwerke.core.obelisk.ObeliskData;
import de.kronwerke.core.slot.SlotManager;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * /kw admin obelisk ...
 *   build <pos>                places the obelisk block at pos and the whole build around it
 *   set <pos>                  the block at pos becomes the obelisk (in the caller's dimension)
 *   clear                      no obelisk
 *   info                       where it is and every feeder with its owner
 *   feeder <pos> <player>      make the container at pos a feeder for player
 *   unfeeder <pos>             stop a feeder
 *   drain                      empty the feeders now instead of waiting
 */
final class ObeliskCommand {

    static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("obelisk")
                .then(Commands.literal("set").then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(ObeliskCommand::set)))
                .then(Commands.literal("build").then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(ObeliskCommand::buildAt)))
                .then(Commands.literal("clear").executes(c -> {
                    Obelisk.get().data().clear();
                    ok(c, "OK no obelisk");
                    return 1;
                }))
                .then(Commands.literal("info").executes(ObeliskCommand::info))
                .then(Commands.literal("feeder").then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .then(Commands.argument("player", StringArgumentType.word()).executes(ObeliskCommand::feeder))))
                .then(Commands.literal("unfeeder").then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(ObeliskCommand::unfeeder)))
                .then(Commands.literal("drain").executes(c -> {
                    long n = Obelisk.get().drain();
                    ok(c, "OK drained " + n);
                    return 1;
                }));
    }

    /** Places the core at pos and builds the whole obelisk around it; the old build, if any, is removed first. */
    private static int buildAt(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(c, "pos");
        var level = c.getSource().getLevel();
        ObeliskData d = Obelisk.get().data();
        if (d.isSet() && level.dimension().location().toString().equals(d.dimension()) && level.getBlockState(d.pos()).is(de.kronwerke.core.obelisk.ObeliskBlocks.OBELISK.get())) {
            level.removeBlock(d.pos(), false);
        }
        level.setBlock(pos, de.kronwerke.core.obelisk.ObeliskBlocks.OBELISK.get().defaultBlockState(), 3);
        de.kronwerke.core.obelisk.ObeliskStructure.build(level, pos);
        String dim = level.dimension().location().toString();
        d.set(dim, pos);
        ok(c, "OK obelisk built at " + dim + " " + pos.getX() + " " + pos.getY() + " " + pos.getZ());
        return 1;
    }

    private static int set(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(c, "pos");
        String dim = c.getSource().getLevel().dimension().location().toString();
        Obelisk.get().data().set(dim, pos);
        ok(c, "OK obelisk " + dim + " " + pos.getX() + " " + pos.getY() + " " + pos.getZ());
        return 1;
    }

    private static int info(CommandContext<CommandSourceStack> c) {
        ObeliskData d = Obelisk.get().data();
        if (!d.isSet()) {
            fail(c, "ERR no obelisk set");
            return 0;
        }
        BlockPos p = d.pos();
        ok(c, "OK obelisk " + d.dimension() + " " + p.getX() + " " + p.getY() + " " + p.getZ() + ", " + d.feeders().size() + " feeders");
        d.feeders().forEach((fp, owner) -> c.getSource().sendSuccess(() -> Component.literal(
                " " + fp.getX() + " " + fp.getY() + " " + fp.getZ() + "  " + Obelisk.get().nameOf(owner)).withStyle(ChatFormatting.GRAY), false));
        return 1;
    }

    private static int feeder(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ObeliskData d = Obelisk.get().data();
        if (!d.isSet()) {
            fail(c, "ERR no obelisk set");
            return 0;
        }
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(c, "pos");
        String name = StringArgumentType.getString(c, "player");
        var profile = SlotManager.get().lookup(name);
        if (profile.isEmpty()) {
            fail(c, "ERR unknown player " + name);
            return 0;
        }
        d.addFeeder(pos, profile.get().getId());
        ok(c, "OK feeder " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " for " + profile.get().getName());
        return 1;
    }

    private static int unfeeder(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(c, "pos");
        if (!Obelisk.get().data().removeFeeder(pos)) {
            fail(c, "ERR no feeder there");
            return 0;
        }
        ok(c, "OK feeder removed");
        return 1;
    }

    private static void ok(CommandContext<CommandSourceStack> c, String msg) {
        c.getSource().sendSuccess(() -> Component.literal(msg).withStyle(ChatFormatting.GREEN), false);
    }

    private static void fail(CommandContext<CommandSourceStack> c, String msg) {
        c.getSource().sendFailure(Component.literal(msg).withStyle(ChatFormatting.RED));
    }
}
