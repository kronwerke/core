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
    public static final ModConfigSpec.IntValue FEEDER_RADIUS;
    public static final ModConfigSpec.IntValue BOSS_BAR_RADIUS;
    public static final ModConfigSpec.BooleanValue CONTAINER_FEEDERS;
    public static final ModConfigSpec.IntValue FEEDERS_PER_PLAYER;
    public static final ModConfigSpec.IntValue FEEDER_INTERVAL;
    public static final ModConfigSpec.IntValue SCALE_BASE_PLAYERS;
    public static final ModConfigSpec.IntValue SCALE_DAYS;
    public static final ModConfigSpec.DoubleValue SCALE_MIN_HOURS;
    public static final ModConfigSpec.DoubleValue SCALE_MIN;
    public static final ModConfigSpec.DoubleValue SCALE_MAX;
    public static final ModConfigSpec.BooleanValue TEST_COMMANDS;
    public static final ModConfigSpec.BooleanValue DIRECT_JOIN;
    public static final ModConfigSpec.IntValue LOG_DAYS;
    public static final ModConfigSpec.ConfigValue<java.util.List<? extends String>> LOCKED_DIMENSIONS;
    public static final ModConfigSpec.ConfigValue<java.util.List<? extends String>> JOIN_KIT;
    public static final ModConfigSpec.IntValue SPAWN_RADIUS;
    public static final ModConfigSpec.ConfigValue<java.util.List<? extends String>> BLAST_PROOF;
    public static final ModConfigSpec.ConfigValue<java.util.List<? extends String>> OWNERS;
    public static final ModConfigSpec.ConfigValue<java.util.List<? extends String>> BOSSES;
    public static final ModConfigSpec.DoubleValue BOSS_HEALTH_PER_PLAYER;
    public static final ModConfigSpec.DoubleValue BOSS_DAMAGE_PER_PLAYER;
    public static final ModConfigSpec.IntValue BOSS_MAX_PLAYERS;
    public static final ModConfigSpec.IntValue BOSS_ATTACK_INTERVAL;
    public static final ModConfigSpec.DoubleValue BOSS_LIGHTNING_DAMAGE;
    public static final ModConfigSpec.DoubleValue BOSS_SHOCKWAVE_DAMAGE;
    public static final ModConfigSpec.ConfigValue<String> TAB_LINE;

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

        b.push("dimensions");
        LOCKED_DIMENSIONS = b.comment("Dimensions that open with a stage, as dimension=stage. A player without the stage cannot enter; creative players can.")
                .defineListAllowEmpty("locked", java.util.List.of(
                        "minecraft:the_nether=kronwerke:stage2", "aether:the_aether=kronwerke:stage2",
                        "undergarden:undergarden=kronwerke:stage3", "deeperdarker:otherside=kronwerke:stage3",
                        "minecraft:the_end=kronwerke:stage4", "eternal_starlight:starlight=kronwerke:stage4",
                        "mahoutsukai:reality_marble=kronwerke:stage4"), o -> o instanceof String str && str.contains("="));
        b.pop();

        b.push("join");
        JOIN_KIT = b.comment("Items every player gets once, on the first join, as item or item*count.")
                .defineListAllowEmpty("kit", java.util.List.of("waystones:waystone", "waystones:warp_dust*4"), o -> o instanceof String);
        b.pop();

        b.push("spawn");
        SPAWN_RADIUS = b.comment("Half the side of the protected square around the overworld spawn, in blocks. 0 turns the protection off. Operators and creative players build freely; containers next to the obelisk stay allowed for feeders.")
                .defineInRange("radius", 96, 0, 2048);
        BLAST_PROOF = b.comment("Blocks no explosion removes, anywhere: block ids, or a namespace prefix ending in *. Bedrock, end portal frames, end portals and gateways and reinforced deepslate are always kept.")
                .defineListAllowEmpty("blastProof", java.util.List.of("cataclysm:altar_of_*", "cataclysm:boss_respawner", "cataclysm:*spawner*"), o -> o instanceof String);
        b.pop();

        b.push("bosses");
        BOSSES = b.comment("Bosses that grow with the group and use the extra attacks.")
                .defineListAllowEmpty("scaling", java.util.List.of(
                        "cataclysm:ender_guardian", "cataclysm:ender_golem", "cataclysm:netherite_monstrosity", "cataclysm:ignis",
                        "cataclysm:the_harbinger", "cataclysm:the_leviathan", "cataclysm:maledictus", "cataclysm:ancient_remnant",
                        "cataclysm:scylla", "cataclysm:wadjet", "cataclysm:the_watcher",
                        "mowziesmobs:frostmaw", "mowziesmobs:wroughtnaut", "mowziesmobs:umvuthi", "mowziesmobs:sculptor",
                        "draconicevolution:chaos_guardian", "minecraft:ender_dragon", "minecraft:wither"), o -> o instanceof String);
        BOSS_HEALTH_PER_PLAYER = b.comment("Extra health per player beyond the first, as a fraction of the base (0.5 = plus 50 percent each).")
                .defineInRange("healthPerPlayer", 0.6, 0.0, 10.0);
        BOSS_DAMAGE_PER_PLAYER = b.comment("Extra damage per player beyond the first, as a fraction of the base.")
                .defineInRange("damagePerPlayer", 0.15, 0.0, 10.0);
        BOSS_MAX_PLAYERS = b.comment("Players beyond this do not scale the boss further.")
                .defineInRange("maxPlayers", 8, 1, 50);
        BOSS_ATTACK_INTERVAL = b.comment("Ticks between the extra attacks when two or more players fight the boss (240 = 12 seconds). Must be a multiple of 40.")
                .defineInRange("attackInterval", 240, 40, 12000);
        BOSS_LIGHTNING_DAMAGE = b.comment("Damage of the lightning attack.")
                .defineInRange("lightningDamage", 8.0, 0.0, 200.0);
        BOSS_SHOCKWAVE_DAMAGE = b.comment("Damage of the shockwave.")
                .defineInRange("shockwaveDamage", 6.0, 0.0, 200.0);
        b.pop();

        b.push("network");
        DIRECT_JOIN = b.comment("On a side world (any launcher role but main): players may join it directly instead of moving there from main. Operators always may.")
                .define("directJoin", false);
        b.pop();

        b.push("tab");
        OWNERS = b.comment("Players shown with the owner rank. Operators get admin, players with whitelist slots streamer, everyone else member.")
                .defineListAllowEmpty("owners", java.util.List.of("Elchi_Sam"), o -> o instanceof String);
        TAB_LINE = b.comment("The second line of the tab list header.")
                .define("line", "ein Projekt von Elchi Studios");
        b.pop();

        b.push("obelisk");
        CONTAINER_FEEDERS = b.comment("Old behaviour: any container placed next to the obelisk becomes a feeder. Off, the intake block is the only way in for machines.")
                .define("containerFeeders", false);
        FEEDER_RADIUS = b.comment("The zone around the obelisk's core, in blocks: intakes work there, and inside the spawn protection anyone may place and take back their own hoppers, pipes and belts there.")
                .defineInRange("feederRadius", 10, 1, 32);
        BOSS_BAR_RADIUS = b.comment("Show the goal's boss bar only to players within this many blocks of the obelisk (in its dimension). 0 shows it to everyone everywhere.")
                .defineInRange("bossBarRadius", 0, 0, 100000);
        FEEDERS_PER_PLAYER = b.comment("How many feeders one player can have.")
                .defineInRange("feedersPerPlayer", 2, 0, 64);
        FEEDER_INTERVAL = b.comment("Empty the feeders into the active goal every this many seconds.")
                .defineInRange("feederInterval", 2, 1, 600);
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

        b.push("privacy");
        LOG_DAYS = b.comment("Delete old server logs (logs/*.log.gz, with names and addresses of players) after this many days, checked at every start. 0 keeps them.")
                .defineInRange("logDays", 30, 0, 3650);
        b.pop();

        b.push("testing");
        TEST_COMMANDS = b.comment("Register /kw test, which creates server side test players. Only for test servers, never in a season.")
                .define("testCommands", false);
        b.pop();

        SPEC = b.build();
    }

    private KronwerkeConfig() {}
}
