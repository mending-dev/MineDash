package dev.mending.minedash.api;

public interface ApiUser {

    String getUsername();

    /** Supports exact match, "*" and prefix wildcards like "console.*". */
    boolean hasPermission(String permission);
}