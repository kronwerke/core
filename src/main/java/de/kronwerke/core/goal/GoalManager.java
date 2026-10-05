package de.kronwerke.core.goal;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import de.kronwerke.core.KronwerkeCore;
import de.kronwerke.core.config.KronwerkeConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import de.kronwerke.core.Text;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Loads goals from config/kronwerke/goals.json, scales them when they become active, takes
 * deposits, holds at the hold point, runs the completion commands and hands out starter kits.
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
        }
        activatePending();
        refreshBossBar();
    }

    public void shutdown() {
        if (bossBar != null) bossBar.removeAllPlayers();
        if (server != null) activity().closeAll(System.currentTimeMillis());
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

    private ActivityData activity() {
        return server.overworld().getDataStorage().computeIfAbsent(ActivityData.FACTORY, ActivityData.NAME);
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
        if (server != null) {
            activatePending();
            refreshBossBar();
        }
    }

    private static Goal normalize(Goal g) {
        return new Goal(g.id(), g.title() == null ? g.id() : g.title(), g.description() == null ? "" : g.description(),
                g.requires() == null ? List.of() : g.requires(), g.holdAt(), g.scale(),
                g.pillars() == null ? List.of() : g.pillars(),
                g.stages() == null ? List.of() : g.stages(),
                g.onComplete() == null ? List.of() : g.onComplete(),
                g.starterKit() == null ? List.of() : g.starterKit());
    }

    private static List<Goal> defaultGoals() {
        List<Goal> l = new ArrayList<>();
        l.add(new Goal("stage1", "Foundation of the Kronwerk",
                "Stone for the walls, alloy for the machines, gems for the spells. When the obelisk is full, the Nether opens.",
                List.of(), 0.98, true,
                List.of(new Goal.Pillar("stone", "Stone", List.of(new Goal.PillarItem("#c:cobblestones", 40000))),
                        new Goal.Pillar("tech", "Tech", List.of(new Goal.PillarItem("create:andesite_alloy", 3000))),
                        new Goal.Pillar("magic", "Magic", List.of(new Goal.PillarItem("ars_nouveau:source_gem", 1500)))),
                List.of("kronwerke:stage2"),
                List.of("say The foundation is complete. Stage 2 is open."),
                List.of()));
        l.add(new Goal("stage2", "The Brass Engine",
                "Brass and mana. Machines that work while you sleep.",
                List.of("stage1"), 0.98, true,
                List.of(new Goal.Pillar("tech", "Tech", List.of(new Goal.PillarItem("create:brass_ingot", 4000),
                                new Goal.PillarItem("create:precision_mechanism", 300))),
                        new Goal.Pillar("magic", "Magic", List.of(new Goal.PillarItem("botania:mana_pearl", 1500),
                                new Goal.PillarItem("botania:terrasteel_ingot", 100)))),
                List.of("kronwerke:stage3"),
                List.of("say The brass engine runs. Stage 3 is open."),
                List.of(new Goal.KitItem("create:brass_ingot", 16), new Goal.KitItem("create:blaze_burner", 1),
                        new Goal.KitItem("ars_nouveau:source_jar", 1), new Goal.KitItem("botania:mana_pearl", 8))));
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

    /** Sets the targets of every active goal that has none yet. */
    private void activatePending() {
        for (Goal g : activeGoals()) {
            if (!data().isActivated(g.id())) activate(g);
        }
    }

    /** Fixes the targets of a goal from its base amounts and the current activity factor. */
    public double activate(Goal g) {
        double factor = 1.0;
        if (g.scales()) {
            int active = activity().activePlayers(System.currentTimeMillis(), KronwerkeConfig.SCALE_DAYS.get(), KronwerkeConfig.SCALE_MIN_HOURS.get());
            double raw = (double) active / KronwerkeConfig.SCALE_BASE_PLAYERS.get();
            factor = Math.max(KronwerkeConfig.SCALE_MIN.get(), Math.min(KronwerkeConfig.SCALE_MAX.get(), raw));
            if (active == 0) factor = 1.0;
        }
        Map<String, Long> targets = new HashMap<>();
        for (Goal.PillarItem it : g.allItems()) {
            double f = it.scales() ? factor : 1.0;
            targets.put(it.item(), Math.max(1, Math.round(it.base() * f)));
        }
        data().activate(g.id(), factor, targets);
        KronwerkeCore.LOGGER.info("Goal {} activated with factor {}", g.id(), String.format("%.2f", factor));
        return factor;
    }

    public long total(Goal g) {
        long t = 0;
        for (Goal.PillarItem it : g.allItems()) t += data().target(g.id(), it.item()) * it.points();
        return t;
    }

    public long done(Goal g) {
        long d = 0;
        for (Goal.PillarItem it : g.allItems())
            d += Math.min(data().progress(g.id(), it.item()), data().target(g.id(), it.item())) * it.points();
        return d;
    }

    public double fraction(Goal g) {
        long t = total(g);
        return t == 0 ? 0 : (double) done(g) / t;
    }

    /** True while the goal sits at its hold point waiting for the event. */
    public boolean isHeld(Goal g) {
        return g.holdFraction() < 1.0 && !data().isReleased(g.id()) && fraction(g) >= g.holdFraction();
    }

    public boolean pillarDone(Goal g, Goal.Pillar p) {
        for (Goal.PillarItem it : p.items()) {
            if (data().progress(g.id(), it.item()) < data().target(g.id(), it.item())) return false;
        }
        return true;
    }

    /** One deposit, for the stream overlay's feed. Kept in memory only. */
    public record Recent(long at, String goal, String name, String item, long amount) {}

    private final Deque<Recent> recent = new ArrayDeque<>();
    private static final int RECENT = 20;

    /** The latest deposits, newest first. */
    public List<Recent> recent() {
        synchronized (recent) {
            return new ArrayList<>(recent);
        }
    }

    private void remember(String goal, Component name, String item, long amount) {
        synchronized (recent) {
            Recent last = recent.peekFirst();
            String who = name.getString();
            // a feeder hands in stack after stack; one line per player and item within a few seconds
            if (last != null && last.goal().equals(goal) && last.name().equals(who) && last.item().equals(item)
                    && System.currentTimeMillis() - last.at() < 10_000) {
                recent.pollFirst();
                amount += last.amount();
            }
            recent.addFirst(new Recent(System.currentTimeMillis(), goal, who, item, amount));
            while (recent.size() > RECENT) recent.removeLast();
        }
    }

    /** Returns how many items were taken from the stack. */
    public long deposit(ServerPlayer player, ItemStack stack) {
        return deposit(player.getUUID(), player.getDisplayName(), stack, true);
    }

    /**
     * Deposits for a contributor who does not have to be online, like the owner of an obelisk
     * feeder. Takes from the stack and returns how many were taken. announce=false leaves the
     * chat message to the caller.
     */
    public long deposit(UUID who, Component name, ItemStack stack, boolean announce) {
        if (stack.isEmpty()) return 0;
        for (Goal g : activeGoals()) {
            if (isHeld(g)) continue;
            for (Goal.PillarItem it : g.allItems()) {
                if (!it.matches(stack)) continue;
                long remaining = data().target(g.id(), it.item()) - data().progress(g.id(), it.item());
                long take = Math.min(remaining, stack.getCount());
                if (take <= 0) continue;
                if (g.holdFraction() < 1.0 && !data().isReleased(g.id())) {
                    // do not let a single deposit jump past the hold point
                    long allowed = (long) Math.ceil(total(g) * g.holdFraction()) - done(g);
                    take = Math.min(take, Math.max(0, allowed) / it.points());
                    if (take <= 0) continue;
                }
                stack.shrink((int) take);
                long now = data().add(g.id(), it.item(), who, take);
                remember(g.id(), name, it.item(), take);
                if (announce && KronwerkeConfig.BROADCAST_DEPOSITS.get() && take >= KronwerkeConfig.BROADCAST_DEPOSIT_MIN.get()) {
                    server.getPlayerList().broadcastSystemMessage(Text.t("goal.deposit", "%s gibt %s %s ab (%s/%s)", name,
                            Component.literal(Text.number(take)).withStyle(ChatFormatting.WHITE), Text.item(it.item()),
                            Text.number(now), Text.number(data().target(g.id(), it.item()))).withStyle(ChatFormatting.GRAY), false);
                }
                if (isHeld(g)) announceHold(g);
                if (done(g) >= total(g)) complete(g);
                refreshBossBar();
                return take;
            }
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

    private void announceHold(Goal g) {
        server.getPlayerList().broadcastSystemMessage(Text.t("goal.hold", "Der Obelisk ist fast voll. %s wartet auf das Event, achtet auf die Ankündigung.",
                Component.literal(g.title()).withStyle(ChatFormatting.YELLOW)).withStyle(ChatFormatting.GOLD), false);
    }

    /** Lifts the hold so the last items can go in. */
    public void release(Goal g) {
        data().release(g.id());
        server.getPlayerList().broadcastSystemMessage(Text.t("goal.release", "Der Obelisk nimmt wieder an. Macht %s voll!",
                Component.literal(g.title()).withStyle(ChatFormatting.YELLOW)).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
        refreshBossBar();
    }

    public void complete(Goal g) {
        if (data().isCompleted(g.id())) return;
        data().markCompleted(g.id());
        server.getPlayerList().broadcastSystemMessage(Text.t("goal.complete", "Gemeinschaftsziel geschafft: %s",
                Component.literal(g.title()).withStyle(ChatFormatting.GOLD)).withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), false);
        for (String cmd : g.onComplete()) {
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), cmd);
        }
        KronwerkeCore.LOGGER.info("Goal {} completed", g.id());
        activatePending();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            giveStages(p);
            giveKits(p);
        }
        refreshBossBar();
    }

    // ---- stages ----

    /** Grants the Chapters stages of every completed goal the player does not have yet. */
    public void giveStages(ServerPlayer player) {
        for (Goal g : goals.values()) {
            if (!data().isCompleted(g.id()) || g.stages().isEmpty() || data().hasStages(g.id(), player.getUUID())) continue;
            for (String stage : g.stages()) {
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(),
                        "chapters add " + player.getGameProfile().getName() + " " + stage);
            }
            data().markStages(g.id(), player.getUUID());
        }
    }

    /** Completes the goal if every target is met and nothing holds it. Used after admin edits. */
    public void check(Goal g) {
        if (isActive(g) && !isHeld(g) && total(g) > 0 && done(g) >= total(g)) complete(g);
        refreshBossBar();
    }

    public void reset(Goal g) {
        data().reset(g.id());
        activatePending();
        refreshBossBar();
    }

    // ---- starter kits ----

    public void giveKits(ServerPlayer player) {
        if (!data().hasKit("join", player.getUUID())) {
            for (String entry : KronwerkeConfig.JOIN_KIT.get()) {
                String[] parts = entry.split("\\*", 2);
                Item item = BuiltInRegistries.ITEM.getOptional(ResourceLocation.tryParse(parts[0].trim())).orElse(null);
                if (item == null) continue;
                int count = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : 1;
                ItemStack stack = new ItemStack(item, count);
                if (!player.getInventory().add(stack)) player.drop(stack, false);
            }
            data().markKit("join", player.getUUID());
            if (!KronwerkeConfig.JOIN_KIT.get().isEmpty()) {
                player.sendSystemMessage(Text.t("goal.join_kit", "Willkommen auf Kronwerke. Ein Wegstein für deine Basis liegt in deinem Inventar.").withStyle(ChatFormatting.GREEN));
            }
        }
        for (Goal g : goals.values()) {
            if (!data().isCompleted(g.id()) || g.starterKit().isEmpty() || data().hasKit(g.id(), player.getUUID())) continue;
            for (Goal.KitItem k : g.starterKit()) {
                Item item = BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(k.item())).orElse(null);
                if (item == null) continue;
                ItemStack stack = new ItemStack(item, k.count());
                if (!player.getInventory().add(stack)) player.drop(stack, false);
            }
            data().markKit(g.id(), player.getUUID());
            player.sendSystemMessage(Text.t("goal.kit", "Das Startpaket für %s liegt in deinem Inventar.", g.title()).withStyle(ChatFormatting.GREEN));
        }
    }

    // ---- boss bar and players ----

    private void refreshBossBar() {
        if (bossBar == null || server == null) return;
        List<Goal> active = activeGoals();
        if (active.isEmpty()) {
            bossBar.setVisible(false);
            return;
        }
        Goal g = active.get(0);
        double f = fraction(g);
        String state = isHeld(g) ? "  wartet auf das Event" : String.format("  %d%%", Math.round(f * 100));
        bossBar.setName(Component.literal(g.title()).withStyle(ChatFormatting.GOLD)
                .append(Component.literal(state).withStyle(isHeld(g) ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.WHITE)));
        bossBar.setColor(isHeld(g) ? BossEvent.BossBarColor.PURPLE : BossEvent.BossBarColor.YELLOW);
        bossBar.setProgress((float) Math.min(1.0, f));
        bossBar.setVisible(true);
    }

    public void onPlayerJoin(Player player) {
        if (player instanceof ServerPlayer sp) {
            activity().login(sp.getUUID(), System.currentTimeMillis());
            if (bossBar != null) bossBar.addPlayer(sp);
            giveStages(sp);
            giveKits(sp);
        }
    }

    public void onPlayerLeave(Player player) {
        if (player instanceof ServerPlayer sp) {
            activity().logout(sp.getUUID(), System.currentTimeMillis());
            if (bossBar != null) bossBar.removePlayer(sp);
        }
    }

    public int activePlayers() {
        return activity().activePlayers(System.currentTimeMillis(), KronwerkeConfig.SCALE_DAYS.get(), KronwerkeConfig.SCALE_MIN_HOURS.get());
    }

    public List<Map.Entry<UUID, Long>> leaderboard(Goal g, int limit) {
        List<Map.Entry<UUID, Long>> l = new ArrayList<>(data().contributions(g.id()).entrySet());
        l.sort(Collections.reverseOrder(Map.Entry.comparingByValue()));
        return l.size() > limit ? l.subList(0, limit) : l;
    }
}
