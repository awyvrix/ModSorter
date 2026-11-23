package net.awyvrix.modsorter;

import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static net.awyvrix.modsorter.ModParser.findDestination;
import static net.awyvrix.modsorter.Util.deleteDirectory;
import static net.awyvrix.modsorter.Util.removeEmptyDirectories;
import static net.awyvrix.modsorter.sorter.ModSorterRules.CONFIG;

public final class ModSorter {
    public static final Pattern MOD_ID = Pattern.compile("^modId\\s*=\\s*\"([^\"]+)\"");
    public static final Pattern MODS_SECTION = Pattern.compile("^\\[\\[mods]]\\s*(?:#.*)?$");
    public static final String JARJAR_PATH = "META-INF/jarjar/";

    public static final Set<String> EARLY_SERVICES = Set.of(
            "cpw.mods.modlauncher.api.ITransformationService",
            "cpw.mods.modlauncher.serviceapi.ITransformerDiscoveryService",
            "cpw.mods.modlauncher.api.ITransformerDiscoveryService",
            "net.neoforged.neoforgespi.locating.IModLocator",
            "net.neoforged.neoforgespi.locating.IModFileCandidateLocator",
            "net.neoforged.neoforgespi.locating.IModFileReader",
            "net.neoforged.neoforgespi.locating.IDependencyLocator",
            "net.neoforged.neoforgespi.earlywindow.GraphicsBootstrapper",
            "net.neoforged.neoforgespi.earlywindow.ImmediateWindowProvider"
    );

    public static void sort() {
        Path modsDir = FMLPaths.MODSDIR.get().toAbsolutePath().normalize();

        if (!Files.isDirectory(modsDir)) {
            Main.LOGGER.warn("Mods directory does not exist: {}", modsDir);
            return;
        }

        int moved = 0;
        int skipped = 0;
        int failed = 0;
        List<Path> jars;

        try (Stream<Path> stream = Files.list(modsDir)) {
            jars = stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar"))
                    .toList();

        } catch (IOException e) {
            Main.LOGGER.error("Failed to scan mods directory", e);
            return;
        }

        for (Path jar : jars) {
            try {
                Path destination = findDestination(jar, modsDir);
                
                if (destination == null || destination.equals(jar)) {
                    skipped++;
                    continue;
                }

                Files.createDirectories(destination.getParent());
                Files.move(jar, destination);

                Main.LOGGER.info("Moved {} -> {}", jar.getFileName(), modsDir.relativize(destination));
                moved++;
            } catch (Exception e) {
                Main.LOGGER.warn("Failed to sort {}: {}", jar.getFileName(), e.toString());
                failed++;
            }
        }

        Main.LOGGER.info("Sorting completed: {} moved, {} skipped, {} failed", moved, skipped, failed);
        try {
            if (CONFIG.deleteEmptyFolders) removeEmptyDirectories(modsDir);
            if (CONFIG.deleteServerMods) deleteDirectory(modsDir.resolve("server"));
            if (CONFIG.deleteClientMods) deleteDirectory(modsDir.resolve("client"));
            if (CONFIG.deleteClientAndServerMods) deleteDirectory(modsDir.resolve("client-server"));
        } catch (IOException e) {
            Main.LOGGER.warn("Failed to polish mods folder", e);
        }
    }

    public static void defragment(boolean rewrite) throws IOException {
        Path modsDir = FMLPaths.MODSDIR.get().toAbsolutePath().normalize();
        List<Path> jars;

        try (Stream<Path> stream = Files.walk(modsDir)) {
            jars = stream.filter(Files::isRegularFile)
                    .filter(path -> !path.getParent().equals(modsDir))
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar"))
                    .toList();
        }
        int moved = 0;
        int skipped = 0;

        for (Path source : jars) {
            Path destination = modsDir.resolve(source.getFileName());

            try {
                if (Files.exists(destination)) {
                    if (rewrite) {
                        Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
                    } else {
                        String name = destination.getFileName().toString();
                        String base = name.substring(0, name.length() - 4);
                        int index = 1;

                        while (Files.exists(destination)) {
                            destination = modsDir.resolve(base + " (" + index++ + ").jar");
                        }

                        Files.move(source, destination);
                    }
                } else {
                    Files.move(source, destination);
                }

                moved++;
            } catch (IOException e) {
                skipped++;
                Main.LOGGER.error("Failed to defragment {}: {}", source, e.getMessage());
            }
        }

        if (CONFIG.deleteEmptyFolders) removeEmptyDirectories(modsDir);
        Main.LOGGER.info("Defragmentation completed: {} moved, {} skipped", moved, skipped);
    }
    
    
}