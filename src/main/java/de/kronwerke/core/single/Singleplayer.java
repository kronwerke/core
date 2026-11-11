package de.kronwerke.core.single;

import de.kronwerke.core.KronwerkeCore;
import de.kronwerke.core.Text;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.loading.FMLEnvironment;

/**
 * A singleplayer (or LAN) world plays like a normal modpack: every stage is open, machines
 * craft everything, and nothing waits for a whitelist, a season or the obelisk. All of that
 * only exists on the Kronwerke server. A world in the game itself always runs on the client's
 * side (the integrated server), a real server never does, so that is how they are told apart.
 */
public final class Singleplayer {
    /** True in a world run by the game itself, never on a dedicated server. */
    public static final boolean ON = FMLEnvironment.dist.isClient();

    private static final int LAST_STAGE = 5;

    private Singleplayer() {
    }

    /** Every stage for the player, the first steps that need the obelisk counted as done. */
    public static void onLogin(ServerPlayer p) {
        if (!ON) return;
        MinecraftServer server = p.getServer();
        if (server == null) return;
        var source = server.createCommandSourceStack().withSuppressedOutput().withPermission(4);
        for (int i = 2; i <= LAST_STAGE; i++) {
            try {
                server.getCommands().performPrefixedCommand(source, "chapters add " + p.getGameProfile().getName() + " kronwerke:stage" + i);
            } catch (RuntimeException e) {
                KronwerkeCore.LOGGER.warn("Singleplayer: could not open stage {}: {}", i, e.toString());
            }
        }
        for (String step : new String[]{"obelisk", "deposited"}) {
            var adv = server.getAdvancements().get(ResourceLocation.fromNamespaceAndPath(KronwerkeCore.MOD_ID, "steps/" + step));
            if (adv != null) p.getAdvancements().award(adv, "done");
        }
        // kept across deaths: the tag the game copies to the respawned player
        var kept = p.getPersistentData().getCompound(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG);
        if (kept.getBoolean("kronwerke_single_told")) return;
        kept.putBoolean("kronwerke_single_told", true);
        p.getPersistentData().put(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG, kept);
        p.sendSystemMessage(Text.t("single.hello", "Einzelspielerwelt: Alle Stufen sind offen, du spielst das Pack frei. Obelisk, Season und Whitelist gibt es nur auf dem Kronwerke-Server.")
                .withStyle(ChatFormatting.GOLD));
    }
}
