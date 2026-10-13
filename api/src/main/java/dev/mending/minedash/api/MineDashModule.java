package dev.mending.minedash.api;

/** Entry point of a module. The class is referenced by "main" in module.json. */
public interface MineDashModule {

    /** Called on the server main thread. Register routes and channels here. */
    void onEnable(ModuleContext context) throws Exception;

    /** Called on the server main thread. Stop tasks and release resources here. */
    default void onDisable() {
    }
}