package de.kronwerke.core.client;

import de.kronwerke.core.config.KronwerkeClientConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Client only: the client config and the language question after the first join. */
public final class ClientSetup {
    private ClientSetup() {
    }

    public static void register(ModContainer container, net.neoforged.bus.api.IEventBus modBus) {
        container.registerConfig(ModConfig.Type.CLIENT, KronwerkeClientConfig.SPEC);
        modBus.addListener((net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers e) ->
                e.registerBlockEntityRenderer(de.kronwerke.core.obelisk.ObeliskBlocks.OBELISK_TOP_ENTITY.get(), ObeliskBeamRenderer::new));
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingIn e) -> LanguageScreen.showIfNeeded());
    }
}
