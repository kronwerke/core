package de.kronwerke.core.obelisk;

import de.kronwerke.core.goal.Goal;
import de.kronwerke.core.goal.GoalManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;
import it.unimi.dsi.fastutil.ints.IntList;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The sequence when a goal completes, about ninety seconds in five acts, built for the
 * moment a stream clips. Everything runs off one start time that the top block entity
 * carries to the clients, so the renderers and the server play the same clock.
 *
 * <ol>
 * <li>Stillness (4 s): the hum stops, black bars close in, a riser swells.</li>
 * <li>The pull (20 s): the sky tears open, the beam comes down out of the tear, blocks of the
 * ground around the plinth are torn up and spiral round the beam (the clients draw copies,
 * the ground stays), players nearby lift off and circle with them, the air around the
 * crystal bends.</li>
 * <li>The burst (4 s, the first of them in slow motion): everything is drawn in and stops,
 * then the crystal bursts; the obelisk breaks apart in the air, the spiral is flung out,
 * the shockwave, the title.</li>
 * <li>The return (22 s): the pieces of the obelisk fly back and join, the new tier is built
 * block by block, arcs of light leap from the pylons, the players drift down.</li>
 * <li>The galaxy (30 s): the roll call on the pedestals and the wall, the fanfare,
 * fireworks, what the next stage brings; the torn sky stays open longer with every stage.</li>
 * </ol>
 */
public final class ObeliskRite {
    public static final int STILL = 80, PULL = 400, BURST = 80, REFORM = 440, ROLL = 600;
    public static final int T_PULL = STILL, T_BURST = T_PULL + PULL, T_REFORM = T_BURST + BURST, T_ROLL = T_REFORM + REFORM, T_END = T_ROLL + ROLL;
    /** the pieces of the obelisk are back in place and the stone is shown again */
    public static final int T_RETURN = T_REFORM + 120;
    /** the older names of the pull, kept for the effects that grew up with them */
    public static final int INTAKE = PULL, T_INTAKE = T_PULL;
    /** how far around the plinth players are lifted, and blocks are torn up */
    public static final int LIFT_RADIUS = 28;
    private static final int[] PILLAR_COLOURS = {0x9aa0a8, 0xd4a24a, 0x9a6fd6};

    private ObeliskRite() {
    }

    /** Schedules the whole rite for the goal that just completed. */
    public static void start(Obelisk ob, ServerLevel level, Goal done, int newTier) {
        MinecraftServer server = level.getServer();
        BlockPos core = ob.data().pos();
        ObeliskTopBlockEntity top = ob.top(level);
        long now = level.getGameTime();
        if (top != null) top.rite(now);
        ObeliskScheduler s = ob.scheduler();
        double cx = core.getX() + 0.5, cz = core.getZ() + 0.5;
        double tipY = core.getY() + 19.3;

        // 1. stillness: the hum stops, a single low note, and the riser carries into the tear
        s.at(0, () -> {
            level.playSound(null, core.above(10), SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 2.0f, 0.6f);
            for (ServerPlayer p : server.getPlayerList().getPlayers()) p.playNotifySound(KwSounds.RISER.get(), SoundSource.MASTER, 0.9f, 1.0f);
        });

        // 2. the pull: the sky tears, the storm winds up, the trunk lights segment by segment
        s.at(T_PULL, () -> {
            ob.tearSky(level, level.getGameTime(), (T_END - T_PULL) + 300 * Math.max(1, newTier), newTier, cx, tipY, cz);
            for (ServerPlayer p : server.getPlayerList().getPlayers()) p.playNotifySound(KwSounds.TEAR.get(), SoundSource.MASTER, 1.0f, 1.0f);
        });
        s.at(T_PULL + 20, () -> level.playSound(null, core.above(8), KwSounds.VORTEX.get(), SoundSource.MASTER, 4.0f, 1.0f));
        float[] scale = {0.5f, 0.56f, 0.63f, 0.75f, 0.84f, 1.0f, 1.12f, 1.26f, 1.5f, 1.68f, 1.89f, 2.0f};
        for (int i = 0; i < 12; i++) {
            int seg = i;
            s.at(T_PULL + 40 + i * 28, () -> {
                double y = core.getY() + 3 + seg;
                level.playSound(null, BlockPos.containing(cx, y, cz), SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, 1.8f, scale[seg]);
                for (int a = 0; a < 16; a++) {
                    double ang = a / 16.0 * Math.PI * 2;
                    level.sendParticles(a % 4 == 0 ? KwParticles.RUNE.get() : ParticleTypes.END_ROD, cx + Math.cos(ang) * 1.7, y + 0.5, cz + Math.sin(ang) * 1.7, 1, 0, 0.02, 0, 0);
                }
            });
        }
        // players near the plinth lift off and circle the beam, higher and faster as the pull goes on
        int liftFrom = T_PULL + 80;
        for (int t = liftFrom; t < T_BURST; t++) {
            int tick = t;
            s.at(t, () -> lift(level, core, (tick - liftFrom) / (float) (T_BURST - liftFrom), tick - liftFrom));
        }
        s.at(T_BURST - 50, () -> {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) p.playNotifySound(KwSounds.IMPLODE.get(), SoundSource.MASTER, 1.0f, 1.0f);
        });

        // 3. the burst
        s.at(T_BURST, () -> {
            level.sendParticles(ParticleTypes.FLASH, cx, tipY, cz, 1, 0, 0, 0, 0);
            level.sendParticles(ParticleTypes.SONIC_BOOM, cx, tipY, cz, 1, 0, 0, 0, 0);
            level.sendParticles(ParticleTypes.END_ROD, cx, tipY, cz, 300, 0, 0, 0, 0.9);
            level.sendParticles(ParticleTypes.GLOW, cx, tipY, cz, 100, 0, 0, 0, 0.5);
            level.playSound(null, BlockPos.containing(cx, tipY, cz), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 3.0f, 0.8f);
            for (ServerPlayer p : server.getPlayerList().getPlayers()) p.playNotifySound(KwSounds.BOOM.get(), SoundSource.MASTER, 1.0f, 1.0f);
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
            if (bolt != null) {
                bolt.moveTo(cx, tipY, cz);
                bolt.setVisualOnly(true);
                level.addFreshEntity(bolt);
            }
            // the stone breaks apart: the clients draw the pieces, the blocks themselves only hide
            ObeliskStructure.setHidden(level, core, true);
            fling(level, core);
            int n = newTier;
            title(server, Component.literal("STUFE " + roman(n) + " GESCHAFFT").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                    Component.literal(done.title()).withStyle(ChatFormatting.YELLOW));
            // the burst in slow motion: the server runs at a quarter speed for twenty of its ticks
            server.tickRateManager().setTickRate(5f);
        });
        s.at(T_BURST + 20, () -> server.tickRateManager().setTickRate(20f));
        // the dome: rings of light that run outward along the ground
        for (int t = 0; t < 14; t++) {
            int step = t;
            s.at(T_BURST + 2 + t * 2, () -> {
                double r = 2 + step * 2.4;
                int count = 16 + step * 4;
                for (int a = 0; a < count; a++) {
                    double ang = a / (double) count * Math.PI * 2;
                    level.sendParticles(ParticleTypes.END_ROD, cx + Math.cos(ang) * r, core.getY() + 0.3 + step * 0.15, cz + Math.sin(ang) * r, 1, 0, 0.01, 0, 0);
                }
            });
        }

        // 4. the return: the pieces come back, then the new tier is built, one block every two ticks or faster
        s.at(T_RETURN - 30, () -> level.playSound(null, core.above(8), SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 3.0f, 0.7f));
        s.at(T_RETURN, () -> {
            ObeliskStructure.setHidden(level, core, false);
            level.playSound(null, core.above(8), SoundEvents.RESPAWN_ANCHOR_SET_SPAWN, SoundSource.BLOCKS, 2.5f, 0.8f);
        });
        List<ObeliskTiers.Placement> blocks = ObeliskTiers.added(core, newTier);
        int buildTime = T_ROLL - T_RETURN - 40;
        int step = blocks.isEmpty() ? 2 : Math.max(1, Math.min(2, buildTime / blocks.size()));
        for (int i = 0; i < blocks.size(); i++) {
            ObeliskTiers.Placement p = blocks.get(i);
            int delay = T_RETURN + 10 + Math.min(buildTime, i * step);
            s.at(delay, () -> {
                if (ObeliskTiers.place(level, ob.data(), p)) {
                    level.playSound(null, p.pos(), p.state().getSoundType().getPlaceSound(), SoundSource.BLOCKS, 0.6f, 0.9f + level.random.nextFloat() * 0.2f);
                    level.sendParticles(ParticleTypes.END_ROD, p.pos().getX() + 0.5, p.pos().getY() + 0.8, p.pos().getZ() + 0.5, 3, 0.2, 0.2, 0.2, 0.01);
                }
            });
        }
        s.at(T_ROLL - 5, () -> ob.data().setBuiltTier(newTier));

        // 5. the galaxy: the roll call, the fanfare, fireworks
        s.at(T_ROLL, () -> {
            GoalManager gm = GoalManager.get();
            for (int i = 0; i < 3 && i < done.pillars().size(); i++) {
                ObeliskPedestalBlockEntity pe = ob.pedestal(level, i);
                if (pe == null) continue;
                Goal.Pillar pillar = done.pillars().get(i);
                StringBuilder names = new StringBuilder();
                int rank = 1;
                for (Map.Entry<UUID, Long> e : gm.leaderboard(done, pillar, 3)) {
                    if (rank > 1) names.append("  ");
                    names.append(rank++).append(". ").append(ob.nameOf(e.getKey()));
                }
                pe.show(null, pillar.title() + "  geschafft", names.length() == 0 ? "" : names.toString());
            }
            ob.refreshBoard(level, done);
            for (ServerPlayer p : server.getPlayerList().getPlayers()) p.playNotifySound(KwSounds.FANFARE.get(), SoundSource.MASTER, 0.9f, 1.0f);
        });
        for (int volley = 0; volley < 5; volley++) {
            int v = volley;
            s.at(T_ROLL + 20 + volley * 30, () -> {
                for (int i = 0; i < 4; i++) {
                    BlockPos p = ObeliskStructure.pedestal(core, i);
                    int colour = PILLAR_COLOURS[Math.min(i, 2)];
                    firework(level, p.getX() + 0.5, p.getY() + 1.2, p.getZ() + 0.5, colour, v >= 3 ? 2 : 1);
                }
            });
        }
        s.at(T_ROLL + 160, () -> {
            Goal next = null;
            for (Goal g : GoalManager.get().activeGoals()) next = g;
            Component line = next == null
                    ? Component.literal("Alle Stufen sind geschafft. Der Obelisk ruht in Gold.").withStyle(ChatFormatting.GOLD)
                    : Component.literal("Stufe " + roman(newTier + 1) + " beginnt: " + next.title()).withStyle(ChatFormatting.GOLD);
            for (ServerPlayer p : server.getPlayerList().getPlayers()) p.displayClientMessage(line, true);
            level.playSound(null, core.above(10), SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, 1.5f, 1.2f);
            if (next != null && !next.description().isEmpty()) {
                Component desc = Component.literal(next.description()).withStyle(ChatFormatting.GRAY);
                s.at(60, () -> {
                    for (ServerPlayer p : server.getPlayerList().getPlayers()) p.displayClientMessage(desc, true);
                });
            }
        });
        s.at(T_END, () -> {
            server.tickRateManager().setTickRate(20f);
            ObeliskStructure.setHidden(level, core, false);
            ObeliskTopBlockEntity t = ob.top(level);
            if (t != null) t.rite(0);
            ob.refreshDisplays();
        });
    }

    /** The players lifted by the pull: everyone within LIFT_RADIUS who is not a spectator. */
    private static List<ServerPlayer> lifted(ServerLevel level, BlockPos core) {
        List<ServerPlayer> out = new java.util.ArrayList<>();
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator()) continue;
            double dx = p.getX() - (core.getX() + 0.5), dz = p.getZ() - (core.getZ() + 0.5);
            if (dx * dx + dz * dz > LIFT_RADIUS * LIFT_RADIUS || Math.abs(p.getY() - core.getY()) > 48) continue;
            out.add(p);
        }
        return out;
    }

    /**
     * One tick of the pull for the players: each is drawn towards a ring around the beam and up,
     * and carried round it, faster as q goes from 0 to 1. Their own steering still counts a
     * little; nobody takes fall damage while it lasts.
     */
    private static void lift(ServerLevel level, BlockPos core, float q, int ticks) {
        double cx = core.getX() + 0.5, cz = core.getZ() + 0.5;
        float ease = Math.min(1f, ticks / 40f);
        for (ServerPlayer p : lifted(level, core)) {
            double dx = p.getX() - cx, dz = p.getZ() - cz;
            double rho = Math.sqrt(dx * dx + dz * dz);
            double phi = rho < 0.5 ? (p.getId() * 2.4) : Math.atan2(dz, dx);
            // each player keeps a ring of their own, between nine and fifteen out
            double ring = 9 + (Math.floorMod(p.getUUID().hashCode(), 7));
            double targetRho = rho + (ring - rho) * 0.04;
            // about two turns over the pull, the last ones fast
            double omega = 0.015 + 0.075 * q * q;
            double targetPhi = phi + omega;
            double targetY = core.getY() + 4 + 26 * q + Math.sin((ticks + p.getId() * 7) / 9.0) * 0.8;
            double tx = cx + Math.cos(targetPhi) * targetRho, tz = cz + Math.sin(targetPhi) * targetRho;
            // the velocity that reaches the target in one tick, with what gravity takes off it already added back
            net.minecraft.world.phys.Vec3 want = new net.minecraft.world.phys.Vec3(tx - p.getX(), targetY - p.getY() + 0.08, tz - p.getZ());
            if (want.length() > 1.6) want = want.normalize().scale(1.6);
            net.minecraft.world.phys.Vec3 v = p.getDeltaMovement().lerp(want, 0.85 * ease);
            p.setDeltaMovement(v);
            p.hurtMarked = true;
            p.resetFallDistance();
        }
    }

    /** At the burst the lifted players are thrown outward and fall slowly for the next twenty seconds. */
    private static void fling(ServerLevel level, BlockPos core) {
        double cx = core.getX() + 0.5, cz = core.getZ() + 0.5;
        for (ServerPlayer p : lifted(level, core)) {
            double dx = p.getX() - cx, dz = p.getZ() - cz;
            double len = Math.max(0.5, Math.sqrt(dx * dx + dz * dz));
            p.setDeltaMovement(dx / len * 1.1, 0.55, dz / len * 1.1);
            p.hurtMarked = true;
            p.resetFallDistance();
            p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOW_FALLING, 20 * 25, 0, false, false, true));
        }
    }

    static void title(MinecraftServer server, Component title, Component sub) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) title(p, title, sub);
    }

    static void title(ServerPlayer p, Component title, Component sub) {
        p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 50, 20));
        p.connection.send(new ClientboundSetSubtitleTextPacket(sub));
        p.connection.send(new ClientboundSetTitleTextPacket(title));
    }

    private static void firework(ServerLevel level, double x, double y, double z, int colour, int flight) {
        ItemStack rocket = new ItemStack(Items.FIREWORK_ROCKET);
        FireworkExplosion burst = new FireworkExplosion(flight == 2 ? FireworkExplosion.Shape.LARGE_BALL : FireworkExplosion.Shape.SMALL_BALL,
                IntList.of(colour), IntList.of(0xf6d68c), true, true);
        rocket.set(DataComponents.FIREWORKS, new Fireworks(flight, List.of(burst)));
        level.addFreshEntity(new FireworkRocketEntity(level, x, y, z, rocket));
    }

    static String roman(int n) {
        String[] r = {"", "I", "II", "III", "IV", "V", "VI", "VII"};
        return n >= 0 && n < r.length ? r[n] : Integer.toString(n);
    }
}
