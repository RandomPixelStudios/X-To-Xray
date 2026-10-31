package com.xtoxray.api.plugin;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/** Text files private to one plugin. Names: letters, digits, '.', '_', '-'. Limits: 1 MiB per file, 16 MiB total. */
public interface PluginStorage {
    Optional<String> read(String name) throws IOException;

    void write(String name, String content) throws IOException;

    boolean delete(String name) throws IOException;

    List<String> list() throws IOException;
}
