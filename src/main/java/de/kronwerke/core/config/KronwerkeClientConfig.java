package de.kronwerke.core.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** The client side settings: what the player already answered. */
public final class KronwerkeClientConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue LANGUAGE_CHOSEN;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        LANGUAGE_CHOSEN = b.comment("The language question after the first join was answered. Set to false to ask again.")
                .define("languageChosen", false);
        SPEC = b.build();
    }

    private KronwerkeClientConfig() {
    }
}
