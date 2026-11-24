package de.kronwerke.core.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;

import de.kronwerke.core.client.ServerListBrand;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.util.FormattedCharSequence;

/**
 * In the server list a Kronwerke server shows the logo's lettering where the entry's name and
 * the first line of its text stand. A server can only send text; the picture comes from the
 * mod, for every entry whose text begins with the name (see {@link ServerListBrand}).
 */
@Mixin(ServerSelectionList.OnlineServerEntry.class)
public abstract class ServerListBrandMixin {

    @Shadow
    @Final
    private ServerData serverData;

    /** The name the player gave the entry: the lettering takes its place. */
    @WrapOperation(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;IIIZ)I"))
    private int kronwerke$name(GuiGraphics g, Font font, String text, int x, int y, int colour, boolean shadow, Operation<Integer> original) {
        return ServerListBrand.is(serverData) ? 0 : original.call(g, font, text, x, y, colour, shadow);
    }

    /** The first line of the server's text, which is the name in letters; the second line stays. */
    @WrapOperation(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;IIIZ)I"))
    private int kronwerke$line(GuiGraphics g, Font font, FormattedCharSequence text, int x, int y, int colour, boolean shadow,
            Operation<Integer> original, @Local(argsOnly = true, ordinal = 1) int top) {
        if (y == top + 12 && ServerListBrand.is(serverData)) return 0;
        return original.call(g, font, text, x, y, colour, shadow);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void kronwerke$logo(GuiGraphics g, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovering, float partial, CallbackInfo ci) {
        if (ServerListBrand.is(serverData)) ServerListBrand.draw(g, serverData, left, top, width);
    }
}
