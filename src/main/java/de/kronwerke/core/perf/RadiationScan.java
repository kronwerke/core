package de.kronwerke.core.perf;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.event.TagsUpdatedEvent;

/**
 * Nuclear Radiation looks at every block of every chunk that loads, to find radioactive
 * blocks and fluids. Each look builds a copy of its tag table, so a chunk costs millions
 * of map entries; with players flying around that was a fifth of the server tick. Almost
 * no chunk holds anything radioactive, and a section's palette already says which block
 * states it can hold. A section whose palette has no radioactive state is skipped, the
 * others are scanned exactly as before. The answer per block state is kept until tags or
 * data packs reload.
 */
public final class RadiationScan {

    private static final Map<BlockState, Boolean> RELEVANT = new ConcurrentHashMap<>();
    private static volatile boolean broken;
    private static MethodHandle ofBlock;
    private static MethodHandle ofFluid;
    private static MethodHandle isEmpty;

    private RadiationScan() {
    }

    /** What the scan sees: true skips the section, like a section of only air. */
    public static boolean skip(LevelChunkSection section) {
        if (section.hasOnlyAir()) {
            return true;
        }
        if (broken || !ready()) {
            return false;
        }
        return !section.maybeHas(RadiationScan::relevant);
    }

    public static void onTags(TagsUpdatedEvent event) {
        RELEVANT.clear();
    }

    static boolean relevant(BlockState state) {
        Boolean known = RELEVANT.get(state);
        if (known != null) {
            return known;
        }
        boolean answer;
        try {
            answer = !(boolean) isEmpty.invoke(ofBlock.invoke(state));
            FluidState fluid = state.getFluidState();
            if (!answer && !fluid.isEmpty()) {
                answer = !(boolean) isEmpty.invoke(ofFluid.invoke(fluid));
            }
        } catch (Throwable t) {
            broken = true;
            return true;
        }
        RELEVANT.put(state, answer);
        return answer;
    }

    private static boolean ready() {
        if (ofBlock != null) {
            return true;
        }
        try {
            Class<?> bindings = Class.forName("igentuman.nr.api.binding.RadiationBindings");
            Class<?> profile = Class.forName("igentuman.nr.api.RadiationProfile");
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            ofFluid = lookup.findStatic(bindings, "of", MethodType.methodType(profile, FluidState.class));
            isEmpty = lookup.findVirtual(profile, "isEmpty", MethodType.methodType(boolean.class));
            ofBlock = lookup.findStatic(bindings, "of", MethodType.methodType(profile, BlockState.class));
            return true;
        } catch (ReflectiveOperationException | LinkageError e) {
            broken = true;
            return false;
        }
    }
}
