package de.kronwerke.core.mixin;

import java.util.IdentityHashMap;
import java.util.Map;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Apothic Enchanting asks, for every animal and every player near it, every tick, whether the
 * player's item has an enchantment effect (TemptGoal). Each answer walks all enchantments of
 * the item and fires NeoForge's enchantment level event, where Apotheosis looks up gems and
 * affixes: 1.6 % of the tick with 50 players. The answer for one item stack and one effect is
 * kept for the rest of the server tick, on the server thread only.
 */
@Mixin(EnchantmentHelper.class)
public abstract class EnchantHasCacheMixin {

    @Unique
    private static final Map<ItemStack, Map<DataComponentType<?>, Boolean>> kronwerke$hasCache = new IdentityHashMap<>();
    @Unique
    private static int kronwerke$hasTick = -1;

    @Unique
    private static Map<DataComponentType<?>, Boolean> kronwerke$slot(ItemStack stack, boolean create) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || !server.isSameThread() || stack.isEmpty()) {
            return null;
        }
        if (kronwerke$hasTick != server.getTickCount()) {
            kronwerke$hasCache.clear();
            kronwerke$hasTick = server.getTickCount();
        }
        return create ? kronwerke$hasCache.computeIfAbsent(stack, s -> new IdentityHashMap<>(2)) : kronwerke$hasCache.get(stack);
    }

    @Inject(method = "has", at = @At("HEAD"), cancellable = true)
    private static void kronwerke$cached(ItemStack stack, DataComponentType<?> type, CallbackInfoReturnable<Boolean> cir) {
        Map<DataComponentType<?>, Boolean> slot = kronwerke$slot(stack, false);
        if (slot != null) {
            Boolean known = slot.get(type);
            if (known != null) {
                cir.setReturnValue(known);
            }
        }
    }

    @Inject(method = "has", at = @At("RETURN"))
    private static void kronwerke$remember(ItemStack stack, DataComponentType<?> type, CallbackInfoReturnable<Boolean> cir) {
        Map<DataComponentType<?>, Boolean> slot = kronwerke$slot(stack, true);
        if (slot != null) {
            slot.put(type, cir.getReturnValue());
        }
    }
}
