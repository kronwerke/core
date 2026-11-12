package de.kronwerke.core.mixin;

import de.kronwerke.core.client.LoadPump;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Pictures during JEI's start; see {@link LoadPump}. */
@Pseudo
@Mixin(targets = "mezz.jei.library.load.PluginCallerTimer", remap = false)
public abstract class JeiPumpStageMixin {
    @Inject(method = "begin", at = @At("HEAD"), require = 0)
    private void kronwerke$stage(String title, ResourceLocation plugin, CallbackInfo ci) {
        LoadPump.stage(title, String.valueOf(plugin));
    }
}
