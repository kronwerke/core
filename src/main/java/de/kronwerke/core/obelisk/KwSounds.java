package de.kronwerke.core.obelisk;

import de.kronwerke.core.KronwerkeCore;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The obelisk's own voice, synthesized by tools/sounds/synth.py. */
public final class KwSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, KronwerkeCore.MOD_ID);
    public static final DeferredHolder<SoundEvent, SoundEvent> HUM = sound("obelisk.hum");
    public static final DeferredHolder<SoundEvent, SoundEvent> RISER = sound("obelisk.riser");
    public static final DeferredHolder<SoundEvent, SoundEvent> TEAR = sound("obelisk.tear");
    public static final DeferredHolder<SoundEvent, SoundEvent> FANFARE = sound("obelisk.fanfare");
    public static final DeferredHolder<SoundEvent, SoundEvent> WHISPER = sound("obelisk.whisper");

    private KwSounds() {
    }

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(KronwerkeCore.MOD_ID, name)));
    }

    public static void register(IEventBus modBus) {
        SOUNDS.register(modBus);
    }
}
