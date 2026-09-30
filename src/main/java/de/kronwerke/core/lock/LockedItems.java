package de.kronwerke.core.lock;

import com.gabinx.chapters.compat.ftb.EffectiveStages;
import com.gabinx.chapters.stage.LockResolver;
import com.gabinx.chapters.stage.PlayerStages;
import com.gabinx.chapters.stage.StageManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.bus.api.ICancellableEvent;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Items from a stage the player does not have yet, the SevTech way: they can be picked up
 * and carried, but not held, worn or used. One that ends up in a hand or an armour slot is
 * moved into the backpack part of the inventory, and only dropped when there is no room.
 * The player is told why in the action bar.
 *
 * Chapters itself refuses the pickup and drops every locked item from the inventory once a
 * second, so loot from a chest or a mob lay on the ground until it despawned. Its audit is
 * replaced by {@link #audit} (see ChaptersAuditMixin) and its pickup refusal is undone here.
 */
public final class LockedItems {
    private LockedItems() {}

    private static final boolean CHAPTERS = ModList.get().isLoaded("chapters");
    private static final Map<UUID, Long> lastMessage = new ConcurrentHashMap<>();

    public static boolean isLocked(ServerPlayer player, ItemStack stack) {
        return CHAPTERS && !stack.isEmpty() && LockResolver.isLocked(player, stack);
    }

    /** Hands and armour: nothing locked may stay there. */
    public static void audit(ServerPlayer player) {
        if (!CHAPTERS || player.isSpectator()) return;
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.armor.size(); i++) {
            move(player, inv.armor, i);
        }
        move(player, inv.offhand, 0);
        if (Inventory.isHotbarSlot(inv.selected)) {
            move(player, inv.items, inv.selected);
        }
    }

    private static void move(ServerPlayer player, NonNullList<ItemStack> list, int slot) {
        ItemStack stack = list.get(slot);
        if (!isLocked(player, stack)) return;
        list.set(slot, ItemStack.EMPTY);
        ItemStack rest = stow(player.getInventory(), stack);
        boolean dropped = !rest.isEmpty();
        if (dropped) {
            ItemEntity entity = player.drop(rest, false, true);
            if (entity != null) entity.setPickUpDelay(60);
        }
        tell(player, stack, dropped ? "dropped" : "stowed");
        player.inventoryMenu.broadcastChanges();
    }

    /** Puts the stack into the inventory, never into the selected hotbar slot. Answers what is left. */
    private static ItemStack stow(Inventory inv, ItemStack stack) {
        ItemStack rest = stack.copy();
        // fill matching stacks first, then empty slots; the backpack part before the hotbar
        for (int pass = 0; pass < 2 && !rest.isEmpty(); pass++) {
            for (int n = 0; n < 36 && !rest.isEmpty(); n++) {
                int i = (n + 9) % 36;
                if (i == inv.selected) continue;
                ItemStack there = inv.items.get(i);
                if (pass == 0 && !there.isEmpty() && ItemStack.isSameItemSameComponents(there, rest)) {
                    int room = Math.min(there.getMaxStackSize(), inv.getMaxStackSize()) - there.getCount();
                    int moved = Math.min(room, rest.getCount());
                    if (moved > 0) {
                        there.grow(moved);
                        rest.shrink(moved);
                    }
                } else if (pass == 1 && there.isEmpty()) {
                    inv.items.set(i, rest);
                    rest = ItemStack.EMPTY;
                }
            }
        }
        return rest;
    }

    /** Whether the stack fits somewhere that is not the selected hotbar slot. */
    private static boolean hasRoom(Inventory inv, ItemStack stack) {
        for (int i = 0; i < 36; i++) {
            if (i == inv.selected) continue;
            ItemStack there = inv.items.get(i);
            if (there.isEmpty()) return true;
            if (ItemStack.isSameItemSameComponents(there, stack)
                    && there.getCount() < Math.min(there.getMaxStackSize(), inv.getMaxStackSize())) return true;
        }
        return false;
    }

    /** Runs after Chapters: a locked item may be picked up after all, if it can go somewhere besides the hand. */
    public static void onPickup(ItemEntityPickupEvent.Pre event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        if (event.canPickup() != TriState.FALSE) return;
        ItemStack stack = event.getItemEntity().getItem();
        if (!isLocked(player, stack)) return;
        if (hasRoom(player.getInventory(), stack)) {
            event.setCanPickup(TriState.DEFAULT);
        } else {
            tell(player, stack, "full");
        }
    }

    /** A quarter of a second is the longest a locked item can sit in a hand. */
    public static void onTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && player.tickCount % 5 == 0) {
            audit(player);
        }
    }

    /** Using, placing or hitting with a locked item in hand. */
    public static void onInteract(PlayerInteractEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(event instanceof ICancellableEvent cancellable)) return;
        if (isLocked(player, player.getItemInHand(event.getHand()))) {
            cancellable.setCanceled(true);
            audit(player);
        }
    }

    private static void tell(ServerPlayer player, ItemStack stack, String what) {
        long now = System.currentTimeMillis();
        Long last = lastMessage.get(player.getUUID());
        if (last != null && now - last < 2000) return;
        lastMessage.put(player.getUUID(), now);

        // the item keeps its name secret until its stage opens, like in the tooltip
        Component item = Component.literal("???").withStyle(ChatFormatting.GOLD);
        Component stage = stageName(lockingStage(player, stack)).copy().withStyle(ChatFormatting.YELLOW);
        String fallback = switch (what) {
            case "dropped" -> "Das gehört zu %2$s. Kein Platz im Inventar, es liegt vor dir.";
            case "full" -> "Das gehört zu %2$s. Mach Platz im Inventar, dann kannst du es aufheben.";
            default -> "Das gehört zu %2$s. Du kannst es einstecken, aber noch nicht benutzen.";
        };
        player.displayClientMessage(Component.translatableWithFallback("kronwerke.locked." + what, fallback, item, stage)
                .withStyle(ChatFormatting.GRAY), true);
    }

    /** The first stage that unlocks the item and the player does not have. */
    private static ResourceLocation lockingStage(ServerPlayer player, ItemStack stack) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        Set<ResourceLocation> stages = StageManager.get().itemStagesIndexView().get(id);
        if (stages == null) return null;
        PlayerStages has = EffectiveStages.snapshot(player);
        return stages.stream().filter(s -> !has.has(s)).sorted().findFirst().orElse(null);
    }

    public static Component stageName(ResourceLocation stage) {
        if (stage == null) return Component.translatableWithFallback("kronwerke.stage.unknown", "einer späteren Stufe");
        return Component.translatableWithFallback("kronwerke.stage." + stage.getNamespace() + "." + stage.getPath(),
                FALLBACK_NAMES.getOrDefault(stage.toString(), stage.toString()));
    }

    private static final Map<String, String> FALLBACK_NAMES = Map.of(
            "kronwerke:stage2", "Stufe 2 (Messingwerk)",
            "kronwerke:stage3", "Stufe 3 (Stahlwerk)",
            "kronwerke:stage4", "Stufe 4 (Sternwerk)",
            "kronwerke:stage5", "Stufe 5 (Chaoswerk)");
}
