package dev.mending.minedash.core.auth;

import dev.mending.minedash.api.ApiUser;

import java.util.ArrayList;
import java.util.List;

public class User implements ApiUser {

    public String username;
    /** Plain text password. Hashed and removed from the file on the next start. */
    public String password;
    public String passwordHash;
    public List<String> permissions = new ArrayList<>();

    @Override
    public String getUsername() {
        return username;
    }

    /** Supports exact match, "*" and prefix wildcards like "console.*". */
    @Override
    public boolean hasPermission(String permission) {
        if (permissions == null) return false;
        for (String granted : permissions) {
            if (granted.equals("*") || granted.equals(permission)) return true;
            if (granted.endsWith(".*") && permission.startsWith(granted.substring(0, granted.length() - 1))) {
                return true;
            }
        }
        return false;
    }
}