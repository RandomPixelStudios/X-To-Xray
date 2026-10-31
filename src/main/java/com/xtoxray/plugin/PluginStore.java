package com.xtoxray.plugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** Private text files of one plugin. Names cannot contain paths. */
final class PluginStore {
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_-][A-Za-z0-9._-]{0,63}");
    private static final long MAX_FILE = 1024 * 1024;
    private static final long MAX_TOTAL = 16 * 1024 * 1024;
    private final Path folder;

    PluginStore(Path folder) {
        this.folder = folder;
    }

    private Path resolve(String name) {
        if (!NAME.matcher(name).matches()) throw new IllegalArgumentException("Invalid file name");
        return folder.resolve(name);
    }

    Optional<String> read(String name) throws IOException {
        Path file = resolve(name);
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) return Optional.empty();
        return Optional.of(Files.readString(file));
    }

    void write(String name, String content) throws IOException {
        Path file = resolve(name);
        byte[] bytes = content.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (bytes.length > MAX_FILE) throw new IOException("File exceeds 1 MiB");
        Files.createDirectories(folder);
        if (Files.exists(file, LinkOption.NOFOLLOW_LINKS) && !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Not a regular file");
        }
        long used = 0;
        try (Stream<Path> files = Files.list(folder)) {
            for (Path p : (Iterable<Path>) files::iterator) {
                if (!p.equals(file) && Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)) used += Files.size(p);
            }
        }
        if (used + bytes.length > MAX_TOTAL) throw new IOException("Storage quota of 16 MiB exceeded");
        Files.write(file, bytes);
    }

    void delete(String name) throws IOException {
        Files.deleteIfExists(resolve(name));
    }
}
