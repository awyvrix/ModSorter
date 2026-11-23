package net.awyvrix.modsorter.sorter;

import cpw.mods.modlauncher.api.IEnvironment;
import cpw.mods.modlauncher.api.ITransformationService;
import cpw.mods.modlauncher.api.ITransformer;
import net.awyvrix.modsorter.Main;
import net.awyvrix.modsorter.ModSorter;
import net.awyvrix.modsorter.config.ConfigManager;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Set;

import static net.awyvrix.modsorter.ModSorter.defragment;
import static net.awyvrix.modsorter.config.ConfigManager.*;
import static net.awyvrix.modsorter.sorter.ModSorterRules.CONFIG;
import static net.awyvrix.modsorter.sorter.ModSorterRules.RULES;

public final class ModSorterService implements ITransformationService {

    @Override
    public String name() {
        return "modsorter";
    }

    @Override
    public void onLoad(IEnvironment environment, Set<String> otherServices) {}

    @Override
    public void initialize(IEnvironment environment) {
        try {
            CONFIG = ConfigManager.loadConfig();
            RULES.putAll(ConfigManager.loadMain().mods);
            if (CONFIG.useOverrides) RULES.putAll(ConfigManager.loadOverride().mods);
        } catch (IOException e) {
            Main.LOGGER.warn("Failed to load config");
            throw new RuntimeException(e);
        }

        if (CONFIG.defragmentation) {
            try {
                Main.LOGGER.info("Initialising Defragmentation process. Rewrite: {}", CONFIG.rewriteOnConflict);

                defragment(CONFIG.rewriteOnConflict);
                CONFIG.defragmentation = false;
                Files.createDirectories(CONFIG_FILE.getParent());
                MAPPER.writeValue(CONFIG_FILE.toFile(), commonConfig);

                Main.LOGGER.info("Defragmentation successfully finished");
            } catch (IOException e) {
                Main.LOGGER.error("Failed to complete defragmentation process");
                throw new RuntimeException(e);
            }
        } else {
            Main.LOGGER.info("Initialising ModSorter");
            try {
                if (commonConfig.resort) defragment(CONFIG.rewriteOnConflict);
                ModSorter.sort();
            } catch (IOException e) {
                Main.LOGGER.error("Failed to complete sorting process");
                throw new RuntimeException(e);
            }
            Main.LOGGER.info("ModSorter initialized");
        }
        if (commonConfig.exitOnComplete) System.exit(1);
    }

    @Override
    public @NotNull List<? extends ITransformer<?>> transformers() {
        return List.of();
    }
}