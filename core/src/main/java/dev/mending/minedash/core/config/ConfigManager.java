package dev.mending.minedash.core.config;

import dev.mending.minedash.core.util.JsonFiles;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ConfigManager {

    private final Path file;

    public ConfigManager(Path file) {
        this.file = file;
    }

    public MineDashConfig load() throws IOException {
        MineDashConfig config = null;
        if (Files.exists(file)) {
            config = JsonFiles.read(file, MineDashConfig.class);
        }
        if (config == null) {
            config = new MineDashConfig();
        }
        config.validate();

        // Write back so new options are added to existing files
        JsonFiles.write(file, config);
        return config;
    }
}