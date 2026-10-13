package dev.mending.minedash.core;

import dev.mending.minedash.core.auth.LoginRateLimiter;
import dev.mending.minedash.core.auth.SessionManager;
import dev.mending.minedash.core.auth.UserStore;
import dev.mending.minedash.core.config.ConfigManager;
import dev.mending.minedash.core.config.MineDashConfig;
import dev.mending.minedash.core.metrics.MetricsCollector;
import dev.mending.minedash.core.module.ModuleManager;
import dev.mending.minedash.core.web.WebServer;
import org.bukkit.plugin.java.JavaPlugin;

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
        try {
            Path dataDir = getDataFolder().toPath();
            Files.createDirectories(dataDir);

            MineDashConfig config = new ConfigManager(dataDir.resolve("config.json")).load();

            UserStore users = new UserStore(dataDir.resolve("users.json"), getLogger());
            users.load();

            SessionManager sessions = new SessionManager(
                    TimeUnit.MINUTES.toMillis(config.auth.sessionTimeoutMinutes));
            LoginRateLimiter limiter = new LoginRateLimiter(
                    config.auth.maxLoginAttempts, TimeUnit.MINUTES.toMillis(config.auth.lockoutMinutes));

            moduleManager = new ModuleManager(this, dataDir, getLogger());
            moduleManager.loadAll();

            metrics = new MetricsCollector(this, getLogger());
            metrics.start();

            webServer = new WebServer(config, users, sessions, limiter, moduleManager, metrics, this, getLogger());
            webServer.start();

            getLogger().info("Web panel running on port " + config.web.port);
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to start MineDash, disabling plugin", e);
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
}