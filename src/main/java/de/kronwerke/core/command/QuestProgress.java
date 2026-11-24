package de.kronwerke.core.command;

import java.util.Optional;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import dev.ftb.mods.ftbquests.quest.ServerQuestFile;
import dev.ftb.mods.ftbquests.quest.TeamData;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;

/**
 * The quest progress of a player's team, for {@code kw admin quests <uuid> json}. Only loaded
 * when FTB Quests and FTB Teams are there; {@link BotCommand} asks first.
 */
final class QuestProgress {

    private QuestProgress() {
    }

    private static final Gson GSON = new Gson();

    /** The answer line: OK and the JSON, or ERR and why. */
    static String json(UUID player) {
        ServerQuestFile file = ServerQuestFile.INSTANCE;
        if (file == null || !FTBTeamsAPI.api().isManagerLoaded()) return "ERR quests not loaded";
        Optional<Team> team = FTBTeamsAPI.api().getManager().getTeamForPlayerID(player);
        if (team.isEmpty()) return "ERR unknown player";
        TeamData data = file.getOrCreateTeamData(team.get());
        JsonArray done = new JsonArray(), started = new JsonArray();
        file.forAllQuests(q -> {
            if (data.isCompleted(q)) done.add(q.getCodeString());
            else if (data.isStarted(q)) started.add(q.getCodeString());
        });
        JsonObject out = new JsonObject();
        out.addProperty("team", team.get().getShortName());
        out.addProperty("members", team.get().getMembers().size());
        out.add("done", done);
        out.add("started", started);
        return "OK " + GSON.toJson(out);
    }
}
