package de.kronwerke.core.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mojang.blaze3d.platform.InputConstants;

import de.kronwerke.core.KronwerkeCore;
import de.kronwerke.core.config.KronwerkeClientConfig;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.settings.KeyModifier;
import net.neoforged.neoforge.common.NeoForge;

/**
 * The pack's key layout. Default Options only writes keybindings.txt into an instance at its
 * very first start, so instances started before a layout change kept the mods' own defaults,
 * with their conflicts. The layout ships in assets/kronwerke/keys.tsv, written by the pack's
 * tools/keys/layout.py: one line per key and earlier default (name, the mod's or an earlier
 * pack layout's key and modifier, the pack's key and modifier), and a version number. Once per
 * version, on the first client tick, every key that still sits on such a default moves to the
 * pack's; a key the player chose is left alone.
 */
public final class DefaultKeys {

    private record Move(String fromKey, KeyModifier fromMod, String toKey, KeyModifier toMod) {
    }

    private static boolean done;

    private DefaultKeys() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.ClientTickEvent.Post e) -> {
            // the options and the client config are loaded by the first tick, long before the
            // resources are; wait for both
            if (done || Minecraft.getInstance().options == null || !KronwerkeClientConfig.SPEC.isLoaded()) return;
            done = true;
            try {
                apply();
            } catch (RuntimeException ex) {
                KronwerkeCore.LOGGER.warn("The pack's key layout was not applied: {}", ex.toString());
            }
        });
    }

    private static void apply() {
        int[] version = {0};
        Map<String, List<Move>> moves = read(version);
        if (moves.isEmpty() || KronwerkeClientConfig.KEYS_APPLIED.get() >= version[0]) return;
        Minecraft mc = Minecraft.getInstance();
        int moved = 0;
        for (KeyMapping k : mc.options.keyMappings) {
            List<Move> ms = moves.get(k.getName());
            if (ms == null) continue;
            for (Move m : ms) {
                if (!k.getKey().getName().equals(m.fromKey()) || k.getKeyModifier() != m.fromMod()) continue;
                k.setKeyModifierAndCode(m.toMod(), InputConstants.getKey(m.toKey()));
                moved++;
                break;
            }
        }
        KeyMapping.resetMapping();
        mc.options.save();
        KronwerkeClientConfig.KEYS_APPLIED.set(version[0]);
        KronwerkeClientConfig.SPEC.save();
        KronwerkeCore.LOGGER.info("Key layout {} applied: {} keys moved from their mod default", version[0], moved);
    }

    private static Map<String, List<Move>> read(int[] version) {
        Map<String, List<Move>> out = new HashMap<>();
        try (InputStream in = DefaultKeys.class.getResourceAsStream("/assets/kronwerke/keys.tsv")) {
            if (in == null) return out;
            BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            List<String> bad = new ArrayList<>();
            while ((line = r.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] f = line.split("\t");
                if (f[0].equals("version") && f.length > 1) {
                    version[0] = Integer.parseInt(f[1].trim());
                    continue;
                }
                if (f.length < 5) {
                    bad.add(f[0]);
                    continue;
                }
                out.computeIfAbsent(f[0], x -> new ArrayList<>()).add(new Move(f[1], KeyModifier.valueFromString(f[2]), f[3], KeyModifier.valueFromString(f[4])));
            }
            if (!bad.isEmpty()) KronwerkeCore.LOGGER.warn("Key layout lines without five fields: {}", bad);
        } catch (IOException | RuntimeException e) {
            KronwerkeCore.LOGGER.warn("The pack's key layout could not be read: {}", e.toString());
        }
        return out;
    }
}
