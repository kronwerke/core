package de.kronwerke.core.share.ftb;

import com.google.gson.JsonObject;
import de.kronwerke.core.link.Role;
import de.kronwerke.core.share.Layer;
import de.kronwerke.core.share.Share;
import dev.ftb.mods.ftblibrary.snbt.SNBTCompoundTag;
import dev.ftb.mods.ftbquests.net.SyncTeamDataMessage;
import dev.ftb.mods.ftbquests.quest.ServerQuestFile;
import dev.ftb.mods.ftbquests.quest.TeamData;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import net.minecraft.nbt.TagParser;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * FTB Quests between servers: the quest book is one book. A player's team progress goes with them
 * to the other server, what they do there (tasks, completed quests, claimed rewards) comes back
 * into their team on main, and every few seconds both sides exchange it while they are away, so
 * team mates on main see it too. Progress only ever grows: each side takes the larger progress,
 * the earlier completion and every claimed reward of the other, so a reward claimed on one side
 * cannot be claimed again on the other.
 */
public final class QuestLayer implements Layer {
    private static final int EVERY = 200;

    /** Not main: progress that came with a player, applied once they are in. */
    private final Map<UUID, String> waiting = new HashMap<>();
    private int ticks;
    private long merges;

    public static QuestLayer create() {
        return new QuestLayer();
    }

    @Override
    public String name() {
        return "ftbq";
    }

    private static ServerQuestFile file() {
        return ServerQuestFile.INSTANCE;
    }

    /** main: the team data of a player, online or not. */
    private static TeamData teamOf(UUID player) {
        if (file() == null || !FTBTeamsAPI.api().isManagerLoaded()) return null;
        Optional<Team> t = FTBTeamsAPI.api().getManager().getTeamForPlayerID(player);
        return t.map(team -> file().getOrCreateTeamData(team)).orElse(null);
    }

    private static TeamData teamOf(ServerPlayer p) {
        if (file() == null || !FTBTeamsAPI.api().isManagerLoaded()) return null;
        return FTBTeamsAPI.api().getManager().getTeamForPlayer(p).map(team -> file().getOrCreateTeamData(team)).orElse(null);
    }

    /** Takes the other side's progress into this team: the larger of each, every reward claimed on either. */
    private void merge(TeamData into, String snbt) {
        TeamData other = new TeamData(into.getTeamId(), file());
        try {
            other.deserializeNBT(SNBTCompoundTag.of(TagParser.parseTag(snbt)));
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            throw new IllegalArgumentException(e);
        }
        into.mergeData(other);
        into.mergeClaimedRewards(other);
        into.clearCachedProgress();
        into.markDirty();
        SyncTeamDataMessage sync = new SyncTeamDataMessage(into);
        // through Architectury, which FTB's packets are registered with; NeoForge's own sender cannot encode them
        for (ServerPlayer p : into.getOnlineMembers()) dev.architectury.networking.NetworkManager.sendToPlayer(p, sync);
        merges++;
    }

    // ---- each tick ----

    @Override
    public void tick(MinecraftServer server) {
        ticks++;
        if (!waiting.isEmpty()) {
            for (UUID id : waiting.keySet().toArray(new UUID[0])) {
                ServerPlayer p = server.getPlayerList().getPlayer(id);
                if (p == null) continue;
                TeamData t = teamOf(p);
                if (t == null) continue;
                merge(t, waiting.remove(id));
            }
        }
        if (Role.main() || ticks % EVERY != 0) return;
        // a side world: what its players did goes to main, main answers with the team's whole progress
        String main = mainPeer();
        if (main == null) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            TeamData t = teamOf(p);
            if (t == null) continue;
            JsonObject m = new JsonObject();
            m.addProperty("t", "merge");
            m.addProperty("uuid", p.getUUID().toString());
            m.addProperty("snbt", t.serializeNBT().toString());
            Share.tell(this, main, m);
        }
    }

    private static String mainPeer() {
        for (String p : Share.peers()) {
            var q = de.kronwerke.core.link.NetworkSync.peer(p);
            if (q != null && q.role().equals("main")) return p;
        }
        return null;
    }

    @Override
    public void onState(String from, JsonObject state) {
    }

    @Override
    public void onTell(MinecraftServer server, String from, JsonObject d) {
        UUID id = UUID.fromString(d.get("uuid").getAsString());
        String snbt = d.get("snbt").getAsString();
        switch (d.get("t").getAsString()) {
            case "merge" -> {
                if (!Role.main()) return;
                TeamData t = teamOf(id);
                if (t == null) return;
                merge(t, snbt);
                JsonObject back = new JsonObject();
                back.addProperty("t", "team");
                back.addProperty("uuid", id.toString());
                back.addProperty("snbt", t.serializeNBT().toString());
                Share.tell(this, from, back);
            }
            case "team" -> {
                ServerPlayer p = server.getPlayerList().getPlayer(id);
                TeamData t = p == null ? null : teamOf(p);
                if (t != null) merge(t, snbt);
            }
            default -> {
            }
        }
    }

    // ---- with the player ----

    @Override
    public JsonObject pack(ServerPlayer p) {
        TeamData t = teamOf(p);
        if (t == null) return null;
        JsonObject o = new JsonObject();
        o.addProperty("snbt", t.serializeNBT().toString());
        return o;
    }

    @Override
    public void unpack(MinecraftServer server, UUID player, JsonObject data) {
        String snbt = data.get("snbt").getAsString();
        TeamData t = Role.main() ? teamOf(player) : null;
        if (t != null) {
            merge(t, snbt);
        } else {
            // a side world makes the player's team when they log in; their progress waits for that
            waiting.put(player, snbt);
        }
    }

    // ---- nothing moves through gives ----

    @Override
    public JsonObject receive(MinecraftServer server, String from, JsonObject data) {
        return null;
    }

    @Override
    public JsonObject refund(MinecraftServer server, JsonObject data) {
        return null;
    }

    @Override
    public String describe() {
        return merges + " merges since start" + (waiting.isEmpty() ? "" : ", " + waiting.size() + " waiting for their player");
    }
}
