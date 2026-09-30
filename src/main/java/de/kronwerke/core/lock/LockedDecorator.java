package de.kronwerke.core.lock;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.IItemDecorator;
import net.neoforged.neoforge.client.event.RegisterItemDecorationsEvent;

/**
 * Client only: an item the player has not unlocked is drawn veiled, dark with a brass
 * question mark, wherever items are drawn (inventories, JEI, chests). Like the unknown
 * items of SevTech: you can see that something is there, not what it is.
 */
public final class LockedDecorator implements IItemDecorator {
    private static final LockedDecorator INSTANCE = new LockedDecorator();

    private LockedDecorator() {}

    public static void register(RegisterItemDecorationsEvent event) {
        for (Item item : BuiltInRegistries.ITEM) event.register(item, INSTANCE);
    }

    @Override
    public boolean render(GuiGraphics g, Font font, ItemStack stack, int x, int y) {
        if (stack.isEmpty() || !ClientLocks.isLocked(stack.getItem())) return false;
        g.pose().pushPose();
        g.pose().translate(0, 0, 250);
        g.fill(RenderType.guiOverlay(), x, y, x + 16, y + 16, 0xC80E0C11);
        g.drawString(font, "?", x + 5, y + 4, 0xFFD6AD5F, true);
        g.pose().popPose();
        return true;
    }
}
