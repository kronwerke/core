package de.kronwerke.core.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import de.kronwerke.core.KronwerkeCore;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Pack maintenance only: with -Dkronwerke.dumpKeys=<file> the client writes every key mapping
 * (name, category, default key and modifier, current key and modifier) to that file on the
 * first client tick (key mappings exist before the resources load), and quits. Used to lay out the pack's default keys without
 * conflicts. Does nothing in normal play.
 */
public final class KeyDump {

    private static boolean done;

    private KeyDump() {
    }

    public static void register() {
        String file = System.getProperty("kronwerke.dumpKeys");
        if (file == null || file.isBlank()) return;
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.ClientTickEvent.Post e) -> {
            if (done || Minecraft.getInstance().options == null) return;
            done = true;
            List<String> lines = new ArrayList<>();
            for (KeyMapping k : Minecraft.getInstance().options.keyMappings) {
                lines.add(String.join("\t", k.getName(), k.getCategory(),
                        k.getDefaultKey().getName(), k.getDefaultKeyModifier().name(),
                        k.getKey().getName(), k.getKeyModifier().name(), k.getKeyConflictContext().toString()));
            }
            try {
                Files.write(Path.of(file), lines);
                KronwerkeCore.LOGGER.info("Wrote {} key mappings to {}", lines.size(), file);
            } catch (IOException ex) {
                KronwerkeCore.LOGGER.warn("Key dump failed: {}", ex.toString());
            }
            Runtime.getRuntime().halt(0);
        });
    }
}
