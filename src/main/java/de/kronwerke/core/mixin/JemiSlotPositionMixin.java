package de.kronwerke.core.mixin;

import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.placement.HorizontalAlignment;
import mezz.jei.api.gui.placement.VerticalAlignment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/**
 * EMI shows the recipes of mods that only have a JEI plugin through a layer that stands in for
 * JEI's classes. Its slot builder (EMI 1.1.24) lacks one method of JEI's interface, the one
 * that places a slot inside an area by alignment. A JEI plugin that calls it throws an
 * AbstractMethodError and EMI drops the recipe: every recipe of Chipped's workbenches, more
 * than 170 errors in the log at each start (seen 15.11.2026 with the whole pack). This is the
 * missing method, worked out as JEI does it. Should EMI bring its own one day, that one wins
 * (a unique method gives way to one that is already there). Nothing without EMI.
 */
@Pseudo
@Mixin(targets = "dev.emi.emi.jemi.impl.JemiRecipeSlotBuilder", remap = false)
public abstract class JemiSlotPositionMixin {

    @Shadow
    public abstract IRecipeSlotBuilder setPosition(int xPos, int yPos);

    @Shadow
    public abstract int getWidth();

    @Shadow
    public abstract int getHeight();

    @Unique
    public IRecipeSlotBuilder setPosition(int areaX, int areaY, int areaWidth, int areaHeight,
                                          HorizontalAlignment horizontal, VerticalAlignment vertical) {
        return setPosition(areaX + horizontal.getXPos(areaWidth, getWidth()), areaY + vertical.getYPos(areaHeight, getHeight()));
    }
}
