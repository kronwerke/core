package de.kronwerke.core.mixin;

import java.util.stream.Stream;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;

/** For the creative menu warm-up, see client/CreativePrewarm. */
@Mixin(CreativeModeTabs.class)
public interface CreativeTabsAccessor {

    @Accessor("CACHED_PARAMETERS")
    static void kronwerke$setCached(CreativeModeTab.ItemDisplayParameters params) {
        throw new AssertionError();
    }

    @Accessor("CACHED_PARAMETERS")
    static CreativeModeTab.ItemDisplayParameters kronwerke$cached() {
        throw new AssertionError();
    }

    @Invoker("streamAllTabs")
    static Stream<CreativeModeTab> kronwerke$allTabs() {
        throw new AssertionError();
    }
}
