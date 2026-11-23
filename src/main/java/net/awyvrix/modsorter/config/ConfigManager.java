package net.awyvrix.modsorter.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import net.awyvrix.modsorter.Main;
import net.awyvrix.modsorter.Update;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ConfigManager {
    public static final Path MAIN_FILE = Path.of("config/modsorter/main.json");
    public static final Path OVERRIDE_FILE = Path.of("config/modsorter/override.json");
    public static final Path CONFIG_FILE = Path.of("config/modsorter/common.json");
    public static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public static SorterRules mainConfig = new SorterRules();
    public static SorterRules overrideConfig = new SorterRules();
    public static Config commonConfig = new Config();

    public static SorterRules loadMain() throws IOException {
        if (Files.exists(MAIN_FILE)) {
            mainConfig = MAPPER.readValue(MAIN_FILE.toFile(), SorterRules.class);
            Main.LOGGER.info("SorterConfig loaded with: {} entries", mainConfig.mods.size());

        } else {
            Main.LOGGER.info("SorterConfig not found - Creating new");
            mainConfig = new SorterRules();
            saveRules(MAIN_FILE, mainConfig);

        }
        if (commonConfig.autoUpdateDatabase) {
            Update.updateDatabase();
        }
        return mainConfig;
    }

    public static SorterRules loadOverride() throws IOException {
        if (Files.exists(OVERRIDE_FILE)) {
            overrideConfig = MAPPER.readValue(OVERRIDE_FILE.toFile(), SorterRules.class);
            Main.LOGGER.info("Override SorterConfig loaded");

        } else {
            Main.LOGGER.info("Override SorterConfig not found - Creating new");
            overrideConfig = new SorterRules();
            saveRules(OVERRIDE_FILE, overrideConfig);
        }
        return overrideConfig;
    }

    public static Config loadConfig() throws IOException {
        if (Files.exists(CONFIG_FILE)) {
            commonConfig = MAPPER.readValue(CONFIG_FILE.toFile(), Config.class);
            Main.LOGGER.info("Common Config loaded");

        } else {
            Main.LOGGER.info("Common Config not found - Creating new");
            commonConfig = new Config();
        }
        Files.createDirectories(CONFIG_FILE.getParent());
        MAPPER.writeValue(CONFIG_FILE.toFile(), commonConfig);
        return commonConfig;
    }

    public static void saveRules(Path file, SorterRules cfg) throws IOException {
        Files.createDirectories(file.getParent());
        MAPPER.writeValue(file.toFile(), cfg);
    }
}