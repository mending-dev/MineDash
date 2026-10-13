package dev.mending.minedash.core.module;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import dev.mending.minedash.api.MineDashApi;
import dev.mending.minedash.api.ModuleInfo;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Pattern;

/** Reads and validates module.json and creates the class loader of a module jar. */
final class ModuleLoader {

    private static final Pattern ID_PATTERN = Pattern.compile("[a-z0-9_-]{1,32}");
    private static final Gson GSON = new Gson();

    private ModuleLoader() {
    }

    static LoadedModule load(Path jar, ClassLoader parent) throws IOException, ModuleException {
        ModuleInfo info = readInfo(jar);
        validate(info);
        return new LoadedModule(info, jar, new ModuleClassLoader(jar, parent));
    }

    private static ModuleInfo readInfo(Path jar) throws IOException {
        try (JarFile file = new JarFile(jar.toFile())) {
            JarEntry entry = file.getJarEntry("module.json");
            if (entry == null) {
                throw new IOException("module.json is missing");
            }
            try (Reader reader = new InputStreamReader(file.getInputStream(entry), StandardCharsets.UTF_8)) {
                ModuleInfo info = GSON.fromJson(reader, ModuleInfo.class);
                if (info == null) {
                    throw new IOException("module.json is empty");
                }
                return info;
            } catch (JsonParseException e) {
                throw new IOException("module.json is invalid: " + e.getMessage(), e);
            }
        }
    }

    private static void validate(ModuleInfo info) throws ModuleException {
        if (info.getId() == null || !ID_PATTERN.matcher(info.getId()).matches()) {
            throw new ModuleException("'id' must match [a-z0-9_-] and be 1-32 characters long");
        }
        if (isBlank(info.getName()) || isBlank(info.getVersion()) || isBlank(info.getMain())) {
            throw new ModuleException("'name', 'version' and 'main' are required");
        }
        if (info.getApiVersion() != MineDashApi.VERSION) {
            throw new ModuleException("Requires API version " + info.getApiVersion()
                    + " but this MineDash provides version " + MineDashApi.VERSION);
        }
        String frontend = info.getFrontend();
        if (frontend != null && (frontend.contains("..") || frontend.contains("\\") || frontend.startsWith("/"))) {
            throw new ModuleException("'frontend' must be a relative path without '..'");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}