package dev.mending.minedash.core.module;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import dev.mending.minedash.api.MineDashApi;
import dev.mending.minedash.api.ModuleInfo;
import dev.mending.minedash.api.WidgetInfo;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Pattern;

/** Reads and validates module.json and creates the class loader of a module jar. */
final class ModuleLoader {

    private static final Pattern ID_PATTERN = Pattern.compile("[a-z0-9_-]{1,32}");
    private static final Set<String> WIDGET_SIZES = new HashSet<>(Arrays.asList("small", "medium", "large"));
    private static final Gson GSON = new Gson();
    /** Looked for at the root of the jar when module.json has no "icon". */
    private static final String DEFAULT_ICON = "icon.svg";
    private static final int MAX_ICON_BYTES = 64 * 1024;

    /** The icon of a module, or the reason why it is not used. */
    private static final class Icon {
        final String markup;
        final String warning;

        Icon(String markup, String warning) {
            this.markup = markup;
            this.warning = warning;
        }
    }

    private ModuleLoader() {
    }

    static LoadedModule load(Path jar, ClassLoader parent) throws IOException, ModuleException {
        ModuleInfo info = readInfo(jar);
        validate(info);
        // A broken icon never keeps a module from starting, it is only left out
        Icon icon = readIcon(jar, info);
        return new LoadedModule(info, jar, new ModuleClassLoader(jar, parent), icon.markup, icon.warning);
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

    /**
     * Reads the icon from module.json ("icon" is SVG markup or the path of an SVG file in the jar) or
     * from icon.svg at the root of the jar, and cleans it.
     */
    private static Icon readIcon(Path jar, ModuleInfo info) {
        String value = info.getIcon();
        try (JarFile file = new JarFile(jar.toFile())) {
            String markup;
            if (value == null) {
                JarEntry entry = file.getJarEntry(DEFAULT_ICON);
                if (entry == null) {
                    return new Icon(null, null);
                }
                markup = readText(file, entry);
            } else if (value.startsWith("<")) {
                markup = value;
            } else {
                if (!isSafeIconPath(value)) {
                    return new Icon(null, "'icon' must be SVG markup or the path of an .svg file inside the jar");
                }
                JarEntry entry = file.getJarEntry(value);
                if (entry == null || entry.isDirectory()) {
                    return new Icon(null, "the icon file '" + value + "' is not in the jar");
                }
                markup = readText(file, entry);
            }
            return new Icon(SvgIcon.sanitize(markup, "mdi-" + info.getId() + "-"), null);
        } catch (IOException | ModuleException e) {
            return new Icon(null, e.getMessage());
        }
    }

    private static boolean isSafeIconPath(String path) {
        return path.length() <= 128
                && path.toLowerCase(Locale.ROOT).endsWith(".svg")
                && !path.startsWith("/")
                && !path.contains("..")
                && !path.contains("\\")
                && !path.contains("\0");
    }

    private static String readText(JarFile file, JarEntry entry) throws IOException {
        try (InputStream in = file.getInputStream(entry)) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[4096];
            int read;
            while ((read = in.read(chunk)) != -1) {
                buffer.write(chunk, 0, read);
                if (buffer.size() > MAX_ICON_BYTES) {
                    throw new IOException("the icon file is larger than " + (MAX_ICON_BYTES / 1024) + " KB");
                }
            }
            String text = new String(buffer.toByteArray(), StandardCharsets.UTF_8);
            // A byte order mark in front of the markup would confuse the parser
            return text.startsWith("\uFEFF") ? text.substring(1) : text;
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
        validateWidgets(info);
    }

    private static void validateWidgets(ModuleInfo info) throws ModuleException {
        if (!info.getWidgets().isEmpty() && info.getFrontend() == null) {
            throw new ModuleException("'widgets' require a 'frontend'");
        }
        Set<String> ids = new HashSet<>();
        for (WidgetInfo widget : info.getWidgets()) {
            if (widget == null || widget.getId() == null || !ID_PATTERN.matcher(widget.getId()).matches()) {
                throw new ModuleException("Widget ids must match [a-z0-9_-] and be 1-32 characters long");
            }
            if (!ids.add(widget.getId())) {
                throw new ModuleException("Duplicate widget id '" + widget.getId() + "'");
            }
            if (isBlank(widget.getTitle())) {
                throw new ModuleException("Widget '" + widget.getId() + "' needs a 'title'");
            }
            if (!WIDGET_SIZES.contains(widget.getSize())) {
                throw new ModuleException("Widget '" + widget.getId() + "': 'size' must be small, medium or large");
            }
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}