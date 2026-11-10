package de.kronwerke.core.client;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.FilePackResources;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.neoforge.event.AddPackFindersEvent;

/**
 * The rank icons in the tab list and chat come from the Nautical Ranks resource pack that ships
 * with the modpack. Until now it was only switched on through Default Options, which writes the
 * options once, on a client's very first start: anyone who had started the instance before (or
 * whose launcher kept an older options file) never got it and saw empty boxes. Core now adds
 * the pack itself as a required pack, always on, whatever the options say.
 */
public final class RanksPack {

    private static final String FILE = "Nautical-Ranks-V3-Java.zip";

    private RanksPack() {
    }

    public static void onFind(AddPackFindersEvent e) {
        if (e.getPackType() != PackType.CLIENT_RESOURCES) return;
        Path zip = Minecraft.getInstance().getResourcePackDirectory().resolve(FILE);
        if (!Files.isRegularFile(zip)) return;
        PackLocationInfo info = new PackLocationInfo("kronwerke:ranks", Component.literal("Kronwerke Ranks"), PackSource.BUILT_IN, Optional.empty());
        Pack pack = Pack.readMetaAndCreate(info, new FilePackResources.FileResourcesSupplier(zip), PackType.CLIENT_RESOURCES,
                new PackSelectionConfig(true, Pack.Position.TOP, false));
        if (pack != null) e.addRepositorySource(consumer -> consumer.accept(pack));
    }
}
