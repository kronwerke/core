package de.kronwerke.core.steps;

import java.lang.reflect.Method;
import java.util.Collection;

import de.kronwerke.core.link.Role;
import de.kronwerke.core.obelisk.Obelisk;
import de.kronwerke.core.obelisk.ObeliskData;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * The first steps in the quest book are done when the player really did them, not when a
 * box is ticked: Core gives a hidden advancement (kronwerke:steps/...) for each, and the
 * quests check for it. claimed: the player's team claimed a chunk. team: the player is in a
 * party team. obelisk: the player stood at the obelisk. deposited: the first deposit. The
 * advancements show nowhere else. (The mining world quest checks an arrival of its own.)
 */
public final class FirstSteps {

    private FirstSteps() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener((PlayerTickEvent.Post e) -> {
            if (e.getEntity() instanceof ServerPlayer p && p.tickCount % 40 == 7) check(p);
        });
    }

    /** A deposit at the obelisk. */
    public static void deposited(ServerPlayer p) {
        award(p, "deposited");
    }

    private static void check(ServerPlayer p) {
        if (!has(p, "obelisk") && Role.main()) {
            ObeliskData d = Obelisk.get().data();
            if (d.isSet() && p.level().dimension().location().toString().equals(d.dimension())) {
                BlockPos core = d.pos();
                if (p.blockPosition().distSqr(core) <= 16 * 16) award(p, "obelisk");
            }
        }
        if (!has(p, "team") && inParty(p)) award(p, "team");
        if (!has(p, "claimed") && claimed(p)) award(p, "claimed");
    }

    private static boolean has(ServerPlayer p, String step) {
        AdvancementHolder a = p.server.getAdvancements().get(id(step));
        return a == null || p.getAdvancements().getOrStartProgress(a).isDone();
    }

    private static void award(ServerPlayer p, String step) {
        AdvancementHolder a = p.server.getAdvancements().get(id(step));
        if (a != null && !p.getAdvancements().getOrStartProgress(a).isDone()) p.getAdvancements().award(a, "done");
    }

    private static ResourceLocation id(String step) {
        return ResourceLocation.fromNamespaceAndPath("kronwerke", "steps/" + step);
    }

    // ---- FTB Teams and FTB Chunks, through reflection so Core runs without them ----

    private static Method teamsApi, teamsManager, teamFor, isParty;
    private static Method chunksApi, chunksManager, dataFor, claims;
    private static boolean looked, broken;

    private static void look() {
        if (looked) return;
        looked = true;
        try {
            teamsApi = Class.forName("dev.ftb.mods.ftbteams.api.FTBTeamsAPI").getMethod("api");
            teamsManager = Class.forName("dev.ftb.mods.ftbteams.api.FTBTeamsAPI$API").getMethod("getManager");
            teamFor = Class.forName("dev.ftb.mods.ftbteams.api.TeamManager").getMethod("getTeamForPlayer", ServerPlayer.class);
            isParty = Class.forName("dev.ftb.mods.ftbteams.api.Team").getMethod("isPartyTeam");
            chunksApi = Class.forName("dev.ftb.mods.ftbchunks.api.FTBChunksAPI").getMethod("api");
            chunksManager = Class.forName("dev.ftb.mods.ftbchunks.api.FTBChunksAPI$API").getMethod("getManager");
            dataFor = Class.forName("dev.ftb.mods.ftbchunks.api.ClaimedChunkManager").getMethod("getOrCreateData", ServerPlayer.class);
            claims = Class.forName("dev.ftb.mods.ftbchunks.api.ChunkTeamData").getMethod("getClaimedChunks");
        } catch (ReflectiveOperationException | LinkageError e) {
            broken = true;
        }
    }

    private static boolean inParty(ServerPlayer p) {
        look();
        if (broken) return false;
        try {
            Object team = ((java.util.Optional<?>) teamFor.invoke(teamsManager.invoke(teamsApi.invoke(null)), p)).orElse(null);
            return team != null && (Boolean) isParty.invoke(team);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    private static boolean claimed(ServerPlayer p) {
        look();
        if (broken) return false;
        try {
            Object data = dataFor.invoke(chunksManager.invoke(chunksApi.invoke(null)), p);
            return data != null && !((Collection<?>) claims.invoke(data)).isEmpty();
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }
}
