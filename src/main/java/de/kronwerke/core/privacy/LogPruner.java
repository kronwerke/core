package de.kronwerke.core.privacy;

import de.kronwerke.core.KronwerkeCore;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

/**
 * Minecraft keeps every rotated log forever, and each one holds the names and addresses of
 * the players who joined that day. This removes the rotated ones older than the configured
 * number of days. latest.log and anything that is not a rotated log are left alone.
 */
public final class LogPruner {
    private LogPruner() {}

    public static int prune(Path logs, int days) {
        if (days <= 0 || !Files.isDirectory(logs)) return 0;
        Instant cutoff = Instant.now().minus(Duration.ofDays(days));
        int removed = 0;
        try (DirectoryStream<Path> files = Files.newDirectoryStream(logs, "*.log.gz")) {
            for (Path f : files) {
                if (!Files.isRegularFile(f)) continue;
                try {
                    if (Files.getLastModifiedTime(f).toInstant().isBefore(cutoff)) {
                        Files.delete(f);
                        removed++;
                    }
                } catch (IOException e) {
                    KronwerkeCore.LOGGER.warn("Could not remove old log {}: {}", f.getFileName(), e.toString());
                }
            }
        } catch (IOException e) {
            KronwerkeCore.LOGGER.warn("Could not look for old logs: {}", e.toString());
        }
        if (removed > 0) KronwerkeCore.LOGGER.info("Removed {} logs older than {} days", removed, days);
        return removed;
    }
}
