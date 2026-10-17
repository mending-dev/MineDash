package dev.mending.minedash.core;

import dev.mending.minedash.core.auth.LoginRateLimiter;
import dev.mending.minedash.core.auth.SessionManager;
import dev.mending.minedash.core.auth.UserStore;
import dev.mending.minedash.core.config.ConfigManager;
import dev.mending.minedash.core.config.MineDashConfig;
import dev.mending.minedash.core.metrics.MetricsCollector;
import dev.mending.minedash.core.module.LoadedModule;
import dev.mending.minedash.core.module.ModuleManager;
import dev.mending.minedash.core.web.WebServer;
import org.bukkit.plugin.java.JavaPlugin;

import java.net.BindException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public class MineDashPlugin extends JavaPlugin {

    private WebServer webServer;
    private ModuleManager moduleManager;
    private MetricsCollector metrics;

    @Override
    public void onEnable() {
        if (isFolia()) {
            getLogger().severe("Folia is not supported by MineDash. Use Spigot or Paper.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        MineDashConfig config = null;
        try {
            Path dataDir = getDataFolder().toPath();
            Files.createDirectories(dataDir);

            config = new ConfigManager(dataDir.resolve("config.json")).load();

            UserStore users = new UserStore(dataDir.resolve("users.json"), getLogger());
            users.load();

            SessionManager sessions = new SessionManager(
                    TimeUnit.MINUTES.toMillis(config.auth.sessionTimeoutMinutes),
                    TimeUnit.HOURS.toMillis(config.auth.maxSessionHours));
            LoginRateLimiter limiter = new LoginRateLimiter(
                    config.auth.maxLoginAttempts, TimeUnit.MINUTES.toMillis(config.auth.lockoutMinutes));

            moduleManager = new ModuleManager(this, dataDir, getLogger());
            moduleManager.loadAll();
            for (LoadedModule module : moduleManager.getModules()) {
                if (module.getIconWarning() != null) {
                    getLogger().warning("Module '" + module.getInfo().getId() + "': icon ignored, "
                            + module.getIconWarning());
                }
            }

            metrics = new MetricsCollector(this, getLogger());
            metrics.start();

            webServer = new WebServer(config, users, sessions, limiter, moduleManager, metrics, this, getLogger());
            webServer.start();

            getLogger().info("Web panel running on port " + config.web.port);
            warnIfUnencrypted(config);
        } catch (Exception e) {
            if (hasCause(e, BindException.class)) {
                getLogger().severe("Cannot start the web panel: port " + (config != null ? config.web.port : "?")
                        + " is already in use or not allowed. Change 'web.port' in plugins/MineDash/config.json.");
            } else {
                getLogger().log(Level.SEVERE, "Failed to start MineDash, disabling plugin", e);
            }
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        // Stop the web server first so no request reaches a module that is shutting down
        if (webServer != null) {
            webServer.stop();
        }
        if (metrics != null) {
            metrics.stop();
        }
        if (moduleManager != null) {
            moduleManager.shutdown();
        }
        getLogger().info("MineDash disabled.");
    }

    private void warnIfUnencrypted(MineDashConfig config) {
        String host = config.web.host;
        boolean loopback = host.equals("127.0.0.1") || host.equals("localhost") || host.equals("::1");
        if (!loopback && !config.auth.secureCookie) {
            getLogger().warning("The panel is served over plain HTTP, so passwords are sent unencrypted. "
                    + "For public servers use an HTTPS reverse proxy (see 'web.host', 'web.trustProxy' "
                    + "and 'auth.secureCookie' in config.json).");
        }
    }

    private static boolean isFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private static boolean hasCause(Throwable error, Class<? extends Throwable> type) {
        int depth = 0;
        for (Throwable t = error; t != null && depth < 10; t = t.getCause(), depth++) {
            if (type.isInstance(t)) {
                return true;
            }
        }
        return false;
    }
}