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
        // the rank icons' resource pack, always on (see RanksPack)
        modBus.addListener(RanksPack::onFind);
        TravelOverlay.register();
        JoinOverlay.register();
        de.kronwerke.core.probe.Probe.register();
        // why a machine, altar or ritual did not craft: over its window or over the block
        LockHints.register();
        // the pack's key layout for instances that never got Default Options' keybindings.txt
        DefaultKeys.register();
        KeyDump.register();
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> ObeliskEffects.forget());
        // fills the creative tabs bit by bit after joining instead of all at once when the menu opens
        // not on a move between the network's servers: the tabs are the same there, and filling
        // them again stood the game still for a second or two right at the arrival
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingIn e) -> {
            if (!TravelOverlay.moving()) CreativePrewarm.reset();
        });
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.ClientTickEvent.Post e) -> {
            LanguageScreen.clientTick();
            de.kronwerke.core.camera.client.CameraShots.tick();
            CreativePrewarm.tick();
        });
        // the overworld builds up to 608, so its clouds move up from vanilla's 192
        modBus.addListener((net.neoforged.neoforge.client.event.RegisterDimensionSpecialEffectsEvent e) ->
                e.register(net.minecraft.world.level.dimension.BuiltinDimensionTypes.OVERWORLD_EFFECTS, new HighCloudEffects()));
        modBus.addListener((net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent e) ->
                e.registerSpriteSet(de.kronwerke.core.obelisk.KwParticles.RUNE.get(), RuneParticle.Provider::new));
        modBus.addListener((net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent e) ->
                e.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener) m -> ObeliskEffects.onReload()));
    }
}
