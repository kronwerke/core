package de.kronwerke.core.obelisk;

import de.kronwerke.core.config.KronwerkeConfig;
import de.kronwerke.core.goal.Goal;
import de.kronwerke.core.goal.GoalManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import de.kronwerke.core.Text;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The obelisk at spawn: the {@link ObeliskBlock} an operator placed, or any block an admin
 * points at with /kw admin obelisk set. Right click it to deposit what you hold, sneak and
 * right click to deposit everything that fits. That is the only way in: no command, no menu
 * and no machine hands anything in (the feeder block and the container feeders are gone since
 * 0.39.30), and a machine's fake player is turned away.
 */
public final class Obelisk {
    private static final Obelisk INSTANCE = new Obelisk();

    private MinecraftServer server;
    private final ObeliskScheduler scheduler = new ObeliskScheduler();
    private int ambientTicks, misfireIn;

    public static Obelisk get() {
        return INSTANCE;
    }

    public void init(MinecraftServer server) {
        this.server = server;
        this.scheduler.clear();
        this.misfireIn = 6000;
        this.settled = false;
        this.tierOverride = -1;
    }

    public ObeliskScheduler scheduler() {
        return scheduler;
    }

    public ObeliskData data() {
        return server.overworld().getDataStorage().computeIfAbsent(ObeliskData.FACTORY, ObeliskData.NAME);
    }

    private boolean isObelisk(Level level, BlockPos pos) {
        ObeliskData d = data();
        if (!d.isSet() || !level.dimension().location().toString().equals(d.dimension())) return false;
        if (d.pos().equals(pos)) return true;
        // every block of the build counts: plinth, shaft, crystal
        return ObeliskStructure.contains(d.pos(), pos, level.getBlockState(pos));
    }

    private static int distance(BlockPos a, BlockPos b) {
        return Math.max(Math.abs(a.getX() - b.getX()), Math.max(Math.abs(a.getY() - b.getY()), Math.abs(a.getZ() - b.getZ())));
    }

    // ---- right click ----

    public void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        if (server == null || event.getLevel().isClientSide() || !isObelisk(event.getLevel(), event.getPos())) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (event.getHand() != InteractionHand.MAIN_HAND || !(event.getEntity() instanceof ServerPlayer player)) return;
        // by hand means a person's hand: a deployer or any other machine that clicks is not one
        if (player instanceof net.neoforged.neoforge.common.util.FakePlayer) return;
        // a client that set a block against the stone all the same has one less in its hand, and only there:
        // tell it what the inventory really holds (ObeliskEffects.onClientClick keeps Core's own client from it)
        player.inventoryMenu.sendAllDataToRemote();

        GoalManager gm = GoalManager.get();
        List<Goal> active = gm.activeGoals();
        if (active.isEmpty()) {
            player.sendSystemMessage(Text.t("obelisk.rests", "Der Obelisk ruht. Gerade ist kein Gemeinschaftsziel offen.").withStyle(ChatFormatting.GRAY));
            return;
        }
        long taken;
        if (player.isShiftKeyDown() && player.getMainHandItem().isEmpty()) {
            ledger(player, active.get(0));
            return;
        }
        if (player.isShiftKeyDown()) {
            // everything that fits is a big step: the first sneak click asks, the second gives
            Long armed = allArmed.get(player.getUUID());
            if (armed == null || System.currentTimeMillis() - armed > 5000) {
                allArmed.put(player.getUUID(), System.currentTimeMillis());
                player.sendSystemMessage(Text.t("obelisk.all_ask", "Alles aus dem Inventar abgeben, was passt? Noch einmal schleichen und klicken. Hotbar und Nebenhand bleiben.")
                        .withStyle(ChatFormatting.YELLOW));
                return;
            }
            allArmed.remove(player.getUUID());
            taken = gm.depositAll(player);
        } else {
            ItemStack held = player.getMainHandItem();
            taken = gm.deposit(player, held);
        }
        if (taken > 0) {
            player.sendSystemMessage(Text.t("obelisk.took", "Der Obelisk nimmt %s.", Text.number(taken)).withStyle(ChatFormatting.GREEN)
                    .append(Component.literal(" ")).append(undoLink()));
            return;
        }
        Goal g = active.get(0);
        if (gm.isHeld(g)) {
            player.sendSystemMessage(Text.t("obelisk.held", "Der Obelisk wartet auf das Event. Die letzten Teile gehen gemeinsam rein.").withStyle(ChatFormatting.LIGHT_PURPLE));
            return;
        }
        // the whole list once in a while; a quick second click just says no
        Long shownAt = listShown.get(player.getUUID());
        if (shownAt != null && System.currentTimeMillis() - shownAt < 30_000) {
            player.displayClientMessage(Text.t("obelisk.not_this", "Das braucht der Obelisk gerade nicht.").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        listShown.put(player.getUUID(), System.currentTimeMillis());
        MutableComponent wanted = Component.empty();
        boolean first = true;
        for (Goal.PillarItem it : g.allItems()) {
            long have = gm.progressData().progress(g.id(), it.item()), need = gm.progressData().target(g.id(), it.item());
            if (have >= need) continue;
            if (!first) wanted.append(Component.literal(", ").withStyle(ChatFormatting.GRAY));
            first = false;
            wanted.append(Text.item(it.item())).append(Component.literal(" " + Text.number(have) + "/" + Text.number(need)).withStyle(ChatFormatting.GRAY));
        }
        player.sendSystemMessage(Text.t("obelisk.wants", "Der Obelisk braucht: %s", wanted).withStyle(ChatFormatting.GOLD));
        player.sendSystemMessage(Text.t("obelisk.hint", "Rechtsklick gibt den Stapel in der Hand ab, Schleichen und Rechtsklick alles, was passt. Schleichen mit leerer Hand zeigt deine Gaben.").withStyle(ChatFormatting.GRAY));
    }

    private final java.util.Map<UUID, Long> allArmed = new java.util.HashMap<>();
    private final java.util.Map<UUID, Long> listShown = new java.util.HashMap<>();

    public static Component undoComponent() {
        return undoLink();
    }

    /** A click in chat that takes the last deposit back, for ten seconds. */
    static Component undoLink() {
        return Text.t("obelisk.undo", "[Rückgängig]").withStyle(s -> s.withColor(ChatFormatting.GRAY).withUnderlined(true)
                .withClickEvent(new net.minecraft.network.chat.ClickEvent(net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND, "/kw undo"))
                .withHoverEvent(new net.minecraft.network.chat.HoverEvent(net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT,
                        Text.t("obelisk.undo_tip", "Gibt dir die Gabe der letzten zehn Sekunden zurück"))));
    }

    /** Sneaking with an empty hand: what the player has given to the running goal, pillar by pillar, and their rank. */
    private void ledger(ServerPlayer player, Goal g) {
        GoalManager gm = GoalManager.get();
        java.util.Map<java.util.UUID, Long> points = gm.points(g);
        long total = points.getOrDefault(player.getUUID(), 0L);
        int rank = gm.rank(g, player.getUUID());
        int hands = points.size();
        player.sendSystemMessage(Text.t("obelisk.ledger.title", "Deine Gaben an %s", g.name().withStyle(ChatFormatting.YELLOW)).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        if (total <= 0) {
            player.sendSystemMessage(Text.t("obelisk.ledger.none", "Noch nichts. Der Obelisk wartet auf deine erste Gabe.").withStyle(ChatFormatting.GRAY));
        } else {
            for (Goal.Pillar pillar : g.pillars()) {
                long mine = gm.contributed(g, pillar, player.getUUID());
                Component best = Component.empty();
                long bestN = 0;
                for (Goal.PillarItem it : pillar.items()) {
                    long n = gm.progressData().contributed(g.id(), it.item(), player.getUUID());
                    if (n > bestN) {
                        bestN = n;
                        best = Text.item(it.item());
                    }
                }
                MutableComponent line = Component.literal("  ").append(pillar.name(g.id())).append(": ").withStyle(ChatFormatting.LIGHT_PURPLE)
                        .append(Text.t("obelisk.ledger.points", "%s Punkte", Text.number(mine)).withStyle(ChatFormatting.WHITE));
                if (bestN > 0) line.append(Text.t("obelisk.ledger.most", ", am meisten %s (%s Stück)", best.copy().withStyle(ChatFormatting.GRAY), Text.number(bestN)).withStyle(ChatFormatting.GRAY));
                player.sendSystemMessage(line);
            }
            player.sendSystemMessage(Text.t("obelisk.ledger.rank", "Platz %s von %s Händen, %s Punkte insgesamt.", rank, hands, Text.number(total)).withStyle(ChatFormatting.GOLD));
        }
        int streak = data().streak(player.getUUID());
        if (streak > 0) player.sendSystemMessage(Text.t("obelisk.ledger.streak", "Tagesgabe: %s in Folge.", streak).withStyle(ChatFormatting.GRAY));
        player.playNotifySound(net.minecraft.sounds.SoundEvents.BOOK_PAGE_TURN, net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 1.0f);
    }

    private int displayTicks;

    private boolean settled;

    public void onServerTick(ServerTickEvent.Post event) {
        if (server == null) return;
        if (!settled) {
            // a preview tier from before a restart is taken back, the build follows the goals again
            settled = true;
            try {
                settleTier();
                // a rite cut short by a restart may have left the stone hidden
                ServerLevel lv = obeliskLevel();
                if (lv != null) ObeliskStructure.setHidden(lv, data().pos(), false);
            } catch (Exception e) {
                de.kronwerke.core.KronwerkeCore.LOGGER.warn("Could not settle the obelisk's tier", e);
            }
        }
        scheduler.tick();
        if (++displayTicks >= 100) {
            displayTicks = 0;
            refreshDisplays();
            GoalManager.get().refreshBossBar();
        }
        ambient();
        if (ambientTicks % 4 == 0) embers();
    }

    // ---- the life of the stone: hum, heartbeat, misfires ----

    private int tierOverride = -1;

    /** Shows the build at a tier for previews; -1 follows the goals again. */
    public void previewTier(int tier) {
        tierOverride = tier;
        settleTier();
        refreshDisplays();
    }

    /** Plays the rite for the running goal without completing anything; the next tier is built and taken back again. */
    public String rehearse() {
        ServerLevel level = obeliskLevel();
        if (level == null || !level.isLoaded(data().pos())) return "Kein Obelisk geladen.";
        ObeliskTopBlockEntity top = top(level);
        if (top == null) return "Die Spitze fehlt, erst kw admin obelisk build.";
        if (top.rite() > 0) return "Das Ritual läuft schon.";
        GoalManager gm = GoalManager.get();
        List<Goal> active = gm.activeGoals();
        Goal g = active.isEmpty() ? null : active.get(0);
        if (g == null) for (Goal each : gm.allGoals()) g = each;
        if (g == null) return "Keine Ziele geladen.";
        int before = tier();
        ObeliskRite.start(this, level, g, Math.min(ObeliskTiers.MAX, before + 1));
        scheduler.at(ObeliskRite.T_END + 200, () -> {
            tierOverride = -1;
            data().setBuiltTier(before + 1);
            settleTier();
        });
        return "OK das Ritual beginnt, die neue Stufe wird nach dem Ende wieder abgebaut.";
    }

    /** How many goals are done: the tier of the build. */
    public int tier() {
        if (tierOverride >= 0) return tierOverride;
        GoalManager gm = GoalManager.get();
        int n = 0;
        for (Goal g : gm.allGoals()) if (gm.progressData().isCompleted(g.id())) n++;
        return Math.min(ObeliskTiers.MAX, n);
    }

    private static final ResourceLocation LIFT = ResourceLocation.fromNamespaceAndPath(de.kronwerke.core.KronwerkeCore.MOD_ID, "obelisk_lift");

    /** From the fourth stage the ground around the obelisk lets go a little: players on the plinth weigh half. */
    private void lift(ServerLevel level, BlockPos core) {
        boolean on = tier() >= 4;
        // everyone, in every dimension: who left the plinth by a portal or a waystone keeps no half gravity
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            var attr = p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.GRAVITY);
            if (attr == null) continue;
            boolean near = on && p.level() == level && Math.abs(p.getX() - core.getX() - 0.5) < 11 && Math.abs(p.getZ() - core.getZ() - 0.5) < 11
                    && p.getY() > core.getY() - 3 && p.getY() < core.getY() + 24;
            boolean has = attr.hasModifier(LIFT);
            if (near && !has) {
                attr.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(LIFT, -0.5,
                        net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
                p.displayClientMessage(Text.t("obelisk.lift", "Der Boden lässt dich ein Stück los.").withStyle(ChatFormatting.LIGHT_PURPLE), true);
            } else if (!near && has) {
                attr.removeModifier(LIFT);
            }
        }
    }

    /** How many players stand close around the plinth. */
    int crowd(ServerLevel level) {
        BlockPos core = data().pos();
        int n = 0;
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(core.getX() + 0.5, core.getY() + 1, core.getZ() + 0.5) <= 12 * 12) n++;
        }
        return n;
    }

    private int lastCrowd = -1;

    private void ambient() {
        ObeliskData d = data();
        if (!d.isSet() || ++ambientTicks % 20 != 0) return;
        ServerLevel level = obeliskLevel();
        if (level == null) return;
        BlockPos core = d.pos();
        if (!level.isLoaded(core)) return;
        lift(level, core);
        // a gathering changes the stone's mood at once, not at the next display refresh
        int crowd = crowd(level);
        // at a busy spawn the crowd changes all the time: the state goes out at most every
        // five seconds, and the chord only when a crowd gathers anew (after a quiet minute)
        long now = System.currentTimeMillis();
        if (crowd != lastCrowd && now - lastStateAt > 5000) {
            lastCrowd = crowd;
            lastStateAt = now;
            ObeliskTopBlockEntity t = top(level);
            if (t != null) broadcastState(level, t);
        }
        if (crowd >= 3 && lastCrowdNoted < 3 && now - lastChordAt > 60_000) {
            gathering(level, core, crowd);
            lastChordAt = now;
        }
        lastCrowdNoted = crowd;
        whisper(level, core, crowd);
        if (!level.hasNearbyAlivePlayer(core.getX() + 0.5, core.getY() + 10, core.getZ() + 0.5, 48)) return;
        ObeliskTopBlockEntity top = top(level);
        if (top != null && top.rite() > 0) return;
        GoalManager gm = GoalManager.get();
        List<Goal> active = gm.activeGoals();
        Goal g = active.isEmpty() ? null : active.get(0);
        BlockPos at = core.above(12);
        if (g != null && gm.isHeld(g)) {
            // the heartbeat of the hold: two low thumps every four seconds
            if (ambientTicks % 80 == 0) {
                level.playSound(null, at, net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BASS.value(), net.minecraft.sounds.SoundSource.BLOCKS, 2.0f, 0.5f);
                scheduler.at(7, () -> level.playSound(null, at, net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BASS.value(), net.minecraft.sounds.SoundSource.BLOCKS, 1.6f, 0.5f));
            }
        } else if (ambientTicks % 160 == 0) {
            // the hum, eight seconds long, rises with the goal
            float f = g == null ? 0.2f : (float) gm.fraction(g);
            float volume = 0.5f + 0.7f * f;
            level.playSound(null, at, KwSounds.HUM.get(), net.minecraft.sounds.SoundSource.BLOCKS, volume, 0.9f + 0.2f * f);
        }
        // every five to ten minutes a rune misfires; nobody will believe it is random
        misfireIn -= 20;
        if (misfireIn <= 0) {
            misfireIn = 6000 + level.random.nextInt(6000);
            int y = level.random.nextBoolean() ? 5 : 12;
            net.minecraft.core.Direction dir = net.minecraft.core.Direction.Plane.HORIZONTAL.getRandomDirection(level.random);
            BlockPos rune = core.above(y).relative(dir, 2);
            double x = rune.getX() + 0.5 + dir.getStepX() * 0.6, z = rune.getZ() + 0.5 + dir.getStepZ() * 0.6;
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK, x, rune.getY() + 0.5, z, 24, 0.3, 0.3, 0.3, 0.05);
            level.playSound(null, rune, net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_RESONATE, net.minecraft.sounds.SoundSource.BLOCKS, 1.2f, 0.6f);
            scheduler.at(6, () -> level.playSound(null, rune, net.minecraft.sounds.SoundEvents.AMETHYST_CLUSTER_BREAK, net.minecraft.sounds.SoundSource.BLOCKS, 0.8f, 1.4f));
        }
    }

    private boolean lightsChecked;

    /**
     * The lights of the tier blocks are always on. Up to 0.39.29 the stone put them out when
     * nobody had given anything for a day; this lights whatever such a night left dark, once
     * after the start, as soon as the build is loaded.
     */
    private void relight(ServerLevel level) {
        for (BlockPos p : data().extras().keySet()) {
            if (!level.isLoaded(p)) return;
            net.minecraft.world.level.block.state.BlockState s = level.getBlockState(p);
            if (s.hasProperty(ObeliskPartBlock.LIT) && !s.getValue(ObeliskPartBlock.LIT)) level.setBlock(p, s.setValue(ObeliskPartBlock.LIT, true), 3);
        }
        lightsChecked = true;
    }

    private int lastCrowdNoted;
    private long lastStateAt, lastChordAt;
    private int whisperIn = 1200;

    /** Three or more around the plinth: the runes answer with a chord, one note per player, and a ring of motes. */
    private void gathering(ServerLevel level, BlockPos core, int crowd) {
        BlockPos at = core.above(6);
        float[] scale = {0.5f, 0.5946f, 0.6674f, 0.7491f, 0.8909f, 1.0f, 1.1892f, 1.3348f};
        for (int i = 0; i < Math.min(crowd, 6); i++) {
            float pitch = scale[i % scale.length];
            scheduler.at(i * 3, () -> level.playSound(null, at, net.minecraft.sounds.SoundEvents.NOTE_BLOCK_CHIME.value(), net.minecraft.sounds.SoundSource.BLOCKS, 1.2f, pitch));
        }
        for (int i = 0; i < 24; i++) {
            double a = i / 24.0 * Math.PI * 2;
            level.sendParticles(KwParticles.RUNE.get(), core.getX() + 0.5 + Math.cos(a) * 5.5, core.getY() + 1.2, core.getZ() + 0.5 + Math.sin(a) * 5.5, 1, 0, 0.02, 0, 0);
        }
    }

    /**
     * Between gifts the stone whispers: every two to four minutes, when someone is close and
     * nothing has come in for ten minutes, runes drift down the trunk and the players nearby
     * read which pillar is furthest behind. A nudge, not a lecture.
     */
    private void whisper(ServerLevel level, BlockPos core, int crowd) {
        whisperIn -= 20;
        if (whisperIn > 0 || crowd == 0) return;
        whisperIn = 2400 + level.random.nextInt(2400);
        // lastDepositAt is wall clock time: ten minutes without a gift
        long since = System.currentTimeMillis() - data().lastDepositAt();
        if (data().lastDepositAt() > 0 && since < 600_000) return;
        GoalManager gm = GoalManager.get();
        List<Goal> active = gm.activeGoals();
        Goal g = active.isEmpty() ? null : active.get(0);
        if (g == null || gm.isHeld(g) || g.pillars().isEmpty()) return;
        Goal.Pillar lowest = null;
        int lowestPercent = 101;
        for (Goal.Pillar pillar : g.pillars()) {
            int pct = pillarPercent(gm, g, pillar);
            if (pct < lowestPercent) {
                lowestPercent = pct;
                lowest = pillar;
            }
        }
        if (lowest == null) return;
        for (int i = 0; i < 16; i++) {
            double a = i / 16.0 * Math.PI * 2 + level.random.nextDouble();
            level.sendParticles(KwParticles.RUNE.get(), core.getX() + 0.5 + Math.cos(a) * 1.8, core.getY() + 13 - i * 0.6, core.getZ() + 0.5 + Math.sin(a) * 1.8, 1, 0, -0.03, 0, 0);
        }
        level.playSound(null, core.above(8), net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_RESONATE, net.minecraft.sounds.SoundSource.BLOCKS, 0.6f, 0.5f);
        Component line = Text.t("obelisk.whisper", "Der Stein flüstert: %s fehlt am meisten.", lowest.name(g.id())).withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.ITALIC);
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(core.getX() + 0.5, core.getY() + 1, core.getZ() + 0.5) <= 24 * 24) p.displayClientMessage(line, true);
        }
    }

    // ---- pedestals and the leaderboard wall ----

    private ServerLevel obeliskLevel() {
        ObeliskData d = data();
        if (!d.isSet()) return null;
        return server.getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(d.dimension())));
    }

    ObeliskTopBlockEntity top(ServerLevel level) {
        BlockPos p = data().pos().above(17);
        if (!level.isLoaded(p)) return null;
        return level.getBlockEntity(p) instanceof ObeliskTopBlockEntity te ? te : null;
    }

    ObeliskPedestalBlockEntity pedestal(ServerLevel level, int index) {
        BlockPos p = ObeliskStructure.pedestal(data().pos(), index);
        if (!level.isLoaded(p)) return null;
        return level.getBlockEntity(p) instanceof ObeliskPedestalBlockEntity pe ? pe : null;
    }

    /** Share of a pillar that is done, by points. */
    private static int pillarPercent(GoalManager gm, Goal g, Goal.Pillar pillar) {
        long need = 0, have = 0;
        for (Goal.PillarItem it : pillar.items()) {
            long t = gm.progressData().target(g.id(), it.item());
            need += t * it.points();
            have += Math.min(t, gm.progressData().progress(g.id(), it.item())) * it.points();
        }
        return need <= 0 ? 0 : (int) Math.floor(100.0 * have / need);
    }

    /**
     * Called by the goal manager for every deposit. The pedestal of the pillar shows the
     * item and a trail runs to the trunk; beyond that the feedback grows with the share of
     * the goal the deposit covers: a note for a handful, an arpeggio and a pulse up the trunk
     * for a real delivery, a title for the giver and a white flash of the beam for a large
     * one, and a word to the whole server for a huge one.
     */
    public void onDeposit(Goal g, Goal.PillarItem item, ItemStack shown, UUID who, long amount) {
        if (server == null) return;
        ServerLevel level = obeliskLevel();
        if (level == null) return;
        GoalManager gm = GoalManager.get();
        data().touch(System.currentTimeMillis());
        int index = -1;
        for (int i = 0; i < g.pillars().size() && i < 3; i++) {
            if (g.pillars().get(i).items().contains(item)) index = i;
        }
        String name = nameOf(who);
        String line = name + "  +" + Text.number(amount);
        Goal.Pillar pillar = index >= 0 ? g.pillars().get(index) : null;
        if (pillar != null) {
            ObeliskPedestalBlockEntity pe = pedestal(level, index);
            if (pe != null) pe.show(shown, pillar.title() + "  " + pillarPercent(gm, g, pillar) + "%", line);
            trail(level, index);
        }
        ObeliskPedestalBlockEntity last = pedestal(level, 3);
        if (last != null) last.show(shown, "Zuletzt", line);
        trail(level, 3);

        double share = gm.total(g) <= 0 ? 0 : amount * (double) item.points() / gm.total(g);
        int size = share >= 0.05 ? 3 : share >= 0.01 ? 2 : share >= 0.001 ? 1 : 0;
        float[] strength = {0.3f, 0.6f, 0.85f, 1.0f};
        ObeliskTopBlockEntity top = top(level);
        if (top != null) {
            top.show((int) Math.round(gm.fraction(g) * 100), gm.isHeld(g) ? ObeliskTopBlockEntity.MOOD_HELD : ObeliskTopBlockEntity.MOOD_RUNNING, tier());
            top.flash(strength[size]);
        }
        BlockPos core = data().pos();
        ServerPlayer player = server.getPlayerList().getPlayer(who);
        // the gift flies to the crystal on every client nearby: from the giver's hand, or from the plinth when the giver stands far off
        boolean fromHand = player != null && player.level() == level && player.blockPosition().distSqr(core) < 40 * 40;
        net.minecraft.world.phys.Vec3 hand = fromHand ? player.getEyePosition().add(player.getLookAngle().scale(0.9)).add(0, -0.35, 0) : null;
        double gx = fromHand ? hand.x : core.getX() + 0.5, gy = fromHand ? hand.y : core.getY() + 1.2, gz = fromHand ? hand.z : core.getZ() + 0.5;
        var gift = new de.kronwerke.core.net.KwNetwork.GiftPayload(gx, gy, gz, shown.copyWithCount(1), size, index, amount);
        for (ServerPlayer p : level.players()) {
            if (p.blockPosition().distSqr(core) < 80 * 80) net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(p, gift);
        }
        // the note of the item: the same item always sounds the same, more of it plays more notes
        float[] scale = {0.5f, 0.56f, 0.63f, 0.75f, 0.84f, 1.0f, 1.12f, 1.26f, 1.5f, 1.68f, 1.89f, 2.0f};
        int base = Math.floorMod(item.item().hashCode(), scale.length);
        var instrument = index == 0 ? net.minecraft.sounds.SoundEvents.NOTE_BLOCK_IRON_XYLOPHONE.value()
                : index == 1 ? net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BELL.value() : net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME;
        int notes = size == 0 ? 1 : size == 1 ? 3 : 5;
        for (int n = 0; n < notes; n++) {
            float pitch = scale[(base + n * 2) % scale.length];
            scheduler.at(n * 3, () -> level.playSound(null, core.above(17), instrument, net.minecraft.sounds.SoundSource.BLOCKS, 1.2f, pitch));
        }
        if (size >= 1) pulse(level, core);
        if (size >= 2) {
            level.playSound(null, core.above(17), net.minecraft.sounds.SoundEvents.BEACON_POWER_SELECT, net.minecraft.sounds.SoundSource.BLOCKS, 1.5f, 1.2f);
            if (player != null && pillar != null) {
                ObeliskRite.title(player, Text.t("obelisk.accepts", "Der Obelisk nimmt an").withStyle(ChatFormatting.GOLD),
                        Component.literal(pillar.title() + "  " + pillarPercent(gm, g, pillar) + "%").withStyle(ChatFormatting.GRAY));
            }
            Component word = Text.t("obelisk.offering", "%s gibt %s %s", Component.literal(name).withStyle(ChatFormatting.WHITE),
                    Component.literal(Text.number(amount)).withStyle(ChatFormatting.WHITE), Text.item(item.item())).withStyle(ChatFormatting.GRAY);
            for (ServerPlayer p : level.players()) {
                if (p.blockPosition().distSqr(core) < 64 * 64) p.displayClientMessage(word, true);
            }
        }
        if (size >= 3) {
            level.playSound(null, core.above(17), net.minecraft.sounds.SoundEvents.BEACON_ACTIVATE, net.minecraft.sounds.SoundSource.BLOCKS, 2.0f, 1.0f);
            server.getPlayerList().broadcastSystemMessage(Text.t("obelisk.great_offering", "Eine große Gabe: %s gibt %s %s an den Obelisken.",
                    Component.literal(name).withStyle(ChatFormatting.WHITE), Component.literal(Text.number(amount)).withStyle(ChatFormatting.WHITE),
                    Text.item(item.item())).withStyle(ChatFormatting.GOLD), false);
            double cx = core.getX() + 0.5, cz = core.getZ() + 0.5;
            for (int t = 0; t < 8; t++) {
                int step = t;
                scheduler.at(t * 2, () -> {
                    double r = 1.5 + step * 1.5;
                    for (int a = 0; a < 24; a++) {
                        double ang = a / 24.0 * Math.PI * 2;
                        level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD, cx + Math.cos(ang) * r, core.getY() + 0.3, cz + Math.sin(ang) * r, 1, 0, 0.01, 0, 0);
                    }
                });
            }
        }
        if (player != null) streak(player);
    }

    /** The given item flies from the giver's hand into the trunk, as a short arc of item particles. */
    /** A light that runs up the trunk from the plinth to the crystal. */
    private void pulse(ServerLevel level, BlockPos core) {
        double cx = core.getX() + 0.5, cz = core.getZ() + 0.5;
        for (int i = 0; i < 14; i++) {
            int y = 3 + i;
            scheduler.at(i, () -> {
                for (net.minecraft.core.Direction d : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                    level.sendParticles(y % 3 == 0 ? KwParticles.RUNE.get() : net.minecraft.core.particles.ParticleTypes.END_ROD,
                            cx + d.getStepX() * 1.6, core.getY() + y + 0.5, cz + d.getStepZ() * 1.6, 1, 0, 0, 0, 0);
                }
            });
        }
    }

    /** The streak before today's gift set it: last day, length, when (seconds). For /kw undo. */
    private final java.util.Map<java.util.UUID, int[]> streakBefore = new java.util.HashMap<>();

    /**
     * /kw undo took back the gift that set today's streak (within its ten seconds): the day
     * does not count (review R2-13).
     */
    public void undoStreak(java.util.UUID player) {
        int[] b = streakBefore.remove(player);
        if (b == null || System.currentTimeMillis() / 1000 - b[2] > 15) return;
        data().setStreak(player, b[0], b[1]);
    }

    /**
     * The daily offering: the first deposit of a player each day counts for their streak. A
     * streak of seven lights an ember that follows the player around the obelisk.
     */
    private void streak(ServerPlayer player) {
        ObeliskData d = data();
        int day = (int) (java.time.LocalDate.now(KronwerkeConfig.zone()).toEpochDay());
        int lastDay = d.streakDay(player.getUUID());
        if (lastDay == day) return;
        int length = lastDay == day - 1 ? d.streak(player.getUUID()) + 1 : 1;
        streakBefore.put(player.getUUID(), new int[]{lastDay, d.streak(player.getUUID()), (int) (System.currentTimeMillis() / 1000)});
        d.setStreak(player.getUUID(), day, length);
        Component msg = length == 1
                ? Text.t("obelisk.tithe_first", "Tagesgabe. Komm morgen wieder, dann zählt die Serie.")
                : Text.t("obelisk.tithe", "Tagesgabe, Tag %s in Folge.", length);
        player.displayClientMessage(msg.copy().withStyle(ChatFormatting.GOLD), false);
        player.playNotifySound(net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP, net.minecraft.sounds.SoundSource.PLAYERS, 0.8f, 1.4f);
        if (length == 7) {
            player.displayClientMessage(Text.t("obelisk.tithe_week", "Sieben Tage ohne Pause. Der Obelisk schenkt dir eine Glut, die dich hier begleitet.").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
        }
    }

    /** The ember of a seven day streak: a small flame that follows the player near the obelisk. */
    public void embers() {
        ObeliskData d = data();
        if (!d.isSet()) return;
        ServerLevel level = obeliskLevel();
        if (level == null) return;
        int today = (int) (java.time.LocalDate.now(KronwerkeConfig.zone()).toEpochDay());
        for (ServerPlayer p : level.players()) {
            if (d.streak(p.getUUID()) < 7 || d.streakDay(p.getUUID()) < today - 1) continue;
            if (p.blockPosition().distSqr(d.pos()) > 96 * 96) continue;
            double a = (level.getGameTime() % 100) / 100.0 * Math.PI * 2;
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.SMALL_FLAME, p.getX() + Math.cos(a) * 0.6, p.getY() + 1.9 + Math.sin(a * 2) * 0.1, p.getZ() + Math.sin(a) * 0.6, 1, 0, 0, 0, 0);
        }
    }

    private void trail(ServerLevel level, int index) {
        BlockPos from = ObeliskStructure.pedestal(data().pos(), index);
        BlockPos to = data().pos().above(5);
        for (int i = 0; i <= 14; i++) {
            double t = i / 14.0;
            double x = from.getX() + 0.5 + (to.getX() - from.getX()) * t;
            double y = from.getY() + 1.4 + (to.getY() - from.getY() - 0.9) * t + Math.sin(t * Math.PI) * 1.5;
            double z = from.getZ() + 0.5 + (to.getZ() - from.getZ()) * t;
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD, x, y, z, 1, 0, 0, 0, 0);
        }
    }

    /** Every five seconds: the pillar progress above the pedestals and the lines of the wall. */
    public void refreshDisplays() {
        ServerLevel level = obeliskLevel();
        if (level == null) return;
        GoalManager gm = GoalManager.get();
        List<Goal> active = gm.activeGoals();
        Goal g = active.isEmpty() ? null : active.get(0);
        // the weakest pillar flickers on its pedestal
        int lowestIndex = -1, lowestPercent = 101;
        if (g != null) {
            for (int i = 0; i < g.pillars().size() && i < 3; i++) {
                int pct = pillarPercent(gm, g, g.pillars().get(i));
                if (pct < lowestPercent) {
                    lowestPercent = pct;
                    lowestIndex = i;
                }
            }
        }
        for (int i = 0; i < 3; i++) {
            ObeliskPedestalBlockEntity pe = pedestal(level, i);
            if (pe == null) continue;
            if (g == null || i >= g.pillars().size()) {
                pe.show(null, "", pe.line());
                pe.gauge(i, 0, false);
                pe.champion(null, "");
                continue;
            }
            Goal.Pillar pillar = g.pillars().get(i);
            int pct = pillarPercent(gm, g, pillar);
            pe.show(null, pillar.title() + "  " + pct + "%", pe.line());
            pe.gauge(i, pct, i == lowestIndex && lowestPercent < 100 && g.pillars().size() > 1);
            // the champion of the pillar wears the crown on its pedestal
            List<java.util.Map.Entry<UUID, Long>> top = gm.leaderboard(g, pillar, 1);
            if (top.isEmpty()) pe.champion(null, "");
            else pe.champion(top.get(0).getKey(), nameOf(top.get(0).getKey()));
        }
        ObeliskTopBlockEntity top = top(level);
        if (top != null && top.rite() == 0) {
            int tier = tier();
            if (g != null) {
                int mood = gm.isHeld(g) ? ObeliskTopBlockEntity.MOOD_HELD : ObeliskTopBlockEntity.MOOD_RUNNING;
                top.show((int) Math.round(gm.fraction(g) * 100), mood, tier);
            } else {
                top.show(tier > 0 ? 100 : 0, tier > 0 ? ObeliskTopBlockEntity.MOOD_DONE : ObeliskTopBlockEntity.MOOD_IDLE, tier);
            }
            if (!lightsChecked) relight(level);
        }
        refreshBoard(level, g);
        broadcastState(level, top);
    }

    private de.kronwerke.core.net.KwNetwork.StatePayload state;

    /** The far effects need to know where the obelisk is and how it feels, even out of render range. */
    private void broadcastState(ServerLevel level, ObeliskTopBlockEntity top) {
        de.kronwerke.core.net.KwNetwork.StatePayload now = top == null
                ? new de.kronwerke.core.net.KwNetwork.StatePayload("", 0, 0, 0, 0, -1, 0, 0, 0)
                : new de.kronwerke.core.net.KwNetwork.StatePayload(level.dimension().location().toString(), top.getBlockPos().getX() + 0.5, top.getBlockPos().getY() + 22 / 16.0 + 0.8, top.getBlockPos().getZ() + 0.5,
                data().pos().getY(), top.tier(), top.mood(), top.percent(), crowd(level));
        if (now.equals(state)) return;
        state = now;
        for (ServerPlayer p : level.players()) net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(p, state);
    }

    private de.kronwerke.core.net.KwNetwork.SkyPayload sky;

    /** Tears the sky open for every player in the obelisk's dimension; late joiners get it on login. */
    public void tearSky(ServerLevel level, long start, int duration, int tier, double x, double y, double z) {
        sky = new de.kronwerke.core.net.KwNetwork.SkyPayload(start, duration, tier, x, y, z);
        for (ServerPlayer p : level.players()) net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(p, sky);
    }

    /** Called when a player joins: a sky that is still open is shown to them too. */
    public void onJoin(ServerPlayer p) {
        if (server == null) return;
        ServerLevel level = obeliskLevel();
        if (level == null || p.level() != level) return;
        if (state != null) net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(p, state);
        if (sky == null || level.getGameTime() > sky.start() + sky.duration()) return;
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(p, sky);
    }

    /** The hold is lifted: the obelisk wakes for everyone to see, so the streams can turn to it. */
    public void onRelease(Goal g) {
        if (server == null) return;
        ServerLevel level = obeliskLevel();
        ObeliskRite.title(server, Text.t("obelisk.awakens", "Der Obelisk erwacht").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD),
                Text.t("obelisk.awakens_sub", "%s nimmt die letzten Gaben an", g.name()).withStyle(ChatFormatting.GRAY));
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.playNotifySound(net.minecraft.sounds.SoundEvents.BEACON_ACTIVATE, net.minecraft.sounds.SoundSource.MASTER, 0.8f, 0.7f);
        }
        if (level == null || !level.isLoaded(data().pos())) return;
        ObeliskTopBlockEntity top = top(level);
        if (top != null) {
            top.show(top.percent(), ObeliskTopBlockEntity.MOOD_RUNNING, tier());
            top.flash(1.0f);
        }
    }

    /** A goal just completed: the rite, and the build grows. */
    /**
     * Starts the rite for a completed goal; false when there is no obelisk to hold it. A
     * goal completed from far away (a remote deposit, an admin) loads the obelisk's chunk
     * for it, so the rite and the new tier always come.
     */
    public boolean celebrate(Goal done) {
        if (server == null || !data().isSet()) return false;
        ServerLevel level = obeliskLevel();
        if (level == null) return false;
        if (!level.isLoaded(data().pos())) {
            level.getChunkSource().addRegionTicket(net.minecraft.server.level.TicketType.PORTAL, new net.minecraft.world.level.ChunkPos(data().pos()), 3, data().pos());
            level.getChunk(data().pos());
        }
        ObeliskRite.start(this, level, done, tier());
        return true;
    }

    /** Puts the build at the tier the goals say, without the rite; for the build command and after a reset. */
    public void settleTier() {
        if (server == null || !data().isSet()) return;
        ServerLevel level = obeliskLevel();
        if (level == null) return;
        ObeliskData d = data();
        int want = tier();
        if (d.builtTier() == want) return;
        if (want < d.builtTier()) ObeliskTiers.clear(level, d);
        for (ObeliskTiers.Placement p : ObeliskTiers.upTo(d.pos(), want)) ObeliskTiers.place(level, d, p);
        d.setBuiltTier(want);
    }

    void refreshBoard(ServerLevel level, Goal active) {
        ObeliskData d = data();
        if (d.board() == null || !level.isLoaded(d.board())) return;
        if (!(level.getBlockEntity(d.board()) instanceof ObeliskBoardBlockEntity be)) return;
        GoalManager gm = GoalManager.get();
        Goal g = active;
        if (g == null) {
            for (Goal each : gm.allGoals()) if (gm.progressData().isCompleted(each.id())) g = each;
        }
        List<String> lines = new ArrayList<>();
        lines.add("Die fleißigsten Hände");
        var season = de.kronwerke.core.season.Season.get();
        String sub = g == null ? "" : g.title();
        if (!season.running()) sub = sub.isEmpty() ? "Vorbereitung" : sub + ", Vorbereitung";
        lines.add(sub);
        if (g != null) {
            int rank = 1;
            for (Map.Entry<UUID, Long> e : gm.leaderboard(g, Math.max(3, d.boardHeight() * 3 - 3))) {
                lines.add(rank++ + ".  " + nameOf(e.getKey()) + "\t" + Text.number(e.getValue()));
            }
        }
        if (lines.size() == 2) lines.add("Noch hat niemand etwas gegeben.\t");
        be.show(lines);
    }

    /** Puts the wall up in front of the player, facing them; an older wall is taken down first. */
    public String placeBoard(ServerPlayer player, int width, int height) {
        ServerLevel level = player.serverLevel();
        ObeliskData d = data();
        if (d.isSet() && !level.dimension().location().toString().equals(d.dimension()))
            return "Die Wand muss in der Welt des Obelisken stehen, sonst bleibt sie leer.";
        net.minecraft.core.Direction look = player.getDirection();
        net.minecraft.core.Direction facing = look.getOpposite();
        net.minecraft.core.Direction right = look.getClockWise();
        BlockPos center = player.blockPosition().relative(look, 4);
        BlockPos anchor = center.relative(right, -(width / 2));
        // never over what stands there (except an older wall, which comes down first)
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < height; j++) {
                BlockPos p = anchor.relative(right, i).above(j);
                var there = level.getBlockState(p);
                if (!there.isAir() && !there.canBeReplaced() && !there.is(ObeliskBlocks.OBELISK_BOARD.get()))
                    return "Da steht etwas im Weg (" + p.getX() + " " + p.getY() + " " + p.getZ() + "). Stell dich vor eine freie Fläche.";
            }
        }
        removeBoard();
        var state = ObeliskBlocks.OBELISK_BOARD.get().defaultBlockState().setValue(ObeliskBoardBlock.FACING, facing);
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < height; j++) {
                BlockPos p = anchor.relative(right, i).above(j);
                level.setBlock(p, state.setValue(ObeliskBoardBlock.ANCHOR, i == 0 && j == 0)
                        .setValue(ObeliskBoardBlock.LEFT, i > 0).setValue(ObeliskBoardBlock.RIGHT, i < width - 1)
                        .setValue(ObeliskBoardBlock.DOWN, j > 0).setValue(ObeliskBoardBlock.UP, j < height - 1), 3);
            }
        }
        if (level.getBlockEntity(anchor) instanceof ObeliskBoardBlockEntity be) be.setSize(width, height);
        d.setBoard(anchor, facing.getName(), width, height);
        refreshDisplays();
        return "Die Ranglisten-Wand steht bei " + anchor.getX() + " " + anchor.getY() + " " + anchor.getZ() + ".";
    }

    public boolean removeBoard() {
        ObeliskData d = data();
        if (d.board() == null) return false;
        ServerLevel level = obeliskLevel();
        if (level == null) level = server.overworld();
        net.minecraft.core.Direction facing = net.minecraft.core.Direction.byName(d.boardFacing());
        if (facing == null) facing = net.minecraft.core.Direction.NORTH;
        net.minecraft.core.Direction right = facing.getOpposite().getClockWise();
        for (int i = 0; i < d.boardWidth(); i++) {
            for (int j = 0; j < d.boardHeight(); j++) {
                BlockPos p = d.board().relative(right, i).above(j);
                if (level.getBlockState(p).is(ObeliskBlocks.OBELISK_BOARD.get())) level.removeBlock(p, false);
            }
        }
        d.setBoard(null, "north", 0, 0);
        return true;
    }

    public String nameOf(UUID id) {
        ServerPlayer online = server.getPlayerList().getPlayer(id);
        if (online != null) return online.getGameProfile().getName();
        if (server.getProfileCache() != null) {
            var p = server.getProfileCache().get(id);
            if (p.isPresent()) return p.get().getName();
        }
        return id.toString();
    }
}
