package net.awyvrix.modsorter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.Locale;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;
import java.util.regex.Matcher;
import java.util.stream.Stream;

import static com.mojang.text2speech.Narrator.LOGGER;
import static net.awyvrix.modsorter.ModSorter.*;
import static net.awyvrix.modsorter.sorter.ModSorterRules.CONFIG;
import static net.awyvrix.modsorter.sorter.ModSorterRules.RULES;

public final class ModParser {
    public static String readModId(JarFile file) throws IOException {
        JarEntry entry = file.getJarEntry("META-INF/neoforge.mods.toml");

        if (entry != null) {
            try (InputStream input = file.getInputStream(entry)) {
                String modId = readModId(input);
                if (modId != null) return modId;
            }
        }
        Enumeration<JarEntry> entries = file.entries();

        while (entries.hasMoreElements()) {
            JarEntry nestedEntry = entries.nextElement();
            String name = nestedEntry.getName();

            if (nestedEntry.isDirectory()
                    || !name.startsWith(JARJAR_PATH)
                    || !name.toLowerCase(Locale.ROOT).endsWith(".jar")
                    || name.substring(JARJAR_PATH.length()).contains("/")
                    || name.contains("\\")) continue;

            try (InputStream input = file.getInputStream(nestedEntry);
                 JarInputStream nestedJar = new JarInputStream(input)) {
                String modId = readModIdFromJar(nestedJar);

                if (modId != null) return modId;

            } catch (IOException e) {
                LOGGER.debug("Failed to inspect nested JAR: {}", name, e);
            }
        }

        return null;
    }

    public static String readModId(InputStream input) throws IOException {
        boolean inMods = false;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
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

    public static String readModIdFromJar(JarInputStream jar) throws IOException {
        JarEntry entry;

        while ((entry = jar.getNextJarEntry()) != null) {
            if (entry.getName().equals("META-INF/neoforge.mods.toml")) return readModId(jar);
        }
        return null;
    }

    public static Path findDestination(Path jar, Path modsDir) throws Exception {
        String modId;
        Path destination;

        try (JarFile file = new JarFile(jar.toFile())) {
            if (hasEarlyServices(file)) {
                LOGGER.info("Skipping early service jar: {}", jar.getFileName());
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

    public static boolean hasEarlyServices(JarFile file) {
        for (String service : EARLY_SERVICES) {
            if (file.getEntry("META-INF/services/" + service) != null) return true;
        }
        return false;
    }

    public static Path findInstalledMod(Path category, String modId) throws IOException {
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
}
