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
import java.util.Map;
import java.util.UUID;

/**
 * Preparation or running. While the season is in preparation the server is a work in
 * progress: the bot and the website say so instead of counting players, and whatever the
 * team deposits is only a test. Starting the season resets every goal, the leaderboards and
 * the starter kits, and takes the goal stages from everyone (players who are offline lose
 * them on their next join, through the season number).
 */
public final class Season extends SavedData {
    public static final String NAME = "kronwerke_season";
    public static final Factory<Season> FACTORY = new Factory<>(Season::new, Season::load, null);

    private boolean running;
    private int number;
    private long startedAt;
    /** player -> season number they were last brought up to date with */
    private final Map<UUID, Integer> seen = new HashMap<>();

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
        gm.resetAll();
        running = true;
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

    /** Takes the goal stages and kits of earlier seasons from a player who missed the start. */
    public void catchUp(ServerPlayer p) {
        if (seen.getOrDefault(p.getUUID(), 0) == number) return;
        for (Goal g : GoalManager.get().allGoals()) {
            for (String stage : g.stages()) {
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(),
                        "chapters remove " + p.getGameProfile().getName() + " " + stage);
            }
        }
        seen.put(p.getUUID(), number);
        setDirty();
        if (number > 0) {
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
        return tag;
    }

    public static Season load(CompoundTag tag, HolderLookup.Provider provider) {
        Season d = new Season();
        d.running = tag.getBoolean("running");
        d.number = tag.getInt("number");
        d.startedAt = tag.getLong("startedAt");
        CompoundTag s = tag.getCompound("seen");
        for (String k : s.getAllKeys()) d.seen.put(UUID.fromString(k), s.getInt(k));
        return d;
    }
}
