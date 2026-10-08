package de.kronwerke.core.mobs;

import de.kronwerke.core.config.KronwerkeConfig;
import de.kronwerke.core.goal.Goal;
import de.kronwerke.core.goal.GoalManager;
import de.kronwerke.core.link.Role;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Hostile mobs grow with the server: every stage that opens makes them tougher (health, damage,
 * armor). Some mobs only spawn naturally from a stage on, and some spawn less often, so one mod
 * does not crowd out the rest. Spawners, eggs and structures are not touched; bosses scale with
 * the group in BossScaling instead. A side world learns the stage from main over the bus.
 */
public final class MobStages {
    private static final ResourceLocation HEALTH_ID = ResourceLocation.fromNamespaceAndPath("kronwerke", "stage_health");
    private static final ResourceLocation DAMAGE_ID = ResourceLocation.fromNamespaceAndPath("kronwerke", "stage_damage");
    private static final ResourceLocation ARMOR_ID = ResourceLocation.fromNamespaceAndPath("kronwerke", "stage_armor");

    private static volatile int remoteStage = 1;
    private static Map<String, Integer> minStage;
    private static Map<String, Double> chance;

    private MobStages() {
    }

    public static void reload() {
        minStage = null;
        chance = null;
    }

    /** The stage that is open: 1 plus every completed goal that opens one. */
    public static int stage() {
        if (!Role.main()) return remoteStage;
        GoalManager g = GoalManager.get();
        if (g == null) return 1;
        int n = 1;
        for (Goal goal : g.allGoals()) if (goal.stages() != null && !goal.stages().isEmpty() && g.isCompleted(goal)) n++;
        return Math.min(n, 5);
    }

    /** A side world: main said which stage is open. */
    public static void setRemoteStage(int s) {
        remoteStage = Math.max(1, Math.min(5, s));
    }

    private static Map<String, Integer> minStage() {
        if (minStage == null) {
            Map<String, Integer> m = new HashMap<>();
            for (String s : KronwerkeConfig.MOB_MIN_STAGE.get()) {
                int eq = s.lastIndexOf('=');
                if (eq > 0) m.put(s.substring(0, eq).trim(), Integer.parseInt(s.substring(eq + 1).trim()));
            }
            minStage = m;
        }
        return minStage;
    }

    private static Map<String, Double> chance() {
        if (chance == null) {
            Map<String, Double> m = new HashMap<>();
            for (String s : KronwerkeConfig.MOB_SPAWN_CHANCE.get()) {
                int eq = s.lastIndexOf('=');
                if (eq > 0) m.put(s.substring(0, eq).trim(), Double.parseDouble(s.substring(eq + 1).trim()));
            }
            chance = m;
        }
        return chance;
    }

    /** The value for an entity id: its own entry, else its mod's "namespace:*". */
    private static <T> T lookup(Map<String, T> m, String id) {
        T v = m.get(id);
        if (v != null) return v;
        return m.get(id.substring(0, id.indexOf(':') + 1) + "*");
    }

    private static boolean hostile(LivingEntity e) {
        return e instanceof Enemy || e.getType().getCategory() == MobCategory.MONSTER;
    }

    /** Natural spawns of a later stage do not happen yet; crowded mods spawn less. */
    public static void onSpawn(FinalizeSpawnEvent e) {
        MobSpawnType t = e.getSpawnType();
        if (t != MobSpawnType.NATURAL && t != MobSpawnType.CHUNK_GENERATION) return;
        String id = EntityType.getKey(e.getEntity().getType()).toString();
        Integer need = lookup(minStage(), id);
        if (need != null && stage() < need) {
            e.setSpawnCancelled(true);
            return;
        }
        Double c = lookup(chance(), id);
        if (c != null && c < 1.0 && e.getLevel().getRandom().nextDouble() >= c) e.setSpawnCancelled(true);
    }

    /** Every hostile mob that enters a world carries the modifiers of the open stage. */
    public static void onJoin(EntityJoinLevelEvent e) {
        if (e.getLevel().isClientSide() || !(e.getEntity() instanceof Mob mob) || !hostile(mob)) return;
        if (KronwerkeConfig.BOSSES.get().contains(EntityType.getKey(mob.getType()).toString())) return;
        int s = stage();
        List<? extends Double> hp = KronwerkeConfig.MOB_HEALTH.get(), dmg = KronwerkeConfig.MOB_DAMAGE.get(), arm = KronwerkeConfig.MOB_ARMOR.get();
        apply(mob, Attributes.MAX_HEALTH, HEALTH_ID, at(hp, s), AttributeModifier.Operation.ADD_MULTIPLIED_BASE, true);
        apply(mob, Attributes.ATTACK_DAMAGE, DAMAGE_ID, at(dmg, s), AttributeModifier.Operation.ADD_MULTIPLIED_BASE, false);
        apply(mob, Attributes.ARMOR, ARMOR_ID, at(arm, s), AttributeModifier.Operation.ADD_VALUE, false);
    }

    private static double at(List<? extends Double> l, int stage) {
        if (l.isEmpty()) return 0;
        return l.get(Math.min(stage, l.size()) - 1);
    }

    private static void apply(Mob mob, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr, ResourceLocation id,
                              double amount, AttributeModifier.Operation op, boolean health) {
        AttributeInstance inst = mob.getAttribute(attr);
        if (inst == null) return;
        AttributeModifier old = inst.getModifier(id);
        if (old != null && Math.abs(old.amount() - amount) < 1e-6) return;
        float fraction = health ? mob.getHealth() / mob.getMaxHealth() : 0;
        inst.removeModifier(id);
        if (amount != 0) inst.addPermanentModifier(new AttributeModifier(id, amount, op));
        if (health) mob.setHealth(Math.max(1f, fraction * mob.getMaxHealth()));
    }
}
