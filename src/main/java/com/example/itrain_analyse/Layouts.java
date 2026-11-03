package com.example.itrain_analyse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/** Layout-Dateien (.tcdz/.tcd) im iTrain-Ordner layouts, neueste zuerst. */
public final class Layouts {

    private Layouts() {
    }

    public static List<Path> liste(Path ordner) {
        if (ordner == null || !Files.isDirectory(ordner)) {
            return List.of();
        }
        try (Stream<Path> s = Files.list(ordner)) {
            return s.filter(Files::isRegularFile)
                    .filter(p -> {
                        String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
                        return n.endsWith(".tcdz") || n.endsWith(".tcd");
                    })
                    .sorted(Comparator.comparing(Layouts::geaendert).reversed())
                    .toList();
        } catch (IOException ex) {
            return List.of();
        }
    }

    private static Long geaendert(Path p) {
        try {
            return Files.getLastModifiedTime(p).toMillis();
        } catch (IOException ex) {
            return 0L;
        }
    }
}
