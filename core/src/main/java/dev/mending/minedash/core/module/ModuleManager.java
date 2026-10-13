package dev.mending.minedash.core.module;

import dev.mending.minedash.api.MineDashModule;
import dev.mending.minedash.api.ModuleInfo;
import dev.mending.minedash.core.util.JsonFiles;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ModuleManager {

    /** Layout of modules.json. */
    private static final class StateFile {
        Map<String, Boolean> modules = new LinkedHashMap<>();
    }

    private final Plugin plugin;
    private final Logger logger;
    private final Path modulesDir;
    private final Path dataRoot;
    private final Path stateFile;

    // Sorted by id and thread-safe, because HTTP threads read it while the main thread changes it
    private final Map<String, LoadedModule> modules = new ConcurrentSkipListMap<>();
    // Only touched inside synchronized methods
    private final Map<String, Boolean> savedState = new LinkedHashMap<>();

    public ModuleManager(Plugin plugin, Path dataDir, Logger logger) {
        this.plugin = plugin;
        this.logger = logger;
        this.modulesDir = dataDir.resolve("modules");
        this.dataRoot = dataDir.resolve("data");
        this.stateFile = dataDir.resolve("modules.json");
    }

    /** Scans the modules folder and enables every module that is not switched off in modules.json. */
    public synchronized void loadAll() throws IOException {
        Files.createDirectories(modulesDir);
        readState();

        List<Path> jars = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(modulesDir, "*.jar")) {
            for (Path jar : stream) {
                jars.add(jar);
            }
        }
        Collections.sort(jars);

        ClassLoader parent = plugin.getClass().getClassLoader();
        for (Path jar : jars) {
            String fileName = jar.getFileName().toString();
            try {
                LoadedModule module = ModuleLoader.load(jar, parent);
                String id = module.getInfo().getId();
                LoadedModule existing = modules.get(id);
                if (existing != null) {
                    logger.warning("Skipping " + fileName + ": module '" + id + "' is already loaded from "
                            + existing.getJar().getFileName());
                    module.closeClassLoader();
                    continue;
                }
                modules.put(id, module);
            } catch (IOException | ModuleException e) {
                logger.warning("Failed to load module " + fileName + ": " + e.getMessage());
            }
        }

        boolean stateChanged = false;
        int enabled = 0;
        for (LoadedModule module : dependencyOrder()) {
            String id = module.getInfo().getId();
            if (!savedState.containsKey(id)) {
                // New modules are enabled by default
                savedState.put(id, true);
                stateChanged = true;
            }
            if (!Boolean.FALSE.equals(savedState.get(id))) {
                try {
                    start(module);
                    enabled++;
                } catch (ModuleException ignored) {
                    // Already logged and stored as module error
                }
            }
        }
        if (stateChanged) {
            writeState();
        }
        logger.info("Found " + modules.size() + " module(s), " + enabled + " enabled.");
    }

    /** Enables a module and remembers the choice. Call on the main thread. */
    public synchronized void enable(String id) throws ModuleException {
        start(require(id));
        saveFlag(id, true);
    }

    /** Disables a module and remembers the choice. Call on the main thread. */
    public synchronized void disable(String id) throws ModuleException {
        LoadedModule module = require(id);
        for (LoadedModule other : modules.values()) {
            if (other.getState() == ModuleState.ENABLED && other.getInfo().getDependencies().contains(id)) {
                throw new ModuleException("Required by enabled module '" + other.getInfo().getId() + "'");
            }
        }
        stop(module);
        saveFlag(id, false);
    }

    /** Disables everything without touching modules.json. Call on the main thread. */
    public synchronized void shutdown() {
        List<LoadedModule> order = dependencyOrder();
        // Dependents first, dependencies last
        Collections.reverse(order);
        for (LoadedModule module : order) {
            stop(module);
            module.closeClassLoader();
        }
        modules.clear();
    }

    public Collection<LoadedModule> getModules() {
        return modules.values();
    }

    public LoadedModule getModule(String id) {
        return id == null ? null : modules.get(id);
    }

    private void start(LoadedModule module) throws ModuleException {
        if (module.getState() == ModuleState.ENABLED) {
            return;
        }
        ModuleInfo info = module.getInfo();

        for (String dependency : info.getDependencies()) {
            LoadedModule required = modules.get(dependency);
            if (required == null || required.getState() != ModuleState.ENABLED) {
                throw fail(module, "Requires module '" + dependency + "' to be enabled", null);
            }
        }

        Path dataFolder = dataRoot.resolve(info.getId());
        ModuleContextImpl context = new ModuleContextImpl(
                plugin, info, new ModuleLogger(logger, info.getId()), dataFolder);

        Thread thread = Thread.currentThread();
        ClassLoader previous = thread.getContextClassLoader();
        thread.setContextClassLoader(module.getClassLoader());
        try {
            Files.createDirectories(dataFolder);
            MineDashModule instance = instantiate(module);
            module.setRuntime(instance, context);
            instance.onEnable(context);

            module.setError(null);
            module.setState(ModuleState.ENABLED);
            logger.info("Enabled module " + info.getName() + " v" + info.getVersion());
        } catch (Exception | LinkageError e) {
            context.close();
            module.setRuntime(null, null);
            throw fail(module, "Failed to enable: " + describe(e), e);
        } finally {
            thread.setContextClassLoader(previous);
        }
    }

    private void stop(LoadedModule module) {
        boolean wasEnabled = module.getState() == ModuleState.ENABLED;
        MineDashModule instance = module.getInstance();
        ModuleContextImpl context = module.getContext();

        if (wasEnabled && instance != null) {
            Thread thread = Thread.currentThread();
            ClassLoader previous = thread.getContextClassLoader();
            thread.setContextClassLoader(module.getClassLoader());
            try {
                instance.onDisable();
            } catch (Exception | LinkageError e) {
                logger.log(Level.WARNING, "Module '" + module.getInfo().getId() + "' failed while disabling", e);
            } finally {
                thread.setContextClassLoader(previous);
            }
        }

        // Stop serving the module first, then tear down connections and routes
        module.setState(ModuleState.DISABLED);
        module.setError(null);
        module.closeConnections();
        if (context != null) {
            context.close();
        }
        module.setRuntime(null, null);

        if (wasEnabled) {
            logger.info("Disabled module " + module.getInfo().getName());
        }
    }

    private MineDashModule instantiate(LoadedModule module) throws Exception {
        String main = module.getInfo().getMain();
        Class<?> type = Class.forName(main, true, module.getClassLoader());
        if (!MineDashModule.class.isAssignableFrom(type)) {
            throw new IllegalStateException("Main class " + main + " does not implement MineDashModule");
        }
        return (MineDashModule) type.getDeclaredConstructor().newInstance();
    }

    private ModuleException fail(LoadedModule module, String message, Throwable cause) {
        module.setState(ModuleState.FAILED);
        module.setError(message);
        String text = "Module '" + module.getInfo().getId() + "': " + message;
        if (cause != null) {
            logger.log(Level.SEVERE, text, cause);
        } else {
            logger.warning(text);
        }
        return new ModuleException(message);
    }

    private String describe(Throwable e) {
        return e.getMessage() != null ? e.getClass().getSimpleName() + ": " + e.getMessage() : e.getClass().getName();
    }

    private LoadedModule require(String id) throws ModuleException {
        LoadedModule module = getModule(id);
        if (module == null) {
            throw new ModuleException("Unknown module '" + id + "'");
        }
        return module;
    }

    /** Dependencies come before the modules that need them. */
    private List<LoadedModule> dependencyOrder() {
        List<LoadedModule> order = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        for (LoadedModule module : modules.values()) {
            visit(module, visited, order);
        }
        return order;
    }

    private void visit(LoadedModule module, Set<String> visited, List<LoadedModule> order) {
        // Marking before recursing also breaks dependency cycles
        if (!visited.add(module.getInfo().getId())) {
            return;
        }
        for (String dependency : module.getInfo().getDependencies()) {
            LoadedModule required = modules.get(dependency);
            if (required != null) {
                visit(required, visited, order);
            }
        }
        order.add(module);
    }

    private void saveFlag(String id, boolean enabled) throws ModuleException {
        savedState.put(id, enabled);
        try {
            writeState();
        } catch (IOException e) {
            throw new ModuleException("State changed, but modules.json could not be saved: " + e.getMessage());
        }
    }

    private void readState() throws IOException {
        savedState.clear();
        if (!Files.exists(stateFile)) {
            return;
        }
        StateFile data = JsonFiles.read(stateFile, StateFile.class);
        if (data != null && data.modules != null) {
            savedState.putAll(data.modules);
        }
    }

    private void writeState() throws IOException {
        StateFile data = new StateFile();
        data.modules = new LinkedHashMap<>(savedState);
        JsonFiles.write(stateFile, data);
    }
}