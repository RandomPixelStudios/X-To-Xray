package com.xtoxray.plugin;

import com.xtoxray.api.plugin.PluginStorage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

final class PluginStorageImpl implements PluginStorage {
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_-][A-Za-z0-9._-]{0,63}");
    private static final long MAX_FILE = 1024 * 1024;
    private static final long MAX_TOTAL = 16 * 1024 * 1024;
    private final Path folder;

    PluginStorageImpl(Path folder) {
        this.folder = folder;
    }

    private Path resolve(String name) {
        if (name == null || !NAME.matcher(name).matches()) throw new IllegalArgumentException("Invalid file name: " + name);
        return folder.resolve(name);
    }

    @Override
    public Optional<String> read(String name) throws IOException {
        Path file = resolve(name);
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) return Optional.empty();
        return Optional.of(Files.readString(file, StandardCharsets.UTF_8));
    }

    @Override
    public void write(String name, String content) throws IOException {
        Path file = resolve(name);
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_FILE) throw new IOException("File exceeds 1 MiB");
        Files.createDirectories(folder);
        if (Files.exists(file, LinkOption.NOFOLLOW_LINKS) && !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Not a regular file: " + name);
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

    @Override
    public boolean delete(String name) throws IOException {
        return Files.deleteIfExists(resolve(name));
    }

    @Override
    public List<String> list() throws IOException {
        if (!Files.isDirectory(folder)) return List.of();
        try (Stream<Path> files = Files.list(folder)) {
            return files.filter(p -> Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS))
                .map(p -> p.getFileName().toString()).sorted().toList();
        }
    }
}
