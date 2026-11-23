package net.awyvrix.modsorter;

import net.awyvrix.modsorter.config.SorterRules;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;

import static net.awyvrix.modsorter.config.ConfigManager.*;
import static net.awyvrix.modsorter.sorter.ModSorterRules.CONFIG;

public class Update {
    private static final int SUPPORTED_FORMAT_VERSION = 1;
    private static final URI DATABASE_URI = URI.create(CONFIG.database);

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public static void updateDatabase() {
        Path temp = MAIN_FILE.resolveSibling(MAIN_FILE.getFileName() + ".tmp");

        try {
            HttpRequest request = HttpRequest.newBuilder(DATABASE_URI)
                    .timeout(Duration.ofSeconds(CONFIG.timeout > 0 ? CONFIG.timeout : 15))
                    .GET()
                    .build();

            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (response.statusCode() != 200) Main.LOGGER.warn("Database update failed: HTTP {}", response.statusCode());
            SorterRules remote = MAPPER.readValue(response.body(), SorterRules.class);

            if (remote.mods == null || remote.formatVersion < 1 || remote.databaseVersion < 1) Main.LOGGER.warn("Downloaded database is invalid");
            if (remote.formatVersion != SUPPORTED_FORMAT_VERSION) Main.LOGGER.warn("Unsupported database format: {}", remote.formatVersion);
            if (remote.databaseVersion <= mainConfig.databaseVersion) Main.LOGGER.info("Database is already up to date");
            saveRules(temp, remote);
            Files.move(temp, MAIN_FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);

            mainConfig = remote;
            Main.LOGGER.info("Database updated to version {}", remote.databaseVersion);

        } catch (Exception e) {
            Main.LOGGER.error("Failed to update database", e);

        } finally {
            try {
                Files.deleteIfExists(temp);
            } catch (IOException ignored) {}
        }
    }
}
