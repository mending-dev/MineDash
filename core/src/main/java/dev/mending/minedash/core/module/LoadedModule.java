package dev.mending.minedash.core.module;

import dev.mending.minedash.api.MineDashModule;
import dev.mending.minedash.api.ModuleInfo;
import dev.mending.minedash.api.WebSocketConnection;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class LoadedModule {

    private final ModuleInfo info;
    private final Path jar;
    private final ModuleClassLoader classLoader;
    private final Set<WebSocketConnection> connections = ConcurrentHashMap.newKeySet();

    private volatile ModuleState state = ModuleState.DISABLED;
    private volatile String error;
    private volatile MineDashModule instance;
    private volatile ModuleContextImpl context;

    LoadedModule(ModuleInfo info, Path jar, ModuleClassLoader classLoader) {
        this.info = info;
        this.jar = jar;
        this.classLoader = classLoader;
    }

    public ModuleInfo getInfo() {
        return info;
    }

    public Path getJar() {
        return jar;
    }

    public ModuleState getState() {
        return state;
    }

    /** Reason of the last failure, or null. */
    public String getError() {
        return error;
    }

    /** Only available while the module is enabled. */
    public ModuleContextImpl getContext() {
        return context;
    }

    /** Opens a file from the module jar, or returns null if it does not exist. */
    public InputStream openResource(String name) throws IOException {
        return classLoader.openOwnResource(name);
    }

    public void addConnection(WebSocketConnection connection) {
        connections.add(connection);
    }

    public void removeConnection(WebSocketConnection connection) {
        connections.remove(connection);
    }

    MineDashModule getInstance() {
        return instance;
    }

    ModuleClassLoader getClassLoader() {
        return classLoader;
    }

    void setState(ModuleState state) {
        this.state = state;
    }

    void setError(String error) {
        this.error = error;
    }

    void setRuntime(MineDashModule instance, ModuleContextImpl context) {
        this.instance = instance;
        this.context = context;
    }

    void closeConnections() {
        for (WebSocketConnection connection : connections) {
            try {
                connection.close();
            } catch (RuntimeException ignored) {
                // Connection is already gone
            }
        }
        connections.clear();
    }

    void closeClassLoader() {
        try {
            classLoader.close();
        } catch (IOException ignored) {
            // Nothing we can do
        }
    }
}