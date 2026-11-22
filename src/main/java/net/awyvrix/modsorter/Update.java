package net.awyvrix.modsorter;

import net.awyvrix.modsorter.config.SorterRules;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;

import static com.mojang.text2speech.Narrator.LOGGER;
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
                    .timeout(Duration.ofSeconds(CONFIG.timeout))
                    .GET()
                    .build();

            HttpResponse<Path> response = HTTP.send(request, HttpResponse.BodyHandlers.ofFile(temp));

            if (response.statusCode() != 200) LOGGER.warn("Database update failed: HTTP {}", response.statusCode());
            SorterRules remote = MAPPER.readValue(temp.toFile(), SorterRules.class);

            if (remote.formatVersion != SUPPORTED_FORMAT_VERSION) LOGGER.warn("Unsupported database format: {}", remote.formatVersion);
            if (remote.databaseVersion <= mainConfig.databaseVersion) LOGGER.info("Database is already up to date");
            saveRules(temp, remote);
            Files.move(temp, MAIN_FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);

            mainConfig = remote;
            LOGGER.info("Database updated to version {}", remote.databaseVersion);

        } catch (Exception e) {
            LOGGER.error("Failed to update database", e);

        } finally {
            try {
                Files.deleteIfExists(temp);
            } catch (IOException ignored) {}
        }
    }
}
