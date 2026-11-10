package de.kronwerke.core.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import de.kronwerke.core.perf.SpreadAutosave;
import net.minecraft.server.MinecraftServer;

/** Marks the periodic autosave so its chunk part can be spread out, see {@link SpreadAutosave}. */
@Mixin(MinecraftServer.class)
public abstract class AutosaveMarkMixin {

    @WrapOperation(method = "tickServer", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;saveEverything(ZZZ)Z"))
    private boolean kronwerke$markAutosave(MinecraftServer server, boolean suppressLog, boolean flush, boolean forced, Operation<Boolean> original) {
        SpreadAutosave.autosaving = true;
        SpreadAutosave.begin();
        try {
            return original.call(server, suppressLog, flush, forced);
        } finally {
            SpreadAutosave.autosaving = false;
            SpreadAutosave.afterSave();
        }
    }
}
