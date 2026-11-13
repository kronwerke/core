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
    public static final ModConfigSpec.IntValue BOSS_BAR_RADIUS;
    public static final ModConfigSpec.IntValue SCALE_BASE_PLAYERS;
    public static final ModConfigSpec.IntValue SCALE_DAYS;
    public static final ModConfigSpec.DoubleValue SCALE_MIN_HOURS;
    public static final ModConfigSpec.DoubleValue SCALE_MIN;
    public static final ModConfigSpec.DoubleValue SCALE_MAX;
    public static final ModConfigSpec.BooleanValue TEST_COMMANDS;
    public static final ModConfigSpec.BooleanValue DIRECT_JOIN;
    public static final ModConfigSpec.ConfigValue<String> PORTAL_TARGET;
    public static final ModConfigSpec.ConfigValue<java.util.List<? extends String>> KEEP_ON_MAIN;
    public static final ModConfigSpec.ConfigValue<java.util.List<? extends String>> DIMENSION_SERVERS;
    public static final ModConfigSpec.IntValue SCALE_EXPECTED;
    public static final ModConfigSpec.ConfigValue<java.util.List<? extends String>> LOCKED_FLUIDS;
    public static final ModConfigSpec.IntValue LOG_DAYS;
    public static final ModConfigSpec.ConfigValue<java.util.List<? extends String>> LOCKED_DIMENSIONS;
    public static final ModConfigSpec.ConfigValue<java.util.List<? extends String>> JOIN_KIT;
    public static final ModConfigSpec.ConfigValue<String> SEASON_START;
    public static final ModConfigSpec.ConfigValue<String> TIMEZONE;
    public static final ModConfigSpec.ConfigValue<String> SEASON_NAME;

    /** The season's time zone, Europe/Berlin when the config names none that exists. */
    public static java.time.ZoneId zone() {
        try {
            return java.time.ZoneId.of(SPEC.isLoaded() ? TIMEZONE.get() : "Europe/Berlin");
        } catch (RuntimeException e) {
            return java.time.ZoneId.of("Europe/Berlin");
        }
    }
    public static final ModConfigSpec.BooleanValue CLEAN_CHUNK_LOADS;
    public static final ModConfigSpec.BooleanValue SPREAD_AUTOSAVE;
    public static final ModConfigSpec.BooleanValue TAG_INDEX;
    public static final ModConfigSpec.BooleanValue CLEAN_LIGHT_UPDATES;
    public static final ModConfigSpec.DoubleValue AUTOSAVE_MS_PER_TICK;
    public static final ModConfigSpec.DoubleValue TNT_MS_PER_TICK;
    public static final ModConfigSpec.BooleanValue QUEUE;
    public static final ModConfigSpec.IntValue QUEUE_LIMIT;
    public static final ModConfigSpec.BooleanValue MACHINE_LOCKS;
    public static final ModConfigSpec.BooleanValue CHEAP_TNT_EXPOSURE;
    public static final ModConfigSpec.IntValue GOAL_PAUSE_TICKS;
    public static final ModConfigSpec.IntValue SPAWN_RADIUS;
    public static final ModConfigSpec.ConfigValue<java.util.List<? extends String>> BLAST_PROOF;
    public static final ModConfigSpec.ConfigValue<String> SPAWN_CENTER;
    public static final ModConfigSpec.ConfigValue<java.util.List<? extends String>> OWNERS;
    public static final ModConfigSpec.ConfigValue<java.util.List<? extends String>> BOSSES;
    public static final ModConfigSpec.DoubleValue BOSS_HEALTH_PER_PLAYER;
    public static final ModConfigSpec.DoubleValue BOSS_DAMAGE_PER_PLAYER;
    public static final ModConfigSpec.IntValue BOSS_MAX_PLAYERS;
    public static final ModConfigSpec.IntValue BOSS_ATTACK_INTERVAL;
    public static final ModConfigSpec.DoubleValue BOSS_LIGHTNING_DAMAGE;
    public static final ModConfigSpec.DoubleValue BOSS_SHOCKWAVE_DAMAGE;
    public static final ModConfigSpec.ConfigValue<String> TAB_LINE;
    public static final ModConfigSpec.ConfigValue<java.util.List<? extends Number>> MOB_HEALTH;
    public static final ModConfigSpec.ConfigValue<java.util.List<? extends Number>> MOB_DAMAGE;
    public static final ModConfigSpec.ConfigValue<java.util.List<? extends Number>> MOB_ARMOR;
    public static final ModConfigSpec.ConfigValue<java.util.List<? extends String>> MOB_MIN_STAGE;
    public static final ModConfigSpec.ConfigValue<java.util.List<? extends String>> MOB_SPAWN_CHANCE;
    public static final ModConfigSpec.ConfigValue<java.util.List<? extends String>> MOB_CAP_SCALE;

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

        b.push("locks");
        MACHINE_LOCKS = b.comment("Recipes whose result belongs to a stage that is not open are held back when the recipes load, so no machine, ritual, autocrafter or crafting grid makes it; the players are told which stage it needs. When a stage opens the recipes load again.")
                .define("machineLocks", true);
        LOCKED_FLUIDS = b.comment("Fluids and chemicals of a stage, as id=stage, for recipes whose result is no item. A fluid whose bucket is in a stage file is locked with it by itself.")
                .defineListAllowEmpty("fluids", java.util.List.of(), o -> o instanceof String);
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
        SEASON_NAME = b.comment("The season's name in the tab list and on the closed start gate.")
                .define("name", "Kronwerke Season 2");
        TIMEZONE = b.comment("The time zone the season lives in: the start date, the daily offering's day. An IANA name.")
                .define("timezone", "Europe/Berlin");
        SEASON_START = b.comment("When the season starts, as local time in Europe/Berlin (2027-01-16T18:00). The closed start gate names it; empty says mid January 2027.")
                .define("seasonStart", "");
        JOIN_KIT = b.comment("Items every player gets once, on the first join, as item or item*count.")
                .defineListAllowEmpty("kit", java.util.List.of("waystones:waystone", "waystones:warp_dust*4"), o -> o instanceof String);
        QUEUE = b.comment("The join queue on main: when the network is full, a joining player waits in a queue screen (still connected) until a place is free, instead of being turned away. Vanilla's max-players no longer turns anyone away while it is on.")
                .define("queue", true);
        QUEUE_LIMIT = b.comment("How many players the whole network holds (main and the side worlds together) before new players queue. Operators, owners, streamers and players coming back from a side world never queue. Changed in game with /kw admin queue limit <n>, which is kept in kronwerke/queue-limit.txt and wins over this value (pack updates bring this file back).")
                .defineInRange("queueLimit", 50, 1, 999);
        b.pop();

        b.push("spawn");
        SPAWN_RADIUS = b.comment("Half the side of the protected square around the overworld spawn, in blocks. 0 turns the protection off. Operators and creative players build freely.")
                .defineInRange("radius", 96, 0, 2048);
        SPAWN_CENTER = b.comment("The middle of the protected square as x,z. Empty: the world spawn as it was when the protection first ran (kept, so /setworldspawn does not move the protection away from the obelisk).")
                .define("center", "");
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
        BOSS_ATTACK_INTERVAL = b.comment("Ticks between the extra attacks when two or more players fight the boss (240 = 12 seconds), counted per boss.")
                .defineInRange("attackInterval", 240, 40, 12000);
        BOSS_LIGHTNING_DAMAGE = b.comment("Damage of the lightning attack.")
                .defineInRange("lightningDamage", 8.0, 0.0, 200.0);
        BOSS_SHOCKWAVE_DAMAGE = b.comment("Damage of the shockwave.")
                .defineInRange("shockwaveDamage", 6.0, 0.0, 200.0);
        b.pop();

        b.push("mobs");
        MOB_HEALTH = b.comment("Extra health of hostile mobs per open stage (1 to 5), as a fraction of their base (0.5 = plus 50 percent).")
                .defineList("health", java.util.List.of(0.0, 0.3, 0.7, 1.2, 1.8), o -> o instanceof Number);
        MOB_DAMAGE = b.comment("Extra attack damage of hostile mobs per open stage, as a fraction of their base.")
                .defineList("damage", java.util.List.of(0.0, 0.2, 0.45, 0.75, 1.1), o -> o instanceof Number);
        MOB_ARMOR = b.comment("Extra armor points of hostile mobs per open stage.")
                .defineList("armor", java.util.List.of(0.0, 2.0, 4.0, 7.0, 10.0), o -> o instanceof Number);
        MOB_MIN_STAGE = b.comment("Mobs that spawn naturally only from a stage on: \"mod:entity=stage\" or \"mod:*=stage\".")
                .defineListAllowEmpty("minStage", java.util.List.of(
                        "eidolon_repraised:wraith=3", "eidolon_repraised:zombie_brute=3", "irons_spellbooks:necromancer=2", "mowziesmobs:naga=3",
                        "born_in_chaos_v1:dread_hound=2", "born_in_chaos_v1:dire_hound_leader=2", "born_in_chaos_v1:mother_spider=2",
                        "born_in_chaos_v1:fallen_chaos_knight=2", "born_in_chaos_v1:sir_pumpkinhead=2", "born_in_chaos_v1:missioner=2",
                        "born_in_chaos_v1:skeleton_demoman=2", "born_in_chaos_v1:phantom_creeper=2",
                        "born_in_chaos_v1:nightmare_stalker=3", "born_in_chaos_v1:lifestealer=3", "born_in_chaos_v1:krampus=3",
                        "born_in_chaos_v1:krampus_henchman=3", "born_in_chaos_v1:supreme_bonescaller=3", "born_in_chaos_v1:spiritof_chaos=3"), o -> o instanceof String);
        MOB_SPAWN_CHANCE = b.comment("How many of a mob's natural spawns happen (0 to 1), so one mod does not crowd out the rest: \"mod:entity=0.5\" or \"mod:*=0.5\".")
                .defineListAllowEmpty("spawnChance", java.util.List.of("born_in_chaos_v1:*=0.55"), o -> o instanceof String);
        MOB_CAP_SCALE = b.comment("The mob caps of one server of the network as a fraction of the usual ones, by the launcher's role: \"mining=0.6\" keeps the farm world at 60 percent of the monsters and animals of the others.")
                .defineListAllowEmpty("capScale", java.util.List.of("mining=0.6"), o -> o instanceof String);
        b.pop();

        b.push("network");
        DIRECT_JOIN = b.comment("On a side world (any launcher role but main): players may join it directly instead of moving there from main. Operators always may.")
                .define("directJoin", false);
        PORTAL_TARGET = b.comment("The launcher role of the server the Grubenrahmen portal leads to from main.")
                .define("portalTarget", "mining");
        KEEP_ON_MAIN = b.comment("Parts of a player file (tag names) main keeps as it knows them when a player comes back from a side world: things that point at main's world, like the waystones a player has activated.")
                .defineListAllowEmpty("keepOnMain", java.util.List.of("WaystonesData"), o -> o instanceof String);
        DIMENSION_SERVERS = b.comment("Dimensions that run on a server of their own, as dimension=launcher role. A portal, a teleport or a respawn into one of them moves the player to that server; everything not listed stays on main. A role that is not on the bus leaves its dimension on main, so a single server keeps working.")
                .defineListAllowEmpty("dimensionServers", java.util.List.of("minecraft:the_nether=nether", "minecraft:the_end=end"), o -> o instanceof String);
        b.pop();

        b.push("tab");
        OWNERS = b.comment("Players shown with the owner rank. Operators get admin, players with whitelist slots streamer, everyone else member.")
                .defineListAllowEmpty("owners", java.util.List.of("Elchi_Sam"), o -> o instanceof String);
        TAB_LINE = b.comment("The second line of the tab list header.")
                .define("line", "ein Projekt von Elchi Studios");
        b.pop();

        b.push("obelisk");
        BOSS_BAR_RADIUS = b.comment("Show the goal's boss bar only to players within this many blocks of the obelisk (in its dimension). 0 shows it to everyone everywhere.")
                .defineInRange("bossBarRadius", 0, 0, 100000);
        b.pop();

        b.push("scaling");
        SCALE_BASE_PLAYERS = b.comment("The base amounts in goals.json assume this many active players.")
                .defineInRange("basePlayers", 30, 1, 1000);
        SCALE_DAYS = b.comment("Look back this many days when counting active players (sessions are kept for at least 14 days, or this long).")
                .defineInRange("days", 7, 1, 60);
        SCALE_MIN_HOURS = b.comment("A player counts as active with at least this many hours in the window.")
                .defineInRange("minHours", 1.0, 0.0, 1000.0);
        SCALE_MIN = b.comment("Lowest factor a goal can be scaled to.")
                .defineInRange("minFactor", 0.4, 0.01, 10.0);
        SCALE_MAX = b.comment("Highest factor a goal can be scaled to.")
                .defineInRange("maxFactor", 1.5, 0.01, 10.0);
        SCALE_EXPECTED = b.comment("Players the team expects when nobody has played yet (a season start). 0 uses basePlayers. Goals scaled this way are scaled again after the first day.")
                .defineInRange("expectedPlayers", 0, 0, 1000);
        b.pop();

        b.push("privacy");
        LOG_DAYS = b.comment("Delete old server logs (logs/*.log.gz, with names and addresses of players) after this many days, checked at every start. 0 keeps them.")
                .defineInRange("logDays", 30, 0, 3650);
        b.pop();

        b.push("performance");
        CLEAN_CHUNK_LOADS = b.comment("Chunks read from disk count as unchanged. Vanilla marks every loaded chunk as changed (through setLightCorrect), so every chunk anyone passed through is written again when it unloads. Real changes still mark it.")
                .define("cleanChunkLoads", true);
        CLEAN_LIGHT_UPDATES = b.comment("A light update only marks a chunk as changed when a block that affects light changed or a chunk was lit for the first time nearby (within one chunk, in the last minute). Light the engine recalculates while chunks load and unload follows from what is on disk and is not written again.")
                .define("cleanLightUpdates", true);
        SPREAD_AUTOSAVE = b.comment("The five minute autosave writes changed chunks a few per tick over the following ticks instead of all in one tick, which froze the server for seconds with many players. save-all, save-all flush and shutdown still save everything at once.")
                .define("spreadAutosave", true);
        AUTOSAVE_MS_PER_TICK = b.comment("With spreadAutosave, how many milliseconds per tick and dimension go to writing chunks. At least one changed chunk is written per tick.")
                .defineInRange("autosaveMsPerTick", 2.0, 0.1, 50.0);
        TNT_MS_PER_TICK = b.comment("How many milliseconds per tick primed TNT may spend exploding. Once used up, the TNT due in this tick waits a tick and explodes then, so thousands of TNT going off together spread over several seconds instead of hanging the server. Each explosion stays vanilla. At least one TNT explodes per tick; 0 turns this off.")
                .defineInRange("tntMsPerTick", 25.0, 0.0, 1000.0);
        CHEAP_TNT_EXPOSURE = b.comment("How much of a primed TNT an explosion sees (only its knockback depends on it) is checked with one ray from its centre instead of 27 over its box. Half covered TNT is then pushed fully or not at all. In a heap of thousands of TNT this is most of the explosion time.")
                .define("cheapTntExposure", true);
        TAG_INDEX = b.comment("Command selectors with a tag (@e[tag=x], datapacks run many of them every tick) look only at the entities that carry the tag instead of every entity of the dimension. The result is the same; /kw admin tagindex compares the index with a full scan.")
                .define("tagIndex", true);
        GOAL_PAUSE_TICKS = b.comment("After a mob decided not to flee (rabbits, cats, foxes, villagers) or a baby not to follow its parent, how many ticks until it asks again. Both search every entity around, which made crowded pens expensive. 0 asks every time like vanilla.")
                .defineInRange("goalPauseTicks", 5, 0, 40);
        b.pop();

        b.push("testing");
        TEST_COMMANDS = b.comment("Register /kw test, which creates server side test players. Only for test servers, never in a season.")
                .define("testCommands", false);
        b.pop();

        SPEC = b.build();
    }

    private KronwerkeConfig() {}
}
