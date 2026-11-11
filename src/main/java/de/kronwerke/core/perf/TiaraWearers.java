package de.kronwerke.core.perf;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.event.CurioChangeEvent;

/**
 * Botania's Flügel Tiara checks every player on every tick: it searches the player's curios
 * for a tiara and looks the player up in a list of names. Kristi's idea: keep the players
 * who wear one in a set, updated when their curios change (and at login, and every five
 * seconds to be safe), and skip the check for everyone else who has no tiara flight to
 * take away. Only on the server; the client runs Botania as before.
 */
public final class TiaraWearers {

    private static final Set<UUID> WEARING = new HashSet<>();
    private static Item tiara;

    private TiaraWearers() {}

    /** Called when Botania and Curios are both loaded. */
    public static void register() {
        NeoForge.EVENT_BUS.addListener((CurioChangeEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p) check(p);
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p) check(p);
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> WEARING.remove(e.getEntity().getUUID()));
    }

    /** Whether Botania's tiara check may be skipped for this player in this tick. */
    public static boolean skip(Player player, boolean hasTiaraFlight) {
        if (!(player instanceof ServerPlayer p) || hasTiaraFlight) return false;
        if (p.tickCount % 100 == 0) check(p);
        return !WEARING.contains(p.getUUID());
    }

    static void check(ServerPlayer p) {
        if (tiara == null) {
            tiara = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("botania", "flight_tiara"));
        }
        if (tiara == Items.AIR) return; // id unknown: never skip, see skip()
        boolean on = CuriosApi.getCuriosInventory(p).map(inv -> inv.isEquipped(tiara)).orElse(false);
        if (on) WEARING.add(p.getUUID());
        else WEARING.remove(p.getUUID());
    }

    /** Without a known tiara item nothing is skipped. */
    public static boolean ready() {
        return tiara == null || tiara != Items.AIR;
    }
}
