package de.kronwerke.core.lock;

import de.kronwerke.core.compat.JeiBatch;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Client only, with Chapters: the veiled locked items, their tooltip, and JEI's list. */
public final class ClientHooks {
    private ClientHooks() {}

    public static void register(IEventBus modBus) {
        NeoForge.EVENT_BUS.addListener(LockedTooltip::onTooltip);
        modBus.addListener(LockedDecorator::register);
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> ClientLocks.tick());
        if (ModList.get().isLoaded("jei")) ClientLocks.onChange(JeiBatch::reconcile);
    }
}
