package de.kronwerke.core.config;

import de.kronwerke.core.KronwerkeCore;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

/**
 * Settings changed on the server itself, kept where no pack update reaches them. The pack ships
 * config/kronwerke-common.toml and every update writes it again; what the team changed by a
 * command (/kw admin locks off) or by hand in kronwerke/overrides.properties is applied on top at
 * every start and stays. Keys are the config paths, like locks.machineLocks or slots.defaultSlots.
 */
public final class Overrides {
    private static final Path FILE = FMLPaths.GAMEDIR.get().resolve("kronwerke").resolve("overrides.properties");

    private Overrides() {
    }

    /** Puts every override into the loaded config; bad lines are named in the log and skipped. */
    public static void apply() {
        Properties p = read();
        for (String key : p.stringPropertyNames()) {
            if (!set(key, p.getProperty(key))) KronwerkeCore.LOGGER.warn("overrides.properties: {} is no setting or {} does not fit it", key, p.getProperty(key));
        }
        if (!p.isEmpty()) KronwerkeCore.LOGGER.info("{} server settings from kronwerke/overrides.properties", p.size());
    }

    /** Changes a setting now and keeps the change for the next starts. */
    public static boolean put(String key, String value) {
        if (!set(key, value)) return false;
        Properties p = read();
        p.setProperty(key, value);
        try {
            Files.createDirectories(FILE.getParent());
            try (Writer w = Files.newBufferedWriter(FILE)) {
                p.store(w, "Kronwerke settings changed on this server; they win over config/kronwerke-common.toml");
            }
        } catch (IOException e) {
            KronwerkeCore.LOGGER.warn("Could not write {}: {}", FILE, e.toString());
        }
        return true;
    }

    private static Properties read() {
        Properties p = new Properties();
        if (Files.exists(FILE)) {
            try (Reader r = Files.newBufferedReader(FILE)) {
                p.load(r);
            } catch (IOException e) {
                KronwerkeCore.LOGGER.warn("Could not read {}: {}", FILE, e.toString());
            }
        }
        return p;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static boolean set(String key, String value) {
        Object o = KronwerkeConfig.SPEC.getValues().get(Arrays.asList(key.split("\\.")));
        if (!(o instanceof ModConfigSpec.ConfigValue v)) return false;
        Object now = v.get();
        try {
            Object next;
            if (now instanceof Boolean) next = Boolean.parseBoolean(value.trim());
            else if (now instanceof Integer) next = Integer.parseInt(value.trim());
            else if (now instanceof Long) next = Long.parseLong(value.trim());
            else if (now instanceof Double) next = Double.parseDouble(value.trim());
            else if (now instanceof List<?>) next = value.isBlank() ? List.of() : Arrays.stream(value.split(",")).map(String::trim).toList();
            else next = value;
            v.set(next);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }
}
