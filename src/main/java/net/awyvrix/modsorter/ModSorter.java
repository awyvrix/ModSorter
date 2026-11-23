package net.awyvrix.modsorter;

import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static net.awyvrix.modsorter.ModParser.findDestination;
import static net.awyvrix.modsorter.ModParser.readModId;
import static net.awyvrix.modsorter.Util.deleteDirectory;
import static net.awyvrix.modsorter.Util.removeEmptyDirectories;
import static net.awyvrix.modsorter.sorter.ModSorterRules.CONFIG;
import static net.awyvrix.modsorter.sorter.ModSorterRules.RULES;

public final class ModSorter {
    public static final Logger LOGGER = LoggerFactory.getLogger(ModSorter.class);
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
            LOGGER.warn("Mods directory does not exist: {}", modsDir);
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
            LOGGER.error("Failed to scan mods directory", e);
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

                LOGGER.info("Moved {} -> {}", jar.getFileName(), modsDir.relativize(destination));
                moved++;
            } catch (Exception e) {
                LOGGER.warn("Failed to sort {}: {}", jar.getFileName(), e.toString());
                failed++;
            }
        }

        LOGGER.info("Sorting completed: {} moved, {} skipped, {} failed", moved, skipped, failed);
        try {
            if (CONFIG.deleteEmptyFolders) removeEmptyDirectories(modsDir);
            if (CONFIG.deleteServerMods) deleteDirectory(modsDir.resolve("server"));
            if (CONFIG.deleteClientMods) deleteDirectory(modsDir.resolve("client"));
            if (CONFIG.deleteClientAndServerMods) deleteDirectory(modsDir.resolve("client-server"));
        } catch (IOException e) {
            LOGGER.warn("Failed to polish mods folder", e);
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
                LOGGER.error("Failed to defragment {}: {}", source, e.getMessage());
            }
        }

        if (CONFIG.deleteEmptyFolders) removeEmptyDirectories(modsDir);
        LOGGER.info("Defragmentation completed: {} moved, {} skipped", moved, skipped);
    }

    public static boolean replaceMods(Path source, Path destination, Path installed) throws IOException {
        Path backupDir = FMLPaths.CONFIGDIR.get().resolve("modsorter/backup");
        Files.createDirectories(backupDir);
        Path backup = null;

        try {
            Path bkp = backupDir.resolve(UUID.randomUUID() + "-" + installed.getFileName());
            Files.move(installed, bkp);
            backup = bkp;

            Files.createDirectories(destination.getParent());
            Files.move(source, destination);

            Files.deleteIfExists(backup);
            LOGGER.info("Replaced {} -> {}", installed.getFileName(), destination.getFileName());
            return true;
        } catch (IOException e) {
            if (backup != null && Files.exists(backup)) {
                Files.createDirectories(installed.getParent());
                Files.move(backup, installed, StandardCopyOption.REPLACE_EXISTING);
            }
            throw e;
        }
    }
}