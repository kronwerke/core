package de.kronwerke.core.obelisk;

import de.kronwerke.core.KronwerkeCore;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The obelisk's own particles: a rune glyph that rises and fades. */
public final class KwParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(Registries.PARTICLE_TYPE, KronwerkeCore.MOD_ID);
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> RUNE = PARTICLES.register("rune", () -> new SimpleParticleType(false));

    private KwParticles() {
    }

    public static void register(IEventBus modBus) {
        PARTICLES.register(modBus);
    }
}
