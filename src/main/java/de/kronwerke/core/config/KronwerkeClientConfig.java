package de.kronwerke.core.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** The client side settings: what the player already answered, and how much of the obelisk they want to see. */
public final class KronwerkeClientConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue LANGUAGE_CHOSEN;
    public static final ModConfigSpec.IntValue CLOUD_HEIGHT;
    public static final ModConfigSpec.BooleanValue FEWER_FLASHES;
    public static final ModConfigSpec.IntValue KEYS_APPLIED;
    public static final ModConfigSpec.BooleanValue LOAD_PICTURES;
    public static final ModConfigSpec.BooleanValue MENU_MUSIC;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        LANGUAGE_CHOSEN = b.comment("The language question after the first join was answered. Set to false to ask again.")
                .define("languageChosen", false);
        CLOUD_HEIGHT = b.comment("The height of the clouds in the overworld. Vanilla puts them at 192; Kronwerke builds up to 608, so they sit higher.")
                .defineInRange("cloudHeight", 448, 64, 1024);
        FEWER_FLASHES = b.comment("Softens the white flashes, the shaking and the strobing light of the obelisk's rite, for players who are sensitive to flashing light.")
                .define("fewerFlashes", false);
        KEYS_APPLIED = b.comment("Which version of the pack's key layout was applied to this instance. Core moves keys only from their mod default to the pack's, and only once per version, so keys a player set stay. Set to 0 to apply the layout again.")
                .defineInRange("keysApplied", 0, 0, Integer.MAX_VALUE);
        LOAD_PICTURES = b.comment("Keeps the loading picture moving while the game is busy on joining (JEI building its recipes and the like): Core draws it from inside that work. Set to false if the game misbehaves while joining or moving between the servers; the picture then stands still for some seconds, as it does without Core.")
                .define("loadPictures", true);
        MENU_MUSIC = b.comment("Kronwerke's own quiet music on the title screen, in the menus and while connecting; it follows the Music slider. Set to false for the game's own menu music.")
                .define("menuMusic", true);
        SPEC = b.build();
    }

    /** The cloud height, or vanilla's while the config is not loaded yet. */
    public static float cloudHeight() {
        return SPEC.isLoaded() ? CLOUD_HEIGHT.get() : 192f;
    }

    public static boolean fewerFlashes() {
        return SPEC.isLoaded() && FEWER_FLASHES.get();
    }

    public static boolean loadPictures() {
        return SPEC.isLoaded() && LOAD_PICTURES.get();
    }

    public static boolean menuMusic() {
        return SPEC.isLoaded() && MENU_MUSIC.get();
    }

    private KronwerkeClientConfig() {
    }
}
