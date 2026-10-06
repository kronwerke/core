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
 * The sequence when a goal completes, about forty seconds long and built so every phase can
 * be clipped on stream. Everything runs off one start time that the top block entity carries
 * to the clients, so the renderer and the server play the same clock.
 *
 * <ol>
 * <li>Silence (1.5 s): everything on the obelisk freezes.</li>
 * <li>Intake (3 s): the rune bands light from the bottom up with a rising scale.</li>
 * <li>Burst (2 s): the crystal bursts, a dome of light, thunder, lightning on the tip, the title.</li>
 * <li>Reformation (10 s): the crystal reforms, the new tier is built block by block.</li>
 * <li>Roll call (20 s): the wall is carved anew, the pedestals name the top three per pillar,
 * fireworks in the pillar colours.</li>
 * <li>Unlock: what the next stage brings, one line at a time.</li>
 * </ol>
 */
public final class ObeliskRite {
    public static final int FREEZE = 30, INTAKE = 60, BURST = 40, REFORM = 200, ROLL = 400;
    public static final int T_INTAKE = FREEZE, T_BURST = FREEZE + INTAKE, T_REFORM = T_BURST + BURST, T_ROLL = T_REFORM + REFORM, T_END = T_ROLL + ROLL;
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

        // 1. silence: the hum stops, a single low note; then the riser carries the intake to the burst
        s.at(0, () -> level.playSound(null, core.above(10), SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 2.0f, 0.6f));
        s.at(T_INTAKE - 20, () -> {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) p.playNotifySound(KwSounds.RISER.get(), SoundSource.MASTER, 0.9f, 1.0f);
        });

        // 2. intake: twelve segments of the trunk light up with a rising pentatonic scale
        float[] scale = {0.5f, 0.56f, 0.63f, 0.75f, 0.84f, 1.0f, 1.12f, 1.26f, 1.5f, 1.68f, 1.89f, 2.0f};
        for (int i = 0; i < 12; i++) {
            int seg = i;
            s.at(T_INTAKE + i * 5, () -> {
                double y = core.getY() + 3 + seg;
                level.playSound(null, BlockPos.containing(cx, y, cz), SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, 1.5f, scale[seg]);
                for (int a = 0; a < 16; a++) {
                    double ang = a / 16.0 * Math.PI * 2;
                    level.sendParticles(a % 4 == 0 ? KwParticles.RUNE.get() : ParticleTypes.END_ROD, cx + Math.cos(ang) * 1.7, y + 0.5, cz + Math.sin(ang) * 1.7, 1, 0, 0.02, 0, 0);
                }
            });
        }

        // 3. burst
        s.at(T_BURST, () -> {
            level.sendParticles(ParticleTypes.FLASH, cx, tipY, cz, 1, 0, 0, 0, 0);
            level.sendParticles(ParticleTypes.SONIC_BOOM, cx, tipY, cz, 1, 0, 0, 0, 0);
            level.sendParticles(ParticleTypes.END_ROD, cx, tipY, cz, 240, 0, 0, 0, 0.7);
            level.sendParticles(ParticleTypes.GLOW, cx, tipY, cz, 80, 0, 0, 0, 0.4);
            level.playSound(null, BlockPos.containing(cx, tipY, cz), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 3.0f, 0.9f);
            for (ServerPlayer p : server.getPlayerList().getPlayers()) p.playNotifySound(KwSounds.TEAR.get(), SoundSource.MASTER, 1.0f, 1.0f);
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
            if (bolt != null) {
                bolt.moveTo(cx, tipY, cz);
                bolt.setVisualOnly(true);
                level.addFreshEntity(bolt);
            }
            int n = newTier;
            title(server, Component.literal("STUFE " + roman(n) + " GESCHAFFT").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                    Component.literal(done.title()).withStyle(ChatFormatting.YELLOW));
            // the sky tears open for everyone, and stays open longer with every stage
            ob.tearSky(level, level.getGameTime(), 400 * Math.max(1, n), n, cx, tipY, cz);
        });
        // the dome: rings of light that run outward along the ground
        for (int t = 0; t < 12; t++) {
            int step = t;
            s.at(T_BURST + 2 + t * 2, () -> {
                double r = 2 + step * 2.2;
                int count = 16 + step * 4;
                for (int a = 0; a < count; a++) {
                    double ang = a / (double) count * Math.PI * 2;
                    level.sendParticles(ParticleTypes.END_ROD, cx + Math.cos(ang) * r, core.getY() + 0.3 + step * 0.15, cz + Math.sin(ang) * r, 1, 0, 0.01, 0, 0);
                }
            });
        }

        // 4. reformation: the crystal grows back while the new tier is built, one block every two ticks
        List<ObeliskTiers.Placement> blocks = ObeliskTiers.added(core, newTier);
        for (int i = 0; i < blocks.size(); i++) {
            ObeliskTiers.Placement p = blocks.get(i);
            int delay = T_REFORM + Math.min(REFORM - 10, i * 2);
            s.at(delay, () -> {
                if (ObeliskTiers.place(level, ob.data(), p)) {
                    level.playSound(null, p.pos(), p.state().getSoundType().getPlaceSound(), SoundSource.BLOCKS, 0.6f, 0.9f + level.random.nextFloat() * 0.2f);
                    level.sendParticles(ParticleTypes.END_ROD, p.pos().getX() + 0.5, p.pos().getY() + 0.8, p.pos().getZ() + 0.5, 3, 0.2, 0.2, 0.2, 0.01);
                }
            });
        }
        s.at(T_REFORM + REFORM - 5, () -> ob.data().setBuiltTier(newTier));

        // 5. roll call: the wall, the top three per pillar on the pedestals, fireworks
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
        for (int volley = 0; volley < 3; volley++) {
            int v = volley;
            s.at(T_ROLL + 20 + volley * 30, () -> {
                for (int i = 0; i < 4; i++) {
                    BlockPos p = ObeliskStructure.pedestal(core, i);
                    int colour = PILLAR_COLOURS[Math.min(i, 2)];
                    firework(level, p.getX() + 0.5, p.getY() + 1.2, p.getZ() + 0.5, colour, v == 2 ? 2 : 1);
                }
            });
        }

        // 6. unlock: what comes next
        s.at(T_ROLL + 120, () -> {
            Goal next = null;
            for (Goal g : GoalManager.get().activeGoals()) next = g;
            Component line = next == null
                    ? Component.literal("Alle Stufen sind geschafft. Der Obelisk ruht in Gold.").withStyle(ChatFormatting.GOLD)
                    : Component.literal("Stufe " + roman(newTier + 1) + " beginnt: " + next.title()).withStyle(ChatFormatting.GOLD);
            for (ServerPlayer p : server.getPlayerList().getPlayers()) p.displayClientMessage(line, true);
            level.playSound(null, core.above(10), SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, 1.5f, 1.2f);
            if (next != null && !next.description().isEmpty()) {
                Component desc = Component.literal(next.description()).withStyle(ChatFormatting.GRAY);
                s.at(40, () -> {
                    for (ServerPlayer p : server.getPlayerList().getPlayers()) p.displayClientMessage(desc, true);
                });
            }
        });
        s.at(T_END, () -> {
            ObeliskTopBlockEntity t = ob.top(level);
            if (t != null) t.rite(0);
            ob.refreshDisplays();
        });
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
