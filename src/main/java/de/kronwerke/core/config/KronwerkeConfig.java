package de.kronwerke.core.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class KronwerkeConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.IntValue DEFAULT_SLOTS;
    public static final ModConfigSpec.BooleanValue ENFORCE_WHITELIST;
    public static final ModConfigSpec.BooleanValue STREAMERS_MANAGE_OWN_SLOTS;
    public static final ModConfigSpec.BooleanValue SHOW_GOAL_BOSSBAR;
    public static final ModConfigSpec.BooleanValue BROADCAST_DEPOSITS;
    public static final ModConfigSpec.IntValue BROADCAST_DEPOSIT_MIN;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.push("slots");
        DEFAULT_SLOTS = b.comment("How many whitelist slots a streamer gets by default. Bonus slots are added on top per streamer.")
                .defineInRange("defaultSlots", 5, 0, 1000);
        ENFORCE_WHITELIST = b.comment("Turn the vanilla whitelist on when the server starts. Invited players are added to it automatically.")
                .define("enforceWhitelist", true);
        STREAMERS_MANAGE_OWN_SLOTS = b.comment("Allow streamers to invite and revoke players themselves with /kw invite and /kw revoke.")
                .define("streamersManageOwnSlots", true);
        b.pop();

        b.push("goals");
        SHOW_GOAL_BOSSBAR = b.comment("Show the currently active community goal as a boss bar for everyone.")
                .define("showBossBar", true);
        BROADCAST_DEPOSITS = b.comment("Announce deposits in chat.")
                .define("broadcastDeposits", true);
        BROADCAST_DEPOSIT_MIN = b.comment("Only announce deposits of at least this many items.")
                .defineInRange("broadcastDepositMin", 64, 1, 1_000_000);
        b.pop();

        SPEC = b.build();
    }

    private KronwerkeConfig() {}
}
