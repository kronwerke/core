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
    /** One bar per running goal (goals can run side by side). */
    private final Map<String, ServerBossEvent> bars = new LinkedHashMap<>();
    private boolean barsOn;
    /** Players who hid the goal bar with /kw bar. */
    private final java.util.Set<UUID> barHidden = new java.util.HashSet<>();

    public static GoalManager get() {
        return INSTANCE;
    }

    public void init(MinecraftServer server) {
        this.server = server;
        reload();
        if (KronwerkeConfig.SHOW_GOAL_BOSSBAR.get()) {
            barsOn = true;
        }
        // a rite cut short by a restart: what it would have revealed goes out now
        for (Goal g : goals.values()) if (data().isCompleted(g.id()) && !data().isRevealed(g.id())) reveal(g);
        activatePending();
        refreshBossBar();
    }

    public void shutdown() {
        for (ServerBossEvent b : bars.values()) b.removeAllPlayers();
        if (server != null) activity().closeAll(System.currentTimeMillis());
    }

    public int goalCount() {
        return goals.size();
    }

    public Goal goal(String id) {
        return goals.get(id);
    }

    public boolean isCompleted(Goal g) {
        return data().isCompleted(g.id());
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
        List<String> problems = reloadChecked();
        for (String pr : problems) KronwerkeCore.LOGGER.error("goals.json: {}", pr);
    }

    /**
     * Reads goals.json into a new set of goals and checks it; only a file without problems
     * replaces the goals in use. A typo (bad JSON, a goal without id, an item id that does not
     * parse, a requirement nobody has) used to clear every goal or stop the server. Returns
     * the problems, for the command that asked.
     */
    public List<String> reloadChecked() {
        de.kronwerke.core.mobs.MobStages.reload();
        Path file = goalsFile();
        List<String> problems = new ArrayList<>();
        Map<String, Goal> fresh = new LinkedHashMap<>();
        try {
            if (Files.notExists(file)) {
                Files.createDirectories(file.getParent());
                try (Writer w = Files.newBufferedWriter(file)) {
                    GSON.toJson(defaultGoals(), w);
                }
            }
            try (Reader r = Files.newBufferedReader(file)) {
                List<Goal> list = GSON.fromJson(r, new TypeToken<List<Goal>>() {}.getType());
                if (list == null) problems.add("the file is empty");
                else for (Goal g : list) {
                    if (g == null || g.id() == null || g.id().isBlank()) {
                        problems.add("a goal without an id");
                        continue;
                    }
                    if (fresh.containsKey(g.id())) problems.add("the id " + g.id() + " is there twice");
                    fresh.put(g.id(), normalize(g));
                }
            }
        } catch (IOException | RuntimeException e) {
            problems.add("cannot be read: " + e.getMessage());
        }
        if (problems.isEmpty()) problems.addAll(validate(fresh));
        if (!problems.isEmpty()) {
            problems.add(0, goals.isEmpty() ? "not loaded, no goals until it is fixed" : "not loaded, the goals in use stay");
            return problems;
        }
        goals.clear();
        goals.putAll(fresh);
        if (server != null) {
            activatePending();
            fillNewTargets();
            for (Goal g : activeGoals()) check(g);
            refreshBossBar();
        }
        return problems;
    }

    /** Everything that would break later: ids, items, weights, requirements, kits. */
    private static List<String> validate(Map<String, Goal> goals) {
        List<String> out = new ArrayList<>();
        for (Goal g : goals.values()) {
            String at = "goal " + g.id() + ": ";
            for (String req : g.requires()) if (!goals.containsKey(req)) out.add(at + "requires " + req + ", which is not in the file");
            if (g.holdAt() != null && (g.holdAt() <= 0 || g.holdAt() > 1)) out.add(at + "holdAt must be above 0 and at most 1");
            if (g.allItems().isEmpty()) out.add(at + "has no items");
            java.util.Set<String> seen = new java.util.HashSet<>();
            for (Goal.Pillar pl : g.pillars()) {
                if (pl.items() == null) {
                    out.add(at + "pillar " + pl.id() + " has no item list");
                    continue;
                }
                for (Goal.PillarItem it : pl.items()) {
                    if (it.item() == null || ResourceLocation.tryParse(it.isTag() ? it.item().substring(1) : it.item()) == null) {
                        out.add(at + "the item " + it.item() + " is not an id");
                        continue;
                    }
                    if (!seen.add(it.item())) out.add(at + it.item() + " is there twice");
                    if (it.base() <= 0) out.add(at + it.item() + " needs a base above 0");
                    if (it.weight() != null && it.weight() < 1) out.add(at + it.item() + " needs a weight of at least 1");
                    if (!it.isTag() && !BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(it.item())))
                        out.add(at + it.item() + " is no item in this pack");
                }
            }
            for (Goal.KitItem k : g.starterKit()) {
                ResourceLocation rl = k.item() == null ? null : ResourceLocation.tryParse(k.item());
                if (rl == null || !BuiltInRegistries.ITEM.containsKey(rl)) out.add(at + "the kit item " + k.item() + " is no item in this pack");
                if (k.count() < 1) out.add(at + "the kit item " + k.item() + " needs a count of at least 1");
            }
        }
        return out;
    }

    /** Items added to a goal after it became active get a target with the goal's own factor. */
    private void fillNewTargets() {
        for (Goal g : goals.values()) {
            if (!data().isActivated(g.id())) continue;
            double f = data().factor(g.id());
            for (Goal.PillarItem it : g.allItems()) {
                if (data().ensureTarget(g.id(), it.item(), Math.max(1, Math.round(it.base() * (it.scales() && g.scales() ? f : 1.0)))))
                    KronwerkeCore.LOGGER.info("Goal {}: {} added with target {}", g.id(), it.item(), data().target(g.id(), it.item()));
            }
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
            if (!data().isRevealed(req)) return false; // the next goal opens with the reveal
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

    /**
     * The factor a goal would get now. Active players over the window divided by the base
     * count, within min and max. Without anyone active yet (a season start) the expected player
     * count the team entered counts instead, or 1.0, and the goal is marked provisional: it is
     * scaled again once the season has run a day.
     */
    public double currentFactor(Goal g) {
        if (!g.scales()) return 1.0;
        int active = activePlayers();
        int expected = KronwerkeConfig.SCALE_EXPECTED.get();
        double players = active > 0 ? active : expected > 0 ? expected : KronwerkeConfig.SCALE_BASE_PLAYERS.get();
        double raw = players / KronwerkeConfig.SCALE_BASE_PLAYERS.get();
        return Math.max(KronwerkeConfig.SCALE_MIN.get(), Math.min(KronwerkeConfig.SCALE_MAX.get(), raw));
    }

    /** The targets a goal would get with a factor. */
    public Map<String, Long> targetsFor(Goal g, double factor) {
        Map<String, Long> targets = new LinkedHashMap<>();
        for (Goal.PillarItem it : g.allItems()) {
            double f = it.scales() ? factor : 1.0;
            targets.put(it.item(), Math.max(1, Math.round(it.base() * f)));
        }
        return targets;
    }

    /** Fixes the targets of a goal from its base amounts and the current activity factor. */
    public double activate(Goal g) {
        double factor = currentFactor(g);
        data().activate(g.id(), factor, targetsFor(g, factor));
        data().setProvisional(g.id(), g.scales() && activePlayers() == 0);
        KronwerkeCore.LOGGER.info("Goal {} activated with factor {}{}", g.id(), String.format("%.2f", factor),
                data().isProvisional(g.id()) ? " (provisional: nobody active yet)" : "");
        return factor;
    }

    /** Scales an active goal again and completes it if the new targets are already met. */
    public double rescale(Goal g) {
        double f = activate(g);
        check(g);
        return f;
    }

    /** Every day: goals scaled before anyone played are scaled again once the season ran a day. */
    public void rescaleProvisional() {
        if (server == null || !de.kronwerke.core.season.Season.get().running()) return;
        if (System.currentTimeMillis() - de.kronwerke.core.season.Season.get().startedAt() < 24L * 3600_000L) return;
        for (Goal g : activeGoals()) {
            if (!data().isProvisional(g.id()) || activePlayers() == 0) continue;
            double old = data().factor(g.id());
            double f = rescale(g);
            KronwerkeCore.LOGGER.info("Goal {} scaled after the first day: factor {} -> {}", g.id(), String.format("%.2f", old), String.format("%.2f", f));
        }
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
        return GoalMath.held(total(g), done(g), g.holdFraction(), data().isReleased(g.id()));
    }

    /** How many of an item one deposit takes now (GoalMath: the item crossing the hold goes in whole). */
    private long takeFor(Goal g, Goal.PillarItem it, long offered) {
        long remaining = data().target(g.id(), it.item()) - data().progress(g.id(), it.item());
        return GoalMath.take(remaining, offered, it.points(), total(g), done(g), g.holdFraction(), data().isReleased(g.id()));
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
            // the display name carries the rank in front; player names have no spaces
            String who = name.getString().trim();
            int space = who.lastIndexOf(' ');
            if (space >= 0) who = who.substring(space + 1);
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
    /** How many of the stack a deposit would take right now, without taking anything. */
    public long wouldTake(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        for (Goal g : activeGoals()) {
            if (isHeld(g)) continue;
            for (Goal.PillarItem it : g.allItems()) {
                if (!it.matches(stack)) continue;
                long take = takeFor(g, it, stack.getCount());
                if (take <= 0) continue;
                return take;
            }
        }
        return 0;
    }

    public long deposit(UUID who, Component name, ItemStack stack, boolean announce) {
        return deposit(who, name, stack, announce, false);
    }

    /** Feedback for inserts that come stack after stack (an intake fed by a belt or a pipe). */
    private record Batch(Goal goal, Goal.PillarItem item, ItemStack shown, UUID who, Component name, long amount, long since, long last) {}

    private final Map<String, Batch> batches = new LinkedHashMap<>();

    /**
     * Credits at once; with batch the feedback (pedestals, the flying gift, the notes, the
     * chat line) is gathered per giver and item and goes out once every few seconds, so a
     * pipe that pushes 300 stacks makes one line, not 300.
     */
    public long deposit(UUID who, Component name, ItemStack stack, boolean announce, boolean batch) {
        if (stack.isEmpty()) return 0;
        for (Goal g : activeGoals()) {
            if (isHeld(g)) continue;
            for (Goal.PillarItem it : g.allItems()) {
                if (!it.matches(stack)) continue;
                long take = takeFor(g, it, stack.getCount());
                if (take <= 0) continue;
                ItemStack shown = stack.copyWithCount(1);
                stack.shrink((int) take);
                long now = data().add(g.id(), it.item(), who, take);
                remember(g.id(), name, it.item(), take);
                if (!batch) rememberGiven(who, g, it, shown, take);
                if (batch) {
                    String key = who + "|" + g.id() + "|" + it.item();
                    Batch b = batches.get(key);
                    long t = System.currentTimeMillis();
                    batches.put(key, new Batch(g, it, shown, who, name, (b == null ? 0 : b.amount()) + take, b == null ? t : b.since(), t));
                } else {
                    feedback(g, it, shown, who, name, take, announce);
                }
                // a held goal waits for the event even when the last piece filled it
                if (isHeld(g)) announceHold(g);
                else if (done(g) >= total(g)) complete(g);
                // a pipe into the intake deposits every few ticks: the bar is sent at most once a second
                barDue = true;
                if (server != null) {
                    ServerPlayer depositor = server.getPlayerList().getPlayer(who);
                    if (depositor != null) de.kronwerke.core.steps.FirstSteps.deposited(depositor);
                }
                return take;
            }
        }
        return 0;
    }

    private void feedback(Goal g, Goal.PillarItem it, ItemStack shown, UUID who, Component name, long take, boolean announce) {
        de.kronwerke.core.obelisk.Obelisk.get().onDeposit(g, it, shown, who, take);
        if (announce && KronwerkeConfig.BROADCAST_DEPOSITS.get() && take >= KronwerkeConfig.BROADCAST_DEPOSIT_MIN.get()) {
            server.getPlayerList().broadcastSystemMessage(Text.t("goal.deposit", "%s gibt %s %s ab (%s/%s)", name,
                    Component.literal(Text.number(take)).withStyle(ChatFormatting.WHITE), Text.item(it.item()),
                    Text.number(data().progress(g.id(), it.item())), Text.number(data().target(g.id(), it.item()))).withStyle(ChatFormatting.GRAY), false);
        }
    }

    /** Every second: batches older than three seconds give their feedback, once. */
    private boolean barDue;
    private long barAt;

    /**
     * Deposits from a pipe or a farm are told when the feed has been quiet for ten seconds,
     * or once a minute while it keeps running; a hand deposit after three seconds as before
     * (review R2-08: a running intake used to post every three seconds).
     */
    public void flushBatches() {
        long now = System.currentTimeMillis();
        if (barDue && now - barAt >= 1000) {
            barDue = false;
            barAt = now;
            refreshBossBar();
        }
        if (batches.isEmpty()) return;
        for (var it = batches.values().iterator(); it.hasNext(); ) {
            Batch b = it.next();
            boolean running = b.last() - b.since() >= 2000; // more than one deposit, spread out: a feed
            long quiet = now - b.last();
            if (running ? quiet < 10_000 && now - b.since() < 60_000 : now - b.since() < 3000) continue;
            it.remove();
            feedback(b.goal(), b.item(), b.shown(), b.who(), b.name(), b.amount(), true);
        }
    }

    /**
     * Deposits everything matching from the backpack part of the inventory. The hotbar, the
     * offhand and the armour stay: what a player holds ready is not given away by a sneak.
     */
    public long depositAll(ServerPlayer player) {
        long total = 0;
        for (int i = 9; i < 36; i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (s.isEmpty()) continue;
            total += deposit(player, s);
        }
        return total;
    }

    /** A player's deposits of the last seconds, which /kw undo takes back. */
    private record Given(String goal, String item, ItemStack stack, long amount, long at) {}

    private final Map<UUID, List<Given>> lastGiven = new HashMap<>();
    private static final long UNDO_MS = 10_000;

    private void rememberGiven(UUID who, Goal g, Goal.PillarItem it, ItemStack shown, long amount) {
        long now = System.currentTimeMillis();
        List<Given> l = lastGiven.computeIfAbsent(who, k -> new ArrayList<>());
        l.removeIf(x -> now - x.at() > UNDO_MS);
        l.add(new Given(g.id(), it.item(), shown.copyWithCount(1), amount, now));
    }

    /**
     * Gives back what the player deposited in the last ten seconds, as long as the goal has not
     * completed or reached its hold with it. Returns the number of items given back.
     */
    public long undo(ServerPlayer player) {
        List<Given> l = lastGiven.remove(player.getUUID());
        if (l == null) return 0;
        long now = System.currentTimeMillis(), back = 0;
        for (Given x : l) {
            if (now - x.at() > UNDO_MS) continue;
            Goal g = goals.get(x.goal());
            if (g == null || data().isCompleted(g.id()) || isHeld(g)) continue;
            data().subtract(x.goal(), x.item(), player.getUUID(), x.amount());
            long left = x.amount();
            while (left > 0) {
                int n = (int) Math.min(left, x.stack().getMaxStackSize());
                ItemStack s = x.stack().copyWithCount(n);
                if (!player.getInventory().add(s)) player.drop(s, false);
                left -= n;
            }
            back += x.amount();
        }
        if (back > 0) {
            refreshBossBar();
            de.kronwerke.core.obelisk.Obelisk.get().undoStreak(player.getUUID());
        }
        return back;
    }

    private void announceHold(Goal g) {
        String when = eventText(g);
        server.getPlayerList().broadcastSystemMessage(when.isEmpty()
                ? Text.t("goal.hold", "Der Obelisk ist fast voll. %s wartet auf das Event, achtet auf die Ankündigung.",
                        g.name().withStyle(ChatFormatting.YELLOW)).withStyle(ChatFormatting.GOLD)
                : Text.t("goal.hold_at", "Der Obelisk ist fast voll. %s wartet auf das Event %s.",
                        g.name().withStyle(ChatFormatting.YELLOW), Component.literal(when).withStyle(ChatFormatting.WHITE)).withStyle(ChatFormatting.GOLD), false);
    }

    /** "am Samstag, 23. Januar, um 20 Uhr" when the team set the event's time, else empty. */
    public String eventText(Goal g) {
        long at = data().eventAt(g.id());
        if (at <= 0) return "";
        var t = java.time.Instant.ofEpochMilli(at).atZone(KronwerkeConfig.zone());
        String s = java.time.format.DateTimeFormatter.ofPattern("'am' EEEE, d. MMMM, 'um' H:mm 'Uhr'", java.util.Locale.GERMAN).format(t);
        return s.replace(":00 Uhr", " Uhr");
    }

    /** Lifts the hold so the last items can go in. */
    public void release(Goal g) {
        data().release(g.id());
        server.getPlayerList().broadcastSystemMessage(Text.t("goal.release", "Der Obelisk nimmt wieder an. Macht %s voll!",
                g.name().withStyle(ChatFormatting.YELLOW)).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
        de.kronwerke.core.obelisk.Obelisk.get().onRelease(g);
        // a goal that was filled while held completes now
        check(g);
    }

    /**
     * The goal is full. Only the short message goes out now; what the stage brings (the
     * goal's commands and titles, the stages, the kits, the next goal) comes at the rite's
     * reveal, so the rite does not show what the title already told. Without a rite (no
     * obelisk built) it comes at once.
     */
    public void complete(Goal g) {
        if (data().isCompleted(g.id())) return;
        data().markCompleted(g.id());
        server.getPlayerList().broadcastSystemMessage(Text.t("goal.complete", "Gemeinschaftsziel geschafft: %s",
                g.name().withStyle(ChatFormatting.GOLD)).withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), false);
        KronwerkeCore.LOGGER.info("Goal {} completed", g.id());
        if (!de.kronwerke.core.obelisk.Obelisk.get().celebrate(g)) reveal(g);
        refreshBossBar();
    }

    /** What the monsters carry in a stage, so nobody wonders why they die more often. */
    public static Component mobLine(int stage) {
        double hp = at(KronwerkeConfig.MOB_HEALTH.get(), stage), dmg = at(KronwerkeConfig.MOB_DAMAGE.get(), stage);
        if (hp <= 0 && dmg <= 0) return null;
        return Text.t("goal.mobs", "Die Monster werden stärker: %s Leben, %s Schaden.",
                "+" + Math.round(hp * 100) + "%", "+" + Math.round(dmg * 100) + "%").withStyle(ChatFormatting.RED);
    }

    private static double at(List<? extends Number> l, int stage) {
        return l.isEmpty() ? 0 : l.get(Math.max(0, Math.min(stage, l.size()) - 1)).doubleValue();
    }

    public boolean isRevealed(Goal g) {
        return data().isRevealed(g.id());
    }

    /** What a completed goal opens: its commands, the stages, the kits and the next goal. Once. */
    public void reveal(Goal g) {
        if (!data().isCompleted(g.id()) || data().isRevealed(g.id())) return;
        data().markRevealed(g.id());
        for (String cmd : g.onComplete()) {
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), cmd);
        }
        activatePending();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            giveStages(p);
            giveKits(p);
        }
        refreshBossBar();
        if (!g.stages().isEmpty()) {
            Component line = mobLine(de.kronwerke.core.mobs.MobStages.stage());
            if (line != null) server.getPlayerList().broadcastSystemMessage(line, false);
        }
        KronwerkeCore.LOGGER.info("Goal {} revealed: stages and kits are out", g.id());
    }

    // ---- stages ----

    /** Grants the Chapters stages of every completed goal the player does not have yet. */
    public void giveStages(ServerPlayer player) {
        for (Goal g : goals.values()) {
            if (!data().isRevealed(g.id()) || g.stages().isEmpty() || data().hasStages(g.id(), player.getUUID())) continue;
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

    /** A new season: every goal, the leaderboards, the starter kits and the recent deposits. */
    public void resetAll() {
        data().resetAll();
        synchronized (recent) {
            recent.clear();
        }
        activatePending();
        refreshBossBar();
        de.kronwerke.core.obelisk.Obelisk.get().data().clearStreaks();
        de.kronwerke.core.obelisk.Obelisk.get().settleTier();
    }

    public void reset(Goal g) {
        data().reset(g.id());
        activatePending();
        refreshBossBar();
        de.kronwerke.core.obelisk.Obelisk.get().settleTier();
    }

    // ---- starter kits ----

    private List<ItemStack> joinKitCache;
    private List<? extends String> joinKitFrom;

    /**
     * The join kit from the config, read once per change: "item" or "item*count". A bad entry
     * is skipped with one warning instead of breaking every login.
     */
    private List<ItemStack> joinKit() {
        List<? extends String> cfg = KronwerkeConfig.JOIN_KIT.get();
        if (cfg == joinKitFrom && joinKitCache != null) return joinKitCache;
        List<ItemStack> out = new ArrayList<>();
        for (String entry : cfg) {
            String[] parts = entry.split("\\*", 2);
            ResourceLocation id = ResourceLocation.tryParse(parts[0].trim());
            Item item = id == null ? null : BuiltInRegistries.ITEM.getOptional(id).orElse(null);
            int count = 1;
            try {
                if (parts.length > 1) count = Integer.parseInt(parts[1].trim());
            } catch (NumberFormatException e) {
                count = -1;
            }
            if (item == null || count < 1) {
                KronwerkeCore.LOGGER.warn("join.kit: {} is not item or item*count with a known item; skipped", entry);
                continue;
            }
            out.add(new ItemStack(item, Math.min(count, 64 * 36)));
        }
        joinKitCache = out;
        joinKitFrom = cfg;
        return out;
    }

    public void giveKits(ServerPlayer player) {
        if (!data().hasKit("join", player.getUUID())) {
            List<ItemStack> kit = joinKit();
            Component names = Component.empty();
            boolean first = true;
            for (ItemStack k : kit) {
                ItemStack stack = k.copy();
                if (!first) names = names.copy().append(Component.literal(", "));
                names = names.copy().append(Component.literal(stack.getCount() + " ")).append(stack.getHoverName());
                first = false;
                if (!player.getInventory().add(stack)) player.drop(stack, false);
            }
            data().markKit("join", player.getUUID());
            if (!kit.isEmpty()) {
                player.sendSystemMessage(Text.t("goal.join_kit_items", "Willkommen auf Kronwerke. In deinem Inventar: %s.", names).withStyle(ChatFormatting.GREEN));
            }
        }
        for (Goal g : goals.values()) {
            if (!data().isRevealed(g.id()) || g.starterKit().isEmpty() || data().hasKit(g.id(), player.getUUID())) continue;
            for (Goal.KitItem k : g.starterKit()) {
                ResourceLocation kid = ResourceLocation.tryParse(k.item());
                Item item = kid == null ? null : BuiltInRegistries.ITEM.getOptional(kid).orElse(null);
                if (item == null) continue;
                ItemStack stack = new ItemStack(item, k.count());
                if (!player.getInventory().add(stack)) player.drop(stack, false);
            }
            data().markKit(g.id(), player.getUUID());
            player.sendSystemMessage(Text.t("goal.kit", "Das Startpaket für %s liegt in deinem Inventar.", g.name()).withStyle(ChatFormatting.GREEN));
        }
    }

    // ---- boss bar and players ----

    public void refreshBossBar() {
        if (!barsOn || server == null) return;
        List<Goal> active = activeGoals();
        java.util.Set<String> want = new java.util.HashSet<>();
        for (Goal g : active) want.add(g.id());
        for (var it = bars.entrySet().iterator(); it.hasNext(); ) {
            var en = it.next();
            if (!want.contains(en.getKey())) {
                en.getValue().removeAllPlayers();
                it.remove();
            }
        }
        boolean asleep = de.kronwerke.core.obelisk.Obelisk.get().slumbering();
        for (Goal g : active) {
            ServerBossEvent bar = bars.computeIfAbsent(g.id(), k -> {
                ServerBossEvent b = new ServerBossEvent(g.name(), BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.NOTCHED_10);
                for (ServerPlayer p : server.getPlayerList().getPlayers()) if (!barHidden.contains(p.getUUID())) b.addPlayer(p);
                return b;
            });
            double f = fraction(g);
            String ev = eventText(g);
            Component state = isHeld(g) ? (ev.isEmpty() ? Text.t("bar.held", "  wartet auf das Event") : Text.t("bar.event", "  Event %s", ev)) : asleep ? Text.t("bar.asleep", "  der Obelisk schläft") : Component.literal(String.format("  %d%%", Math.round(f * 100)));
            bar.setName(g.name().withStyle(ChatFormatting.GOLD)
                    .append(state.copy().withStyle(isHeld(g) ? ChatFormatting.LIGHT_PURPLE : asleep ? ChatFormatting.GRAY : ChatFormatting.WHITE)));
            bar.setColor(isHeld(g) ? BossEvent.BossBarColor.PURPLE : BossEvent.BossBarColor.YELLOW);
            bar.setProgress((float) Math.min(1.0, f));
            bar.setVisible(true);
        }
        scopeBossBar();
    }

    /** /kw bar: a player hides the goal bar, or shows it again. True when it is shown now. */
    public boolean toggleBar(ServerPlayer p) {
        boolean show = barHidden.remove(p.getUUID());
        if (!show) barHidden.add(p.getUUID());
        for (ServerBossEvent b : bars.values()) {
            if (show) b.addPlayer(p);
            else b.removePlayer(p);
        }
        return show;
    }

    /** With a boss bar radius set, only players near the obelisk carry the bar. */
    private void scopeBossBar() {
        int radius = KronwerkeConfig.BOSS_BAR_RADIUS.get();
        if (radius <= 0 || bars.isEmpty()) return;
        var ob = de.kronwerke.core.obelisk.Obelisk.get();
        var d = ob.data();
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            boolean near = !barHidden.contains(sp.getUUID()) && d.isSet() && sp.level().dimension().location().toString().equals(d.dimension())
                    && sp.blockPosition().distSqr(d.pos()) <= (long) radius * radius;
            for (ServerBossEvent bar : bars.values()) {
                boolean has = bar.getPlayers().contains(sp);
                if (near && !has) bar.addPlayer(sp);
                else if (!near && has) bar.removePlayer(sp);
            }
        }
    }

    public void onPlayerJoin(Player player) {
        if (player instanceof ServerPlayer sp) {
            activity().login(sp.getUUID(), System.currentTimeMillis());
            if (!barHidden.contains(sp.getUUID())) for (ServerBossEvent b : bars.values()) b.addPlayer(sp);
            giveStages(sp);
            giveKits(sp);
        }
    }

    public void onPlayerLeave(Player player) {
        if (player instanceof ServerPlayer sp) {
            activity().logout(sp.getUUID(), System.currentTimeMillis());
            for (ServerBossEvent b : bars.values()) b.removePlayer(sp);
        }
    }

    public void clearActivity() {
        activity().clear();
    }

    /** Every minute from the server tick: open sessions written, provisional goals scaled after day one. */
    public void minute() {
        if (server == null) return;
        activity().checkpoint(System.currentTimeMillis());
        rescaleProvisional();
    }

    public int activePlayers() {
        return activity().activePlayers(System.currentTimeMillis(), KronwerkeConfig.SCALE_DAYS.get(), KronwerkeConfig.SCALE_MIN_HOURS.get());
    }

    /** Every contributor's points for a goal: items times their weight, as the bar counts. */
    public Map<UUID, Long> points(Goal g) {
        Map<UUID, Long> sum = new HashMap<>();
        for (Goal.PillarItem it : g.allItems()) {
            data().itemContributions(g.id(), it.item()).forEach((u, a) -> sum.merge(u, a * it.points(), Long::sum));
        }
        return sum;
    }

    /** The most diligent hands of a goal, by points (eight brass hearts outweigh a pile of cobblestone). */
    public List<Map.Entry<UUID, Long>> leaderboard(Goal g, int limit) {
        List<Map.Entry<UUID, Long>> l = new ArrayList<>(points(g).entrySet());
        l.sort(Collections.reverseOrder(Map.Entry.comparingByValue()));
        return l.size() > limit ? l.subList(0, limit) : l;
    }

    /** The most diligent hands of one pillar, by points. */
    public List<Map.Entry<UUID, Long>> leaderboard(Goal g, Goal.Pillar pillar, int limit) {
        Map<UUID, Long> sum = new HashMap<>();
        for (Goal.PillarItem it : pillar.items()) {
            data().itemContributions(g.id(), it.item()).forEach((u, a) -> sum.merge(u, a * it.points(), Long::sum));
        }
        List<Map.Entry<UUID, Long>> l = new ArrayList<>(sum.entrySet());
        l.sort(Collections.reverseOrder(Map.Entry.comparingByValue()));
        return l.size() > limit ? l.subList(0, limit) : l;
    }

    /** What a player gave to a pillar, by points. */
    public long contributed(Goal g, Goal.Pillar pillar, UUID player) {
        long sum = 0;
        for (Goal.PillarItem it : pillar.items()) sum += data().contributed(g.id(), it.item(), player) * it.points();
        return sum;
    }

    /** The player's rank among everyone who gave to the goal, 1 based, or 0 when nothing yet. */
    public int rank(Goal g, UUID player) {
        Map<UUID, Long> all = points(g);
        long mine = all.getOrDefault(player, 0L);
        if (mine <= 0) return 0;
        int rank = 1;
        for (long v : all.values()) if (v > mine) rank++;
        return rank;
    }
}
