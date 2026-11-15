package de.kronwerke.core.goal;

import de.kronwerke.core.KronwerkeCore;
import de.kronwerke.core.link.Role;
import de.kronwerke.core.single.Singleplayer;
import dev.ftb.mods.ftbteams.api.event.TeamEvent;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * A player who founds, joins or leaves a party keeps their stages. Chapters keeps the stages
 * in the FTB team, and a new party is a new team with none: whoever followed the quest book's
 * first chapter and founded a team stood at stage 1 again, with everything of the later stages
 * taken out of their hands, until the next login. In a singleplayer world that was the end of
 * the pack being open (nothing there gives a stage back but the login), and on the server it
 * would have hit every player who founds a party after stage 2.
 * <p>
 * Now the team the player is in after the change gets what the player is due: everything in a
 * singleplayer world, what the goals and the bypass say on main, and what main last said in a
 * side world. Nothing is taken from a team here.
 */
public final class TeamStages {
    private TeamStages() {
    }

    /** Only called when FTB Teams and Chapters are there. */
    public static void register() {
        TeamEvent.PLAYER_CHANGED.register(event -> {
            ServerPlayer p = event.getPlayer();
            if (p != null) refresh(p);
        });
    }

    public static void refresh(ServerPlayer p) {
        if (p.getServer() == null) return;
        try {
            if (Singleplayer.ON) {
                List<String> changed = StageLink.set(p, StageLink.all(), StageLink.all());
                if (!changed.isEmpty()) KronwerkeCore.LOGGER.info("Singleplayer: {} changed team, stages open again: {}", p.getGameProfile().getName(), changed);
            } else if (Role.main()) {
                GoalManager gm = GoalManager.get();
                StageLink.reconcile(p, gm.goalStages(), gm.earnedStages(), gm.progressData().hasBypass(p.getUUID()));
            } else {
                StageLink.teamChanged(p);
            }
        } catch (RuntimeException | LinkageError e) {
            KronwerkeCore.LOGGER.warn("Could not set the stages of {} right after a team change: {}", p.getGameProfile().getName(), e.toString());
        }
    }
}
