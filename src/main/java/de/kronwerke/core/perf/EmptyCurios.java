package de.kronwerke.core.perf;

import java.util.Optional;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

/**
 * Artifacts gives zombies, skeletons, piglins and other mobs curio slots, so that a few of
 * them spawn wearing an artifact. Because of that, Curios ticks every such mob's slots every
 * tick, and every mod that asks "does this entity wear X" searches them. Almost all of these
 * mobs wear nothing. Whether a mob's curio slots are empty is looked at once when it is first
 * seen and again every five seconds; while they are empty, the Curios tick, the searches and
 * Artifacts' own per tick update are skipped for that mob. A mob that wears an artifact runs
 * as before, and so do players. Mobs cannot put things into curio slots themselves; only
 * spawning (Artifacts) or commands do, and the five second recheck covers commands.
 */
public final class EmptyCurios {

    private static final int RECHECK_TICKS = 100;

    private EmptyCurios() {
    }

    /** True for a mob whose curio slots are known to be empty. Never true for players. */
    public static boolean emptyMob(LivingEntity entity) {
        if (entity == null || entity instanceof Player || entity.level().isClientSide()) {
            return false;
        }
        CurioState state = (CurioState) entity;
        int now = entity.tickCount;
        int at = state.kronwerke$curioCheckedAt();
        if (at >= 0 && now - at < RECHECK_TICKS && now >= at) {
            return state.kronwerke$curioEmpty();
        }
        boolean empty = look(entity);
        state.kronwerke$setCurioState(now, empty);
        return empty;
    }

    private static boolean look(LivingEntity entity) {
        Optional<ICuriosItemHandler> inventory = CuriosApi.getCuriosInventory(entity);
        if (inventory.isEmpty()) {
            return true;
        }
        for (ICurioStacksHandler handler : inventory.get().getCurios().values()) {
            if (!empty(handler.getStacks()) || !empty(handler.getCosmeticStacks())) {
                return false;
            }
        }
        return true;
    }

    private static boolean empty(IDynamicStackHandler stacks) {
        for (int i = 0; i < stacks.getSlots(); i++) {
            if (!stacks.getStackInSlot(i).isEmpty()) {
                return false;
            }
        }
        return true;
    }
}
