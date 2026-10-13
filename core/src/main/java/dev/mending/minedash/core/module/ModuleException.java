package dev.mending.minedash.core.module;

/** Expected module problem. The message is safe to show to the user. */
public class ModuleException extends Exception {

    public ModuleException(String message) {
        super(message);
    }
}