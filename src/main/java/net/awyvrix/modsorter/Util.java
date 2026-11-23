package net.awyvrix.modsorter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

import static com.mojang.text2speech.Narrator.LOGGER;

public class Util {
    public static void removeEmptyDirectories(Path root) throws IOException {
        root = root.toAbsolutePath().normalize();
        try (Stream<Path> stream = Files.walk(root)) {
            Path finalRoot = root;
            for (Path dir : stream
                    .filter(Files::isDirectory)
                    .filter(path -> !path.equals(finalRoot))
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
}
