package de.kronwerke.core.season;

import de.kronwerke.core.KronwerkeCore;
import de.kronwerke.core.Text;
import de.kronwerke.core.goal.Goal;
import de.kronwerke.core.goal.GoalManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Preparation or running. While the season is in preparation the server is a work in
 * progress: the bot and the website say so instead of counting players, and whatever the
 * team deposits is only a test. Starting the season resets every goal, the leaderboards and
 * the starter kits, and takes the goal stages from everyone (players who are offline lose
 * them on their next join, through the season number).
 *
 * The start gate belongs to the preparation: while it is closed, only operators and the
 * names on its list may join (StartGate); during the beta also everyone with a place of their
 * own. It opens by itself when the season starts.
 */
public final class Season extends SavedData {
    public static final String NAME = "kronwerke_season";
    public static final Factory<Season> FACTORY = new Factory<>(Season::new, Season::load, null);

    private boolean running;
    private int number;
    private long startedAt;
    /** player -> season number they were last brought up to date with */
    private final Map<UUID, Integer> seen = new HashMap<>();
    /** whether the start gate is up while the season is in preparation */
    private boolean gate = true;
    /** during the beta, everyone with a place of their own passes the closed gate as well */
    private boolean beta;
    /** names, in lower case, that may pass the closed gate (testers) */
    private final Set<String> gateAllow = new TreeSet<>();

    private static MinecraftServer server;

    public static void init(MinecraftServer s) {
        server = s;
    }

    public static Season get() {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    public static boolean available() {
        return server != null;
    }

    public boolean running() {
        return running;
    }

    public int number() {
        return number;
    }

    public long startedAt() {
        return startedAt;
    }

    public String label() {
        return running ? "läuft" : "Vorbereitung";
    }

    /** Resets every goal and opens the season. */
    public void start() {
        GoalManager gm = GoalManager.get();
        de.kronwerke.core.link.NetworkSync.requestBackup("season start");
        // the beta's play time says nothing about the season's players: the first goal is
        // scaled from the expected count and again after the first day (GoalManager.activate)
        gm.clearActivity();
        gm.resetAll();
        running = true;
        gate = false; // a pause later must not lock everyone out again
        number++;
        startedAt = System.currentTimeMillis();
        setDirty();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) catchUp(p);
        server.getPlayerList().broadcastSystemMessage(Text.t("season.start", "Die Season beginnt. Der Obelisk ist leer, alle Ziele starten von vorn.")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
        KronwerkeCore.LOGGER.info("Season {} started", number);
    }

    /** Back to preparation, without touching any progress. */
    public void pause() {
        running = false;
        setDirty();
    }

    /** True while players who are not on the gate list are kept out. */
    public boolean gateClosed() {
        return gate && !running;
    }

    public boolean gateOn() {
        return gate;
    }

    public void setGate(boolean on) {
        gate = on;
        setDirty();
    }

    public boolean beta() {
        return beta;
    }

    public void setBeta(boolean on) {
        beta = on;
        setDirty();
    }

    public boolean gateAllows(String name) {
        return gateAllow.contains(name.toLowerCase(Locale.ROOT));
    }

    public boolean allow(String name, boolean add) {
        boolean changed = add ? gateAllow.add(name.toLowerCase(Locale.ROOT)) : gateAllow.remove(name.toLowerCase(Locale.ROOT));
        if (changed) setDirty();
        return changed;
    }

    public Set<String> gateList() {
        return Set.copyOf(gateAllow);
    }

    /** Takes the goal stages and kits of earlier seasons from a player who missed the start. */
    public void catchUp(ServerPlayer p) {
        if (seen.getOrDefault(p.getUUID(), 0) == number) return;
        boolean hadEarlier = seen.containsKey(p.getUUID());
        for (Goal g : GoalManager.get().allGoals()) {
            for (String stage : g.stages()) {
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(),
                        "chapters remove " + p.getGameProfile().getName() + " " + stage);
            }
        }
        seen.put(p.getUUID(), number);
        setDirty();
        // only for players who played an earlier season here; a new player has nothing to reset
        if (number > 1 && hadEarlier) {
            p.sendSystemMessage(Text.t("season.caught_up", "Neue Season: deine Stufen starten wieder bei 1.").withStyle(ChatFormatting.GOLD));
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putBoolean("running", running);
        tag.putInt("number", number);
        tag.putLong("startedAt", startedAt);
        CompoundTag s = new CompoundTag();
        seen.forEach((k, v) -> s.putInt(k.toString(), v));
        tag.put("seen", s);
        tag.putBoolean("gate", gate);
        tag.putBoolean("beta", beta);
        net.minecraft.nbt.ListTag allow = new net.minecraft.nbt.ListTag();
        for (String n : gateAllow) allow.add(net.minecraft.nbt.StringTag.valueOf(n));
        tag.put("gateAllow", allow);
        return tag;
    }

    public static Season load(CompoundTag tag, HolderLookup.Provider provider) {
        Season d = new Season();
        d.running = tag.getBoolean("running");
        d.number = tag.getInt("number");
        d.startedAt = tag.getLong("startedAt");
        CompoundTag s = tag.getCompound("seen");
        for (String k : s.getAllKeys()) d.seen.put(UUID.fromString(k), s.getInt(k));
        d.gate = !tag.contains("gate") || tag.getBoolean("gate");
        d.beta = tag.getBoolean("beta");
        net.minecraft.nbt.ListTag allow = tag.getList("gateAllow", net.minecraft.nbt.Tag.TAG_STRING);
        for (int i = 0; i < allow.size(); i++) d.gateAllow.add(allow.getString(i));
        return d;
    }
}
