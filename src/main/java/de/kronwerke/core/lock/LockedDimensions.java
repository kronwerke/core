package de.kronwerke.core.lock;

import com.gabinx.chapters.compat.ftb.EffectiveStages;
import com.gabinx.chapters.stage.PlayerStages;
import de.kronwerke.core.config.KronwerkeConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * Dimensions that open with a stage. The Nether and the End are the only vanilla things the
 * stages gate; Chapters ignores the "dimensions" field of its stage files, so Core keeps the
 * door shut. Creative players walk through, so admins can look around.
 */
public final class LockedDimensions {
    private static final boolean CHAPTERS = ModList.get().isLoaded("chapters");
    private static final Map<String, String> FALLBACK_NAMES = Map.of(
            "minecraft:the_nether", "Der Nether",
            "minecraft:the_end", "Das End",
            "aether:the_aether", "Der Aether",
            "undergarden:undergarden", "Der Undergarden",
            "deeperdarker:otherside", "Die Otherside",
            "eternal_starlight:starlight", "Eternal Starlight",
            "mahoutsukai:reality_marble", "Das Reality Marble");

    private LockedDimensions() {
    }

    private static Map<ResourceLocation, ResourceLocation> table() {
        Map<ResourceLocation, ResourceLocation> m = new HashMap<>();
        for (String entry : KronwerkeConfig.LOCKED_DIMENSIONS.get()) {
            int i = entry.indexOf('=');
            if (i <= 0) continue;
            ResourceLocation dim = ResourceLocation.tryParse(entry.substring(0, i).trim());
            ResourceLocation stage = ResourceLocation.tryParse(entry.substring(i + 1).trim());
            if (dim != null && stage != null) m.put(dim, stage);
        }
        return m;
    }

    /** A Nether portal lit before its stage: the players nearby learn it now, not when they step in. */
    public static void onPortalLit(net.neoforged.neoforge.event.level.BlockEvent.PortalSpawnEvent event) {
        if (!(event.getLevel() instanceof net.minecraft.server.level.ServerLevel level) || !CHAPTERS) return;
        ResourceLocation nether = net.minecraft.world.level.Level.NETHER.location();
        if (openForAll(nether)) return;
        Component name = Component.translatableWithFallback("kronwerke.dimension.minecraft.the_nether", FALLBACK_NAMES.get("minecraft:the_nether"));
        for (ServerPlayer p : level.players()) {
            if (p.blockPosition().distSqr(event.getPos()) > 32 * 32) continue;
            ResourceLocation stage = lockingStage(p, nether);
            if (stage == null) continue;
            p.displayClientMessage(Component.translatableWithFallback("kronwerke.dimension.lit_locked",
                    "Das Portal brennt, aber %s öffnet erst mit %s.", name.copy().withStyle(ChatFormatting.GOLD),
                    LockedItems.stageName(stage).copy().withStyle(ChatFormatting.YELLOW)).withStyle(ChatFormatting.GRAY), true);
        }
    }

    /** The stage the player still needs for the dimension, or null when the way is open. */
    public static ResourceLocation lockingStage(ServerPlayer player, ResourceLocation dimension) {
        if (!CHAPTERS || player.isCreative() || player.isSpectator()) return null;
        ResourceLocation stage = table().get(dimension);
        if (stage == null) return null;
        PlayerStages has = EffectiveStages.snapshot(player);
        return has.has(stage) ? null : stage;
    }

    /** Whether a dimension's stage is open for the server as a whole (the community reached it). */
    private static boolean openForAll(ResourceLocation dim) {
        ResourceLocation stage = table().get(dim);
        if (stage == null || !stage.getPath().startsWith("stage")) return stage == null;
        try {
            return de.kronwerke.core.mobs.MobStages.stage() >= Integer.parseInt(stage.getPath().substring(5));
        } catch (NumberFormatException e) {
            return true;
        }
    }

    public static void onTravel(EntityTravelToDimensionEvent event) {
        ResourceLocation dim = event.getDimension().location();
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            // a boat, cart or horse with a player in it: the player's stage counts
            for (var p : event.getEntity().getPassengers()) {
                if (p instanceof ServerPlayer rider && lockingStage(rider, dim) != null) {
                    event.setCanceled(true);
                    return;
                }
            }
            // items and mobs: not into a dimension nobody may enter yet
            if (CHAPTERS && !openForAll(dim)) event.setCanceled(true);
            return;
        }
        if (refuse(player, dim)) event.setCanceled(true);
    }

    /**
     * Says no and tells the player why, when the dimension still needs a stage. For the ways
     * into a dimension on another server that do not come past the event above: a waystone or
     * a Mekanism teleporter that stands there moves the player from server to server without
     * a change of dimension that the game knows of.
     */
    public static boolean refuse(ServerPlayer player, ResourceLocation dim) {
        ResourceLocation stage = lockingStage(player, dim);
        if (stage == null) return false;
        Component name = Component.translatableWithFallback("kronwerke.dimension." + dim.getNamespace() + "." + dim.getPath(),
                FALLBACK_NAMES.getOrDefault(dim.toString(), dim.toString()));
        player.displayClientMessage(Component.translatableWithFallback("kronwerke.dimension.locked",
                "%s öffnet mit %s. Die Gemeinschaft baut den Obelisken voll, dann geht die Tür auf.",
                name.copy().withStyle(ChatFormatting.GOLD),
                LockedItems.stageName(stage).copy().withStyle(ChatFormatting.YELLOW)).withStyle(ChatFormatting.RED), true);
        return true;
    }
}
