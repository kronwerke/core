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
 * right click to deposit everything that fits. Items from machines come in through the
 * {@link ObeliskIntakeBlock} next to it, credited to the player who placed the intake.
 * Containers as feeders are off by default (obelisk.containerFeeders).
 */
public final class Obelisk {
    private static final Obelisk INSTANCE = new Obelisk();

    private MinecraftServer server;
    private int ticks;
    private final ObeliskScheduler scheduler = new ObeliskScheduler();
    private int ambientTicks, misfireIn;
    /** the pillar index of a deposit that is being batched from the intake, with the amount, to flare once */

    public static Obelisk get() {
        return INSTANCE;
    }

    public void init(MinecraftServer server) {
        this.server = server;
        this.ticks = 0;
        this.scheduler.clear();
        this.misfireIn = 6000;
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

        GoalManager gm = GoalManager.get();
        List<Goal> active = gm.activeGoals();
        if (active.isEmpty()) {
            player.sendSystemMessage(Text.t("obelisk.rests", "Der Obelisk ruht. Gerade ist kein Gemeinschaftsziel offen.").withStyle(ChatFormatting.GRAY));
            return;
        }
        long taken;
        if (player.isShiftKeyDown()) {
            taken = gm.depositAll(player);
        } else {
            ItemStack held = player.getMainHandItem();
            taken = gm.deposit(player, held);
        }
        if (taken > 0) {
            player.sendSystemMessage(Text.t("obelisk.took", "Der Obelisk nimmt %s.", Text.number(taken)).withStyle(ChatFormatting.GREEN));
            return;
        }
        Goal g = active.get(0);
        if (gm.isHeld(g)) {
            player.sendSystemMessage(Text.t("obelisk.held", "Der Obelisk wartet auf das Event. Die letzten Teile gehen gemeinsam rein.").withStyle(ChatFormatting.LIGHT_PURPLE));
            return;
        }
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
        player.sendSystemMessage(Text.t("obelisk.hint", "Rechtsklick gibt den Stapel in der Hand ab, Schleichen und Rechtsklick alles, was passt.").withStyle(ChatFormatting.GRAY));
    }

    // ---- feeders ----

    public void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!KronwerkeConfig.CONTAINER_FEEDERS.get()) return;
        if (server == null || !(event.getEntity() instanceof ServerPlayer player) || !(event.getLevel() instanceof ServerLevel level)) return;
        ObeliskData d = data();
        if (!d.isSet() || !level.dimension().location().toString().equals(d.dimension())) return;
        BlockPos pos = event.getPos();
        if (pos.equals(d.pos()) || distance(pos, d.pos()) > KronwerkeConfig.FEEDER_RADIUS.get()) return;
        if (level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null) == null) return;

        int max = KronwerkeConfig.FEEDERS_PER_PLAYER.get();
        long owned = d.feeders().values().stream().filter(u -> u.equals(player.getUUID())).count();
        if (owned >= max) {
            player.sendSystemMessage(Text.t("obelisk.feeders_full", "Du hast schon %s Zubringer am Obelisken. Dieser zählt nicht.", owned).withStyle(ChatFormatting.GRAY));
            return;
        }
        d.addFeeder(pos, player.getUUID());
        player.sendSystemMessage(Text.t("obelisk.feeder", "Das ist jetzt ein Zubringer. Alles, was hier reinkommt, zählt für dich.").withStyle(ChatFormatting.GREEN));
    }

    public void onBreak(BlockEvent.BreakEvent event) {
        if (server == null || !(event.getLevel() instanceof ServerLevel level)) return;
        ObeliskData d = data();
        if (!d.isSet() || !level.dimension().location().toString().equals(d.dimension())) return;
        d.removeFeeder(event.getPos());
    }

    private int displayTicks;

    public void onServerTick(ServerTickEvent.Post event) {
        if (server == null) return;
        scheduler.tick();
        if (++displayTicks >= 100) {
            displayTicks = 0;
            refreshDisplays();
            GoalManager.get().refreshBossBar();
        }
        ambient();
        if (ambientTicks % 4 == 0) embers();
        if (++ticks < KronwerkeConfig.FEEDER_INTERVAL.get() * 20) return;
        ticks = 0;
        drain();
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

    /** True when nobody has given anything for a day. */
    public boolean slumbering() {
        if (server == null) return false;
        long last = data().lastDepositAt();
        return last > 0 && System.currentTimeMillis() - last > 24L * 60 * 60 * 1000;
    }

    private void ambient() {
        ObeliskData d = data();
        if (!d.isSet() || ++ambientTicks % 20 != 0) return;
        ServerLevel level = obeliskLevel();
        if (level == null) return;
        BlockPos core = d.pos();
        if (!level.isLoaded(core) || !level.hasNearbyAlivePlayer(core.getX() + 0.5, core.getY() + 10, core.getZ() + 0.5, 48)) return;
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
        } else if (ambientTicks % 80 == 0) {
            // the hum rises with the goal
            float f = g == null ? 0.2f : (float) gm.fraction(g);
            float volume = slumbering() ? 0.15f : 0.3f + 0.5f * f;
            level.playSound(null, at, net.minecraft.sounds.SoundEvents.BEACON_AMBIENT, net.minecraft.sounds.SoundSource.BLOCKS, volume, 0.8f + 0.3f * f);
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
        boolean wasAsleep = slumbering();
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
        if (wasAsleep) size = Math.max(size, 2);
        float[] strength = {0.3f, 0.6f, 0.85f, 1.0f};
        ObeliskTopBlockEntity top = top(level);
        if (top != null) {
            top.show((int) Math.round(gm.fraction(g) * 100), gm.isHeld(g) ? ObeliskTopBlockEntity.MOOD_HELD : ObeliskTopBlockEntity.MOOD_RUNNING, tier());
            top.flash(strength[size]);
        }
        BlockPos core = data().pos();
        ServerPlayer player = server.getPlayerList().getPlayer(who);
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

    /** A light that runs up the trunk from the plinth to the crystal. */
    private void pulse(ServerLevel level, BlockPos core) {
        double cx = core.getX() + 0.5, cz = core.getZ() + 0.5;
        for (int i = 0; i < 14; i++) {
            int y = 3 + i;
            scheduler.at(i, () -> {
                for (net.minecraft.core.Direction d : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                    level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD, cx + d.getStepX() * 1.6, core.getY() + y + 0.5, cz + d.getStepZ() * 1.6, 1, 0, 0, 0, 0);
                }
            });
        }
    }

    /**
     * The daily offering: the first deposit of a player each day counts for their streak. A
     * streak of seven lights an ember that follows the player around the obelisk.
     */
    private void streak(ServerPlayer player) {
        ObeliskData d = data();
        int day = (int) (java.time.LocalDate.now(java.time.ZoneId.of("Europe/Berlin")).toEpochDay());
        int lastDay = d.streakDay(player.getUUID());
        if (lastDay == day) return;
        int length = lastDay == day - 1 ? d.streak(player.getUUID()) + 1 : 1;
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
        int today = (int) (java.time.LocalDate.now(java.time.ZoneId.of("Europe/Berlin")).toEpochDay());
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
        for (int i = 0; i < 3; i++) {
            ObeliskPedestalBlockEntity pe = pedestal(level, i);
            if (pe == null) continue;
            if (g == null || i >= g.pillars().size()) {
                pe.show(null, "", pe.line());
                continue;
            }
            Goal.Pillar pillar = g.pillars().get(i);
            pe.show(null, pillar.title() + "  " + pillarPercent(gm, g, pillar) + "%", pe.line());
        }
        ObeliskTopBlockEntity top = top(level);
        if (top != null && top.rite() == 0) {
            int tier = tier();
            if (g != null) {
                int mood = gm.isHeld(g) ? ObeliskTopBlockEntity.MOOD_HELD : slumbering() ? ObeliskTopBlockEntity.MOOD_ASLEEP : ObeliskTopBlockEntity.MOOD_RUNNING;
                top.show((int) Math.round(gm.fraction(g) * 100), mood, tier);
            } else {
                top.show(tier > 0 ? 100 : 0, tier > 0 ? ObeliskTopBlockEntity.MOOD_DONE : ObeliskTopBlockEntity.MOOD_IDLE, tier);
            }
        }
        refreshBoard(level, g);
    }

    /** A goal just completed: the rite, and the build grows. */
    public void celebrate(Goal done) {
        if (server == null) return;
        ServerLevel level = obeliskLevel();
        if (level == null || !level.isLoaded(data().pos())) return;
        ObeliskRite.start(this, level, done, tier());
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
        removeBoard();
        net.minecraft.core.Direction look = player.getDirection();
        net.minecraft.core.Direction facing = look.getOpposite();
        net.minecraft.core.Direction right = look.getClockWise();
        BlockPos center = player.blockPosition().relative(look, 4);
        BlockPos anchor = center.relative(right, -(width / 2));
        var state = ObeliskBlocks.OBELISK_BOARD.get().defaultBlockState().setValue(ObeliskBoardBlock.FACING, facing);
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < height; j++) {
                BlockPos p = anchor.relative(right, i).above(j);
                level.setBlock(p, state.setValue(ObeliskBoardBlock.ANCHOR, i == 0 && j == 0), 3);
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

    /** Moves everything the active goal can take out of every feeder. Returns what was moved. */
    public long drain() {
        if (!KronwerkeConfig.CONTAINER_FEEDERS.get()) return 0;
        ObeliskData d = data();
        if (!d.isSet() || d.feeders().isEmpty() || GoalManager.get().activeGoals().isEmpty()) return 0;
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(d.dimension())));
        if (level == null) return 0;

        long moved = 0;
        List<BlockPos> gone = new ArrayList<>();
        for (Map.Entry<BlockPos, UUID> f : d.feeders().entrySet()) {
            BlockPos pos = f.getKey();
            if (!level.isLoaded(pos)) continue;
            IItemHandler h = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null);
            if (h == null) {
                gone.add(pos);
                continue;
            }
            UUID owner = f.getValue();
            Component name = Component.literal(nameOf(owner));
            Map<String, Long> delivered = new LinkedHashMap<>();
            for (int slot = 0; slot < h.getSlots(); slot++) {
                ItemStack peek = h.extractItem(slot, Integer.MAX_VALUE, true);
                if (peek.isEmpty()) continue;
                ItemStack copy = peek.copy();
                long taken = GoalManager.get().deposit(owner, name, copy, false);
                if (taken <= 0) continue;
                ItemStack out = h.extractItem(slot, (int) taken, false);
                moved += out.getCount();
                delivered.merge(out.getHoverName().getString(), (long) out.getCount(), Long::sum);
            }
            announce(name, delivered);
        }
        for (BlockPos p : gone) d.removeFeeder(p);
        return moved;
    }

    private void announce(Component name, Map<String, Long> delivered) {
        if (!KronwerkeConfig.BROADCAST_DEPOSITS.get()) return;
        delivered.forEach((item, n) -> {
            if (n < KronwerkeConfig.BROADCAST_DEPOSIT_MIN.get()) return;
            server.getPlayerList().broadcastSystemMessage(Text.t("obelisk.feeder_delivered", "Zubringer von %s liefert %s %s",
                    name.copy().withStyle(ChatFormatting.WHITE), Component.literal(Text.number(n)).withStyle(ChatFormatting.WHITE),
                    Component.literal(item).withStyle(ChatFormatting.AQUA)).withStyle(ChatFormatting.GRAY), false);
        });
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
