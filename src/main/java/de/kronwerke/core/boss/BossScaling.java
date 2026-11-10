package de.kronwerke.core.boss;

import de.kronwerke.core.Text;
import de.kronwerke.core.config.KronwerkeConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Bosses grow with the group. A boss on the list gets more health and more damage for every
 * player near it, and with two or more players it uses three extra attacks of its own:
 * lightning on one player, a shockwave around itself, and a rage that heals it and makes it
 * hit harder for a few seconds. All of it runs on events, nothing in the boss mods changes.
 */
public final class BossScaling {
    private static final ResourceLocation HEALTH_ID = ResourceLocation.fromNamespaceAndPath("kronwerke", "boss_group_health");
    private static final ResourceLocation DAMAGE_ID = ResourceLocation.fromNamespaceAndPath("kronwerke", "boss_group_damage");
    private static final ResourceLocation RAGE_ID = ResourceLocation.fromNamespaceAndPath("kronwerke", "boss_rage");
    private static final int RANGE = 48;

    private static Set<EntityType<?>> bosses;

    private BossScaling() {
    }

    /** The configured bosses as entity types, so the check on every entity tick allocates nothing. */
    public static Set<EntityType<?>> bossTypes() {
        Set<EntityType<?>> b = bosses;
        if (b == null) {
            b = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
            for (String id : KronwerkeConfig.BOSSES.get()) {
                ResourceLocation rl = ResourceLocation.tryParse(id);
                if (rl != null) net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getOptional(rl).ifPresent(b::add);
            }
            bosses = b;
        }
        return b;
    }

    private static boolean isBoss(LivingEntity e) {
        return bossTypes().contains(e.getType());
    }

    public static void reload() {
        bosses = null;
    }

    /** Who hit the boss lately (boss id -> player -> game time): fighting from range counts too. */
    private static final java.util.Map<Integer, java.util.Map<java.util.UUID, Long>> hitters = new java.util.HashMap<>();
    private static final long HIT_MEMORY = 20 * 30;

    /** The players in the fight: near the boss, or who hit it in the last thirty seconds from anywhere. */
    private static List<ServerPlayer> playersNear(ServerLevel level, LivingEntity boss) {
        var hit = hitters.getOrDefault(boss.getId(), java.util.Map.of());
        long now = level.getGameTime();
        return level.getPlayers(p -> p.isAlive() && !p.isSpectator() && !p.isCreative()
                && (p.distanceToSqr(boss) < RANGE * RANGE || now - hit.getOrDefault(p.getUUID(), Long.MIN_VALUE / 2) < HIT_MEMORY));
    }

    /** A player hurts a boss: remembered for the scaling. */
    public static void onBossHurt(net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity boss) || !(boss.level() instanceof ServerLevel level) || !isBoss(boss)) return;
        if (event.getSource().getEntity() instanceof ServerPlayer p) {
            hitters.computeIfAbsent(boss.getId(), k -> new java.util.HashMap<>()).put(p.getUUID(), level.getGameTime());
        }
        if (boss.isDeadOrDying()) hitters.remove(boss.getId());
    }

    public static void onTick(EntityTickEvent.Post event) {
        // every entity ticks through here: the cheap checks first
        if (event.getEntity().tickCount % 20 != 0) return;
        if (!(event.getEntity() instanceof LivingEntity boss) || !(boss.level() instanceof ServerLevel level) || !isBoss(boss)) return;
        List<ServerPlayer> near = playersNear(level, boss);
        int n = near.size();
        if (boss.tickCount % 40 == 0) apply(boss, n);
        // the interval in ticks, counted per boss: any value works, not only multiples of the check
        int interval = Math.max(20, KronwerkeConfig.BOSS_ATTACK_INTERVAL.get());
        int last = boss.getPersistentData().getInt("kronwerke_attack");
        if (n >= 2 && boss.tickCount - last >= interval) {
            boss.getPersistentData().putInt("kronwerke_attack", boss.tickCount);
            extraAttack(level, boss, near);
        }
    }

    /** More health and damage per extra player, capped; the health fraction stays where it was. */
    private static void apply(LivingEntity boss, int players) {
        int extra = Math.max(0, Math.min(players, KronwerkeConfig.BOSS_MAX_PLAYERS.get()) - 1);
        double health = extra * KronwerkeConfig.BOSS_HEALTH_PER_PLAYER.get();
        double damage = extra * KronwerkeConfig.BOSS_DAMAGE_PER_PLAYER.get();
        AttributeInstance hp = boss.getAttribute(Attributes.MAX_HEALTH);
        if (hp != null) {
            AttributeModifier old = hp.getModifier(HEALTH_ID);
            if (old == null || Math.abs(old.amount() - health) > 0.001) {
                float fraction = boss.getHealth() / boss.getMaxHealth();
                hp.removeModifier(HEALTH_ID);
                if (health > 0) hp.addPermanentModifier(new AttributeModifier(HEALTH_ID, health, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
                boss.setHealth(fraction * boss.getMaxHealth());
            }
        }
        AttributeInstance dmg = boss.getAttribute(Attributes.ATTACK_DAMAGE);
        if (dmg != null) {
            AttributeModifier old = dmg.getModifier(DAMAGE_ID);
            if (old == null || Math.abs(old.amount() - damage) > 0.001) {
                dmg.removeModifier(DAMAGE_ID);
                if (damage > 0) dmg.addPermanentModifier(new AttributeModifier(DAMAGE_ID, damage, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
            }
        }
    }

    private static void extraAttack(ServerLevel level, LivingEntity boss, List<ServerPlayer> players) {
        int pick = level.random.nextInt(3);
        switch (pick) {
            case 0 -> {
                // lightning on one player the boss can see (not through walls)
                List<ServerPlayer> seen = players.stream().filter(boss::hasLineOfSight).toList();
                if (seen.isEmpty()) return;
                ServerPlayer target = seen.get(level.random.nextInt(seen.size()));
                LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
                if (bolt != null) {
                    bolt.moveTo(Vec3.atBottomCenterOf(target.blockPosition()));
                    bolt.setVisualOnly(true);
                    level.addFreshEntity(bolt);
                }
                target.hurt(level.damageSources().lightningBolt(), (float) (double) KronwerkeConfig.BOSS_LIGHTNING_DAMAGE.get());
                target.displayClientMessage(Text.t("boss.lightning", "Der Boss hat dich im Blick.").withStyle(ChatFormatting.RED), true);
            }
            case 1 -> {
                // shockwave: everyone close is thrown back and hurt
                level.playSound(null, boss.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.2f, 0.6f);
                for (ServerPlayer p : players) {
                    double d = p.distanceTo(boss);
                    if (d > 10) continue;
                    Vec3 away = p.position().subtract(boss.position()).normalize().scale(2.2).add(0, 0.6, 0);
                    p.push(away.x, away.y, away.z);
                    p.hurtMarked = true;
                    p.hurt(level.damageSources().mobAttack(boss), (float) (double) KronwerkeConfig.BOSS_SHOCKWAVE_DAMAGE.get());
                }
                for (int i = 0; i < 40; i++) {
                    double a = i / 40.0 * Math.PI * 2;
                    level.sendParticles(ParticleTypes.EXPLOSION, boss.getX() + Math.cos(a) * 4, boss.getY() + 0.5, boss.getZ() + Math.sin(a) * 4, 1, 0, 0, 0, 0);
                }
            }
            default -> {
                // rage: a heal and a few seconds of harder hits
                boss.heal(boss.getMaxHealth() * 0.05f);
                boss.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 120, 0, false, true));
                boss.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 120, 0, false, true));
                level.sendParticles(ParticleTypes.ANGRY_VILLAGER, boss.getX(), boss.getY() + boss.getBbHeight(), boss.getZ(), 20, 1, 1, 1, 0);
                level.playSound(null, boss.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 1.5f, 0.7f);
            }
        }
    }

    /** Damage the boss deals through anything but its attack attribute (projectiles, abilities) scales too. */
    public static void onDamage(LivingIncomingDamageEvent event) {
        if (!(event.getSource().getEntity() instanceof LivingEntity boss) || !(boss.level() instanceof ServerLevel level) || !isBoss(boss)) return;
        if (!(event.getEntity() instanceof ServerPlayer)) return;
        int extra = Math.max(0, Math.min(playersNear(level, boss).size(), KronwerkeConfig.BOSS_MAX_PLAYERS.get()) - 1);
        if (extra == 0) return;
        AttributeInstance dmg = boss.getAttribute(Attributes.ATTACK_DAMAGE);
        // melee already carries the attribute modifier; everything else gets the factor here
        if (dmg != null && event.getSource().getDirectEntity() == boss && dmg.getModifier(DAMAGE_ID) != null) return;
        event.setAmount((float) (event.getAmount() * (1.0 + extra * KronwerkeConfig.BOSS_DAMAGE_PER_PLAYER.get())));
    }

    /** The one-time note for the Chaos Guardian fight: a player who comes close hears how it works. */
    private static final Set<java.util.UUID> told = new HashSet<>();

    public static void chaosHint(ServerPlayer p) {
        if (told.contains(p.getUUID()) || !(p.level() instanceof ServerLevel level)) return;
        AABB box = p.getBoundingBox().inflate(96);
        List<LivingEntity> guardians = level.getEntitiesOfClass(LivingEntity.class, box, e -> EntityType.getKey(e.getType()).toString().equals("draconicevolution:chaos_guardian"));
        if (guardians.isEmpty()) return;
        told.add(p.getUUID());
        p.sendSystemMessage(Text.t("boss.chaos_title", "Der Chaoswächter").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD));
        p.sendSystemMessage(Text.t("boss.chaos_1", "1. Die vier Wächterkristalle auf den Säulen heilen und schützen ihn. Solange sie stehen, ist er unverwundbar.").withStyle(ChatFormatting.LIGHT_PURPLE));
        p.sendSystemMessage(Text.t("boss.chaos_2", "2. Jeder Kristall hat einen Schild, der nachwächst: Schild runter, Kristall sofort zerstören. Pfeile, Zauber und Nahkampf zählen.").withStyle(ChatFormatting.LIGHT_PURPLE));
        p.sendSystemMessage(Text.t("boss.chaos_3", "3. Ohne Kristalle ist er verwundbar und wütend: Feuerbälle, Laser, Flächenschaden. Zusammenbleiben, heilen, beim Tanken abwechseln.").withStyle(ChatFormatting.LIGHT_PURPLE));
        p.sendSystemMessage(Text.t("boss.chaos_4", "4. Nach dem Sieg: der Chaoskristall in der Mitte. Abbauen gibt die Chaosscherben, dann fliegt die Insel in die Luft. Erst wegfliegen, dann jubeln.").withStyle(ChatFormatting.LIGHT_PURPLE));
    }

    public static void forget(java.util.UUID id) {
        told.remove(id);
    }
}
