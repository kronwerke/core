package de.kronwerke.core.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** The client side settings: what the player already answered, and how much of the obelisk they want to see. */
public final class KronwerkeClientConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue LANGUAGE_CHOSEN;
    public static final ModConfigSpec.IntValue CLOUD_HEIGHT;
    public static final ModConfigSpec.BooleanValue FEWER_FLASHES;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        LANGUAGE_CHOSEN = b.comment("The language question after the first join was answered. Set to false to ask again.")
                .define("languageChosen", false);
        CLOUD_HEIGHT = b.comment("The height of the clouds in the overworld. Vanilla puts them at 192; Kronwerke builds up to 608, so they sit higher.")
                .defineInRange("cloudHeight", 448, 64, 1024);
        FEWER_FLASHES = b.comment("Softens the white flashes, the shaking and the strobing light of the obelisk's rite, for players who are sensitive to flashing light.")
                .define("fewerFlashes", false);
        SPEC = b.build();
    }

    /** The cloud height, or vanilla's while the config is not loaded yet. */
    public static float cloudHeight() {
        return SPEC.isLoaded() ? CLOUD_HEIGHT.get() : 192f;
    }

    public static boolean fewerFlashes() {
        return SPEC.isLoaded() && FEWER_FLASHES.get();
    }

    private KronwerkeClientConfig() {
    }
}
