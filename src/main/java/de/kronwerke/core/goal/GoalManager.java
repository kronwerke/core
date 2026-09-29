package de.kronwerke.core.goal;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import de.kronwerke.core.KronwerkeCore;
import de.kronwerke.core.config.KronwerkeConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads goals from config/kronwerke/goals.json, tracks deposits, shows the active goal as a
 * boss bar and fires the goal's commands once it is reached.
 */
public final class GoalManager {
    private static final GoalManager INSTANCE = new GoalManager();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private MinecraftServer server;
    private final Map<String, Goal> goals = new LinkedHashMap<>();
    private ServerBossEvent bossBar;

    public static GoalManager get() {
        return INSTANCE;
    }

    public void init(MinecraftServer server) {
        this.server = server;
        reload();
        if (KronwerkeConfig.SHOW_GOAL_BOSSBAR.get()) {
            bossBar = new ServerBossEvent(Component.literal("Kronwerke"), BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.NOTCHED_10);
            bossBar.setVisible(false);
            refreshBossBar();
        }
    }

    public void shutdown() {
        if (bossBar != null) bossBar.removeAllPlayers();
    }

    public int goalCount() {
        return goals.size();
    }

    public Goal goal(String id) {
        return goals.get(id);
    }

    public Iterable<Goal> allGoals() {
        return goals.values();
    }

    private GoalData data() {
        return server.overworld().getDataStorage().computeIfAbsent(GoalData.FACTORY, GoalData.NAME);
    }

    public GoalData progressData() {
        return data();
    }

    // ---- loading ----

    public static Path goalsFile() {
        return FMLPaths.CONFIGDIR.get().resolve("kronwerke").resolve("goals.json");
    }

    public void reload() {
        goals.clear();
        Path file = goalsFile();
        try {
            if (Files.notExists(file)) {
                Files.createDirectories(file.getParent());
                try (Writer w = Files.newBufferedWriter(file)) {
                    GSON.toJson(defaultGoals(), w);
                }
            }
            try (Reader r = Files.newBufferedReader(file)) {
                List<Goal> list = GSON.fromJson(r, new TypeToken<List<Goal>>() {}.getType());
                if (list != null) for (Goal g : list) goals.put(g.id(), normalize(g));
            }
        } catch (IOException e) {
            KronwerkeCore.LOGGER.error("Could not read goals file {}", file, e);
        }
        refreshBossBar();
    }

    private static Goal normalize(Goal g) {
        return new Goal(g.id(), g.title() == null ? g.id() : g.title(), g.description() == null ? "" : g.description(),
                g.item(), g.amount(),
                g.requires() == null ? List.of() : g.requires(),
                g.onComplete() == null ? List.of() : g.onComplete());
    }

    private static List<Goal> defaultGoals() {
        List<Goal> l = new ArrayList<>();
        l.add(new Goal("age1_cobble", "Foundation of the Kronwerk",
                "Bring cobblestone to the spawn. Once the pile is complete, the first machines unlock for everyone.",
                "#c:cobblestones", 10000, List.of(),
                List.of("say The foundation is complete. Age 1 is open.")));
        l.add(new Goal("age2_iron", "Iron Age",
                "Deposit iron ingots to unlock advanced processing.",
                "#c:ingots/iron", 5000, List.of("age1_cobble"),
                List.of("say Iron Age unlocked.")));
        return l;
    }

    // ---- state ----

    public boolean isActive(Goal g) {
        if (data().isCompleted(g.id())) return false;
        for (String req : g.requires()) {
            if (!data().isCompleted(req)) return false;
        }
        return true;
    }

    public List<Goal> activeGoals() {
        List<Goal> out = new ArrayList<>();
        for (Goal g : goals.values()) if (isActive(g)) out.add(g);
        return out;
    }

    /** Returns how many items were taken from the stack. */
    public long deposit(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty()) return 0;
        for (Goal g : activeGoals()) {
            if (!g.matches(stack)) continue;
            long remaining = g.amount() - data().progress(g.id());
            long take = Math.min(remaining, stack.getCount());
            if (take <= 0) continue;
            stack.shrink((int) take);
            long now = data().add(g.id(), player.getUUID(), take);
            if (KronwerkeConfig.BROADCAST_DEPOSITS.get() && take >= KronwerkeConfig.BROADCAST_DEPOSIT_MIN.get()) {
                server.getPlayerList().broadcastSystemMessage(Component.literal("")
                        .append(player.getDisplayName())
                        .append(Component.literal(" deposited " + take + " for ").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal(g.title()).withStyle(ChatFormatting.GOLD))
                        .append(Component.literal(" (" + now + "/" + g.amount() + ")").withStyle(ChatFormatting.GRAY)), false);
            }
            if (now >= g.amount()) complete(g);
            refreshBossBar();
            return take;
        }
        return 0;
    }

    /** Deposits everything matching from the player's inventory. Returns total taken. */
    public long depositAll(ServerPlayer player) {
        long total = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (s.isEmpty()) continue;
            total += deposit(player, s);
        }
        return total;
    }

    public void complete(Goal g) {
        if (data().isCompleted(g.id())) return;
        data().markCompleted(g.id());
        data().setProgress(g.id(), g.amount());
        server.getPlayerList().broadcastSystemMessage(Component.literal("Community goal complete: ").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)
                .append(Component.literal(g.title()).withStyle(ChatFormatting.GOLD)), false);
        for (String cmd : g.onComplete()) {
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), cmd);
        }
        KronwerkeCore.LOGGER.info("Goal {} completed", g.id());
        refreshBossBar();
    }

    public void reset(Goal g) {
        data().reset(g.id());
        refreshBossBar();
    }

    // ---- boss bar ----

    private void refreshBossBar() {
        if (bossBar == null || server == null) return;
        List<Goal> active = activeGoals();
        if (active.isEmpty()) {
            bossBar.setVisible(false);
            return;
        }
        Goal g = active.get(0);
        long p = data().progress(g.id());
        bossBar.setName(Component.literal(g.title()).withStyle(ChatFormatting.GOLD)
                .append(Component.literal("  " + p + " / " + g.amount()).withStyle(ChatFormatting.WHITE)));
        bossBar.setProgress(g.amount() == 0 ? 1f : Math.min(1f, (float) p / g.amount()));
        bossBar.setVisible(true);
    }

    public void onPlayerJoin(Player player) {
        if (bossBar != null && player instanceof ServerPlayer sp) bossBar.addPlayer(sp);
    }

    public void onPlayerLeave(Player player) {
        if (bossBar != null && player instanceof ServerPlayer sp) bossBar.removePlayer(sp);
    }

    public List<Map.Entry<java.util.UUID, Long>> leaderboard(Goal g, int limit) {
        List<Map.Entry<java.util.UUID, Long>> l = new ArrayList<>(data().contributions(g.id()).entrySet());
        l.sort(Collections.reverseOrder(Map.Entry.comparingByValue()));
        return l.size() > limit ? l.subList(0, limit) : l;
    }
}
