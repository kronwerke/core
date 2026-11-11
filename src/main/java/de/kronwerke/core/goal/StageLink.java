package de.kronwerke.core.goal;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import de.kronwerke.core.KronwerkeCore;
import de.kronwerke.core.link.NetworkSync;
import de.kronwerke.core.link.Role;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A player's stages on every server of the network.
 * <p>
 * Chapters keeps the stages in the player's FTB team, and every server has its own teams, so a
 * stage given on main (by a goal, or all of them by the admin bypass) was unknown in the side
 * worlds: whoever went mining there stood at the start again. Main stays the one that decides:
 * <ul>
 * <li>A player who moves takes their stages along, and the side world sets exactly those.</li>
 * <li>Main says with its state which stages everybody has by now; a side world gives them to
 * whoever is there, so a stage that opens while someone is mining reaches them too.</li>
 * <li>The bypass works in a side world: it opens or closes the stages there and tells main,
 * which keeps the list and sets the player right the next time they are home.</li>
 * </ul>
 */
public final class StageLink {
    public static final String TOPIC = "kw.bypass";
    private static final boolean CHAPTERS = net.neoforged.fml.ModList.get().isLoaded("chapters");

    /** Side world: the stages every player has by now, as main last said. */
    private static volatile Set<String> earned = Set.of();
    /** Side world: who has the bypass, as main last said and as set here since. */
    private static final Set<UUID> bypass = ConcurrentHashMap.newKeySet();
    /** Side world: what a player on the way here has, until they are in. */
    private static final Map<UUID, Set<String>> arriving = new ConcurrentHashMap<>();

    private StageLink() {
    }

    // ---- Chapters ----

    /** The stages the player has here. */
    public static Set<String> of(ServerPlayer p) {
        Set<String> out = new LinkedHashSet<>();
        if (!CHAPTERS) return out;
        try {
            for (ResourceLocation id : com.gabinx.chapters.compat.ftb.EffectiveStages.of(p)) out.add(id.toString());
        } catch (RuntimeException | LinkageError e) {
            KronwerkeCore.LOGGER.warn("Could not read the stages of {}: {}", p.getGameProfile().getName(), e.toString());
        }
        return out;
    }

    /** Every stage the pack defines. */
    public static Set<String> all() {
        Set<String> out = new LinkedHashSet<>();
        if (!CHAPTERS) return out;
        try {
            for (ResourceLocation id : com.gabinx.chapters.stage.StageManager.get().stageIds()) out.add(id.toString());
        } catch (RuntimeException | LinkageError e) {
            KronwerkeCore.LOGGER.warn("Could not read the pack's stages: {}", e.toString());
        }
        return out;
    }

    /**
     * Within scope the player ends up with exactly the stages of want; stages outside scope stay
     * as they are. Returns what changed.
     */
    public static List<String> set(ServerPlayer p, Collection<String> want, Collection<String> scope) {
        List<String> changed = new ArrayList<>();
        if (!CHAPTERS) return changed;
        Set<String> has = of(p);
        for (String stage : scope) {
            boolean should = want.contains(stage);
            if (should == has.contains(stage)) continue;
            try {
                ResourceLocation id = ResourceLocation.parse(stage);
                if (should) com.gabinx.chapters.compat.ftb.EffectiveStages.add(p, id);
                else com.gabinx.chapters.compat.ftb.EffectiveStages.remove(p, id);
                changed.add(stage);
            } catch (RuntimeException | LinkageError e) {
                KronwerkeCore.LOGGER.warn("Could not {} stage {} for {}: {}", should ? "give" : "take", stage, p.getGameProfile().getName(), e.toString());
            }
        }
        return changed;
    }

    // ---- main ----

    /** main: players whose bypass a side world turned off; their extra stages close when they are home. */
    private static final Set<UUID> toClose = ConcurrentHashMap.newKeySet();

    /**
     * main, at every join: the player has the stages the goals and the bypass give. Stages are
     * only taken back for a player whose bypass a side world turned off, never on a guess.
     */
    public static void reconcile(ServerPlayer p, Collection<String> goalStages, Collection<String> earnedHere, boolean hasBypass) {
        Set<String> want = new HashSet<>(hasBypass ? goalStages : earnedHere);
        if (!toClose.remove(p.getUUID()) || hasBypass) want.addAll(of(p));
        List<String> changed = set(p, want, goalStages);
        if (!changed.isEmpty()) KronwerkeCore.LOGGER.info("Stages of {} set right: {}", p.getGameProfile().getName(), changed);
    }

    /** main: what goes out with the state, for the side worlds. */
    public static void state(JsonObject d, Collection<String> earnedHere, Collection<UUID> bypassing) {
        JsonArray s = new JsonArray();
        earnedHere.forEach(s::add);
        d.add("stages", s);
        JsonArray b = new JsonArray();
        bypassing.forEach(u -> b.add(u.toString()));
        d.add("bypass", b);
    }

    /** main: a side world turned somebody's bypass on or off. */
    public static void onBypassMessage(MinecraftServer server, JsonObject d) {
        if (!Role.main() || server == null) return;
        try {
            UUID id = UUID.fromString(d.get("uuid").getAsString());
            boolean on = d.get("on").getAsBoolean();
            server.execute(() -> {
                GoalManager.get().progressData().setBypass(id, on);
                if (on) toClose.remove(id);
                else toClose.add(id);
                KronwerkeCore.LOGGER.info("Stage bypass {} for {} (set in a side world)", on ? "on" : "off", id);
            });
        } catch (RuntimeException e) {
            KronwerkeCore.LOGGER.warn("A bypass message that does not read: {}", e.toString());
        }
    }

    // ---- side worlds ----

    /** Goes along with a player who moves. */
    public static void pack(ServerPlayer p, JsonObject data) {
        JsonArray a = new JsonArray();
        of(p).forEach(a::add);
        data.add("stages", a);
    }

    /** Side world: a player is on the way here with these stages. */
    public static void received(UUID id, JsonObject data) {
        if (Role.main() || !(data.get("stages") instanceof JsonArray a)) return;
        Set<String> s = new HashSet<>();
        for (JsonElement e : a) s.add(e.getAsString());
        arriving.put(id, s);
    }

    /** Side world: the player is in. They get what they came with, and what everybody has by now. */
    public static void onLogin(ServerPlayer p) {
        if (Role.main()) return;
        Set<String> came = arriving.remove(p.getUUID());
        if (came != null) {
            Set<String> want = new HashSet<>(came);
            want.addAll(earned);
            set(p, want, all());
        } else {
            set(p, union(of(p), earned), earned);
        }
    }

    /** Side world: main's state. Whoever is here gets the stages everybody has by now. */
    public static void onState(MinecraftServer server, JsonObject d) {
        if (Role.main() || server == null) return;
        if (d.get("bypass") instanceof JsonArray b) {
            Set<UUID> ids = new HashSet<>();
            for (JsonElement e : b) {
                try {
                    ids.add(UUID.fromString(e.getAsString()));
                } catch (RuntimeException ignored) {
                    // not an id
                }
            }
            bypass.retainAll(ids);
            bypass.addAll(ids);
        }
        if (!(d.get("stages") instanceof JsonArray a)) return;
        Set<String> s = new LinkedHashSet<>();
        for (JsonElement e : a) s.add(e.getAsString());
        boolean grew = !earned.containsAll(s);
        earned = Set.copyOf(s);
        if (grew) server.execute(() -> {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) set(p, union(of(p), earned), earned);
        });
    }

    /** Side world: whether the player has the bypass. */
    public static boolean hasBypass(UUID id) {
        return bypass.contains(id);
    }

    /** Side world: opens every stage for the player or closes those not everybody has, and tells main. */
    public static List<String> bypassHere(ServerPlayer p, boolean on) {
        List<String> changed = set(p, on ? all() : earned, all());
        if (on) bypass.add(p.getUUID());
        else bypass.remove(p.getUUID());
        JsonObject d = new JsonObject();
        d.addProperty("uuid", p.getUUID().toString());
        d.addProperty("on", on);
        NetworkSync.send("*", TOPIC, d, null);
        KronwerkeCore.LOGGER.info("Stage bypass {} for {} (side world)", on ? "on" : "off", p.getGameProfile().getName());
        return changed;
    }

    private static Set<String> union(Set<String> a, Set<String> b) {
        Set<String> out = new HashSet<>(a);
        out.addAll(b);
        return out;
    }
}
