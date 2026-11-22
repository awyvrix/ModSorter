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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static net.awyvrix.modsorter.sorter.ModSorterRules.CONFIG;
import static net.awyvrix.modsorter.sorter.ModSorterRules.RULES;

public final class ModSorter {
    private static final Logger LOGGER = LoggerFactory.getLogger(ModSorter.class);
    private static final Pattern MOD_ID = Pattern.compile("^modId\\s*=\\s*\"([^\"]+)\"");
    private static final Pattern MODS_SECTION = Pattern.compile("^\\[\\[mods]]\\s*(?:#.*)?$");
    private static final Set<String> EARLY_SERVICES = Set.of(
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
            removeEmptyDirectories(modsDir);
            if (CONFIG.deleteServerMods) deleteDirectory(modsDir.resolve("server"));
            if (CONFIG.deleteClientMods) deleteDirectory(modsDir.resolve("client"));
        } catch (IOException e) {
            LOGGER.warn("Failed to polish mods folder", e);
        }
    }

    private static Path findDestination(Path jar, Path modsDir) throws Exception {
        String modId;
        Path destination;

        try (JarFile file = new JarFile(jar.toFile())) {
            if (hasEarlyServices(file)) {
                LOGGER.debug("Skipping early service jar: {}", jar.getFileName());
                return null;
            }

            modId = readModId(file);
            if (RULES.get(modId) == null) {
                LOGGER.warn("ModId is not in sortList or not found: {}", jar.getFileName());
                return null;
            }
            Path category = Path.of(RULES.get(modId).path());
            Path normalized = category.normalize();

            if (normalized.isAbsolute() || normalized.startsWith("..")) throw new IllegalArgumentException("Invalid category path: " + category);
            destination = modsDir.resolve(normalized).resolve(jar.getFileName()).normalize();
        }

        if (!destination.startsWith(modsDir) || destination.equals(jar)) return null;
        Path installed = findInstalledMod(destination.getParent(), modId);

        if (installed != null && CONFIG.alwaysOverride) {
            if (replaceMods(jar, destination, installed)) return null;
        }
        return destination;
    }

    private static String readModId(JarFile file) throws IOException {
        JarEntry entry = file.getJarEntry("META-INF/neoforge.mods.toml");

        if (entry == null) return null;
        boolean inMods = false;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(entry), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();

                if (line.startsWith("[")) {
                    inMods = MODS_SECTION.matcher(line).matches();
                    continue;
                }

                if (inMods) {
                    Matcher matcher = MOD_ID.matcher(line);
                    if (matcher.find()) return matcher.group(1);
                }
            }
        }

        return null;
    }

    private static boolean hasEarlyServices(JarFile file) {
        for (String service : EARLY_SERVICES) {
            if (file.getEntry("META-INF/services/" + service) != null) return true;
        }
        return false;
    }

    private static void removeEmptyDirectories(Path root) throws IOException {
        if (!CONFIG.deleteEmptyFolders) return;
        try (Stream<Path> stream = Files.walk(root)) {
            for (Path dir : stream
                    .filter(Files::isDirectory)
                    .filter(path -> !path.equals(root))
                    .sorted(Comparator.reverseOrder())
                    .toList()) {
                try (DirectoryStream<Path> contents = Files.newDirectoryStream(dir)) {
                    if (!contents.iterator().hasNext()) {
                        Files.delete(dir);
                        LOGGER.debug("Removed empty directory: {}", root.relativize(dir));
                    }
                }
            }
        }
    }

    private static Path findInstalledMod(Path category, String modId) throws IOException {
        if (!Files.isDirectory(category)) return null;
        try (Stream<Path> stream = Files.walk(category)) {
            return stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar"))
                    .filter(path -> {
                        try (JarFile file = new JarFile(path.toFile())) {
                            String installedId = readModId(file);
                            return modId.equals(installedId);
                        } catch (Exception e) {
                            LOGGER.debug("Failed to inspect installed jar: {}", path, e);
                            return false;
                        }
                    })
                    .findFirst()
                    .orElse(null);
        }
    }

    private static boolean replaceMods(Path source, Path destination, Path installed) throws IOException {
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

    public static void deleteDirectory(Path dir) throws IOException {
        if (Files.notExists(dir)) return;
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.delete(path);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
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

        removeEmptyDirectories(modsDir);
        LOGGER.info("Defragmentation completed: {} moved, {} skipped", moved, skipped);
    }
}