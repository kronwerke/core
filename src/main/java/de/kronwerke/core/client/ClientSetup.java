package de.kronwerke.core.client;

import de.kronwerke.core.config.KronwerkeClientConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Client only: the client config, the renderers, the obelisk's effects on the picture and the language question after the first join. */
public final class ClientSetup {
    private ClientSetup() {
    }

    public static void register(ModContainer container, net.neoforged.bus.api.IEventBus modBus) {
        container.registerConfig(ModConfig.Type.CLIENT, KronwerkeClientConfig.SPEC);
        modBus.addListener((net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers e) -> {
            e.registerBlockEntityRenderer(de.kronwerke.core.obelisk.ObeliskBlocks.OBELISK_TOP_ENTITY.get(), ObeliskTopRenderer::new);
            e.registerBlockEntityRenderer(de.kronwerke.core.obelisk.ObeliskBlocks.OBELISK_PEDESTAL_ENTITY.get(), PedestalRenderer::new);
            e.registerBlockEntityRenderer(de.kronwerke.core.obelisk.ObeliskBlocks.OBELISK_BOARD_ENTITY.get(), BoardRenderer::new);
        });
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingIn e) -> LanguageScreen.showIfNeeded());
        ObeliskEffects.register(modBus);
        modBus.addListener((net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent e) ->
                e.registerSpriteSet(de.kronwerke.core.obelisk.KwParticles.RUNE.get(), RuneParticle.Provider::new));
        modBus.addListener((net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent e) ->
                e.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener) m -> ObeliskEffects.onReload()));
    }
}
