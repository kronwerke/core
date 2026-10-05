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

    /** The stage the player still needs for the dimension, or null when the way is open. */
    public static ResourceLocation lockingStage(ServerPlayer player, ResourceLocation dimension) {
        if (!CHAPTERS || player.isCreative() || player.isSpectator()) return null;
        ResourceLocation stage = table().get(dimension);
        if (stage == null) return null;
        PlayerStages has = EffectiveStages.snapshot(player);
        return has.has(stage) ? null : stage;
    }

    public static void onTravel(EntityTravelToDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ResourceLocation dim = event.getDimension().location();
        ResourceLocation stage = lockingStage(player, dim);
        if (stage == null) return;
        event.setCanceled(true);
        Component name = Component.translatableWithFallback("kronwerke.dimension." + dim.getNamespace() + "." + dim.getPath(),
                FALLBACK_NAMES.getOrDefault(dim.toString(), dim.toString()));
        player.displayClientMessage(Component.translatableWithFallback("kronwerke.dimension.locked",
                "%s öffnet mit %s. Die Gemeinschaft baut den Obelisken voll, dann geht die Tür auf.",
                name.copy().withStyle(ChatFormatting.GOLD),
                LockedItems.stageName(stage).copy().withStyle(ChatFormatting.YELLOW)).withStyle(ChatFormatting.RED), true);
    }
}
