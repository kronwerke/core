package de.kronwerke.core.lock;

import de.kronwerke.core.KronwerkeCore;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.util.TriState;

import java.lang.reflect.Method;
import java.util.Map;

/**
 * Locked items in accessory slots: a ring, charm, belt or backpack worn in a Curios or an
 * Accessories slot kept its full effect, because the lock only looked at hands and armour.
 * Once a second such an item goes back into the inventory like one in a hand; Curios is also
 * asked not to let it in at all. Accessories comes inside the Aether and is reached by
 * reflection.
 */
public final class LockedAccessories {
    private static final boolean CURIOS = ModList.get().isLoaded("curios");
    private static final boolean ACCESSORIES = ModList.get().isLoaded("accessories");
    private static Method accGet, accContainers, accItems;
    private static boolean accBroken;

    private LockedAccessories() {
    }

    public static void audit(ServerPlayer player) {
        if (CURIOS) Curios.audit(player);
        if (ACCESSORIES && !accBroken) accessories(player);
    }

    /** Curios' own question before an item goes into a slot. */
    public static void register() {
        if (CURIOS) Curios.register();
    }

    private static void accessories(ServerPlayer player) {
        try {
            if (accGet == null) {
                Class<?> cap = Class.forName("io.wispforest.accessories.api.AccessoriesCapability");
                accGet = cap.getMethod("get", LivingEntity.class);
                accContainers = cap.getMethod("getContainers");
                accItems = Class.forName("io.wispforest.accessories.api.AccessoriesContainer").getMethod("getAccessories");
            }
            Object c = accGet.invoke(null, player);
            if (c == null) return;
            for (Object container : ((Map<?, ?>) accContainers.invoke(c)).values()) {
                if (accItems.invoke(container) instanceof Container items) {
                    for (int i = 0; i < items.getContainerSize(); i++) {
                        ItemStack s = items.getItem(i);
                        if (!LockedItems.isLocked(player, s)) continue;
                        items.setItem(i, ItemStack.EMPTY);
                        items.setChanged();
                        LockedItems.putBack(player, s);
                    }
                }
            }
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            accBroken = true;
            KronwerkeCore.LOGGER.warn("Locked items in Accessories slots are not checked: {}", e.toString());
        }
    }

    /** Only loaded with Curios there. */
    private static final class Curios {
        static void audit(ServerPlayer player) {
            top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(player).ifPresent(h -> {
                for (var handler : h.getCurios().values()) {
                    var stacks = handler.getStacks();
                    for (int i = 0; i < stacks.getSlots(); i++) {
                        ItemStack s = stacks.getStackInSlot(i);
                        if (!LockedItems.isLocked(player, s)) continue;
                        stacks.setStackInSlot(i, ItemStack.EMPTY);
                        LockedItems.putBack(player, s);
                    }
                }
            });
        }

        static void register() {
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener((top.theillusivec4.curios.api.event.CurioCanEquipEvent e) -> {
                if (e.getEntity() instanceof ServerPlayer p && LockedItems.isLocked(p, e.getStack())) e.setEquipResult(TriState.FALSE);
            });
        }
    }
}
