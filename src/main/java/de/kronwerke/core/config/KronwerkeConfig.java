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
    public static final ModConfigSpec.IntValue SCALE_BASE_PLAYERS;
    public static final ModConfigSpec.IntValue SCALE_DAYS;
    public static final ModConfigSpec.DoubleValue SCALE_MIN_HOURS;
    public static final ModConfigSpec.DoubleValue SCALE_MIN;
    public static final ModConfigSpec.DoubleValue SCALE_MAX;

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

        b.push("scaling");
        SCALE_BASE_PLAYERS = b.comment("The base amounts in goals.json assume this many active players.")
                .defineInRange("basePlayers", 30, 1, 1000);
        SCALE_DAYS = b.comment("Look back this many days when counting active players.")
                .defineInRange("days", 7, 1, 60);
        SCALE_MIN_HOURS = b.comment("A player counts as active with at least this many hours in the window.")
                .defineInRange("minHours", 1.0, 0.0, 1000.0);
        SCALE_MIN = b.comment("Lowest factor a goal can be scaled to.")
                .defineInRange("minFactor", 0.4, 0.01, 10.0);
        SCALE_MAX = b.comment("Highest factor a goal can be scaled to.")
                .defineInRange("maxFactor", 1.5, 0.01, 10.0);
        b.pop();

        SPEC = b.build();
    }

    private KronwerkeConfig() {}
}
