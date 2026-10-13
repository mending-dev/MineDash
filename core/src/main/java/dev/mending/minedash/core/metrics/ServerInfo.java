package dev.mending.minedash.core.metrics;

import org.bukkit.Bukkit;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Static facts about the server and the machine. Collected once. */
public final class ServerInfo {

    public static final class Os {
        public final String name;
        public final String version;
        public final String arch;

        private Os(String name, String version, String arch) {
            this.name = name;
            this.version = version;
            this.arch = arch;
        }
    }

    public static final class Java {
        public final String version;
        public final String vendor;
        public final String vm;

        private Java(String version, String vendor, String vm) {
            this.version = version;
            this.vendor = vendor;
            this.vm = vm;
        }
    }

    private static final Pattern MC_VERSION = Pattern.compile("\\(MC: ([^)]+)\\)");

    public final String software;
    public final String minecraftVersion;
    public final String build;
    public final int port;
    public final boolean onlineMode;
    public final Os os;
    public final Java java;
    public final int processors;
    public final long totalMemory;
    public final long maxHeap;
    public final String timezone;

    private ServerInfo(String software, String minecraftVersion, String build, int port, boolean onlineMode,
                       Os os, Java java, int processors, long totalMemory, long maxHeap, String timezone) {
        this.software = software;
        this.minecraftVersion = minecraftVersion;
        this.build = build;
        this.port = port;
        this.onlineMode = onlineMode;
        this.os = os;
        this.java = java;
        this.processors = processors;
        this.totalMemory = totalMemory;
        this.maxHeap = maxHeap;
        this.timezone = timezone;
    }

    /** Must be called on the server main thread because it uses the Bukkit API. */
    static ServerInfo collect(OsMetrics osMetrics) {
        Runtime runtime = Runtime.getRuntime();
        String version = Bukkit.getVersion();

        Os os = new Os(
                osName(),
                System.getProperty("os.version", ""),
                System.getProperty("os.arch", ""));
        Java java = new Java(
                System.getProperty("java.version", ""),
                System.getProperty("java.vendor", ""),
                System.getProperty("java.vm.name", ""));

        return new ServerInfo(
                detectSoftware(),
                minecraftVersion(version),
                version.replaceAll("\\s*\\(MC: [^)]+\\)", "").trim(),
                Bukkit.getPort(),
                Bukkit.getOnlineMode(),
                os,
                java,
                runtime.availableProcessors(),
                osMetrics.totalPhysicalMemory(),
                runtime.maxMemory(),
                TimeZone.getDefault().getID());
    }

    private static String minecraftVersion(String version) {
        Matcher matcher = MC_VERSION.matcher(version);
        if (matcher.find()) {
            return matcher.group(1);
        }
        // Fallback: "1.20.4-R0.1-SNAPSHOT" -> "1.20.4"
        String bukkit = Bukkit.getBukkitVersion();
        int dash = bukkit.indexOf('-');
        return dash > 0 ? bukkit.substring(0, dash) : bukkit;
    }

    private static String detectSoftware() {
        // Forks like Paper and Purpur report their own name, Spigot still says CraftBukkit
        String name = Bukkit.getName();
        if (name != null && !name.equals("CraftBukkit")) {
            return name;
        }
        ClassLoader loader = Bukkit.getServer().getClass().getClassLoader();
        if (present("io.papermc.paper.configuration.GlobalConfiguration", loader)
                || present("com.destroystokyo.paper.PaperConfig", loader)
                || present("org.github.paperspigot.PaperSpigotConfig", loader)) {
            return "Paper";
        }
        if (present("org.spigotmc.SpigotConfig", loader)) {
            return "Spigot";
        }
        return "CraftBukkit";
    }

    private static boolean present(String className, ClassLoader loader) {
        try {
            Class.forName(className, false, loader);
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    /** "Debian GNU/Linux 12 (bookworm)" on Linux, otherwise the plain os.name. */
    private static String osName() {
        String name = System.getProperty("os.name", "Unknown");
        if (name.toLowerCase(Locale.ROOT).contains("linux")) {
            try {
                List<String> lines = Files.readAllLines(Paths.get("/etc/os-release"), StandardCharsets.UTF_8);
                for (String line : lines) {
                    if (line.startsWith("PRETTY_NAME=")) {
                        String value = line.substring("PRETTY_NAME=".length()).trim();
                        value = value.replaceAll("^\"|\"$", "");
                        if (!value.isEmpty()) {
                            return value;
                        }
                    }
                }
            } catch (IOException | RuntimeException ignored) {
                // Not available, use the generic name
            }
        }
        return name;
    }
}