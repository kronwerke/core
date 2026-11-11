package de.kronwerke.core.mixin;

import dev.ftb.mods.ftbquests.client.ClientQuestFile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * At every login the server sends the whole quest book, and the client took the old copy
 * apart first, quest by quest, each one searching all the others for links and dependencies:
 * with 3 500 quests that is millions of steps while the game stands still, for an object that
 * is thrown away right after. The old copy is only marked as no longer valid.
 */
@Pseudo
@Mixin(targets = "dev.ftb.mods.ftbquests.client.ClientQuestFile", remap = false)
public abstract class QuestFileDropMixin {
    @Redirect(method = "syncFromServer", require = 0, at = @At(value = "INVOKE", target = "Ldev/ftb/mods/ftbquests/client/ClientQuestFile;deleteSelf()V"))
    private static void kronwerke$drop(ClientQuestFile old) {
        old.invalidate();
    }
}
