package dev.mending.minedash.core.auth;

import dev.mending.minedash.core.util.JsonFiles;
import org.mindrot.jbcrypt.BCrypt;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

public final class UserStore {

    private static final int BCRYPT_ROUNDS = 10;
    private static final String PASSWORD_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";

    private static final class UsersFile {
        List<User> users = new ArrayList<>();
    }

    private final Path file;
    private final Logger logger;
    private final SecureRandom random = new SecureRandom();
    // Used to keep login timing equal for unknown usernames
    private final String dummyHash = BCrypt.hashpw("minedash-dummy", BCrypt.gensalt(BCRYPT_ROUNDS));

    private volatile Map<String, User> users = new HashMap<>();

    public UserStore(Path file, Logger logger) {
        this.file = file;
        this.logger = logger;
    }

    public void load() throws IOException {
        UsersFile data = Files.exists(file) ? JsonFiles.read(file, UsersFile.class) : null;
        boolean changed = false;

        if (data == null || data.users == null || data.users.isEmpty()) {
            data = new UsersFile();
            String password = randomPassword(16);

            User admin = new User();
            admin.username = "admin";
            admin.passwordHash = hash(password);
            admin.permissions.add("*");
            data.users.add(admin);
            changed = true;

            logger.warning("==================================================");
            logger.warning("No accounts found. Created default admin account:");
            logger.warning("  Username: admin");
            logger.warning("  Password: " + password);
            logger.warning("This password is shown only once.");
            logger.warning("==================================================");
        }

        Map<String, User> loaded = new HashMap<>();
        for (User user : data.users) {
            if (user == null || user.username == null || user.username.trim().isEmpty()) {
                logger.warning("Skipping account without username in " + file.getFileName());
                continue;
            }
            // A plain text password always replaces the stored hash
            if (user.password != null && !user.password.isEmpty()) {
                user.passwordHash = hash(user.password);
                user.password = null;
                changed = true;
            }
            if (user.passwordHash == null || user.passwordHash.isEmpty()) {
                logger.warning("Skipping account '" + user.username + "': no password set");
                continue;
            }
            if (user.permissions == null) {
                user.permissions = new ArrayList<>();
            }
            String key = normalize(user.username);
            if (loaded.containsKey(key)) {
                logger.warning("Skipping duplicate account '" + user.username + "'");
                continue;
            }
            loaded.put(key, user);
        }

        if (changed) {
            JsonFiles.write(file, data);
        }
        users = loaded;
        logger.info("Loaded " + loaded.size() + " account(s).");
    }

    public User get(String username) {
        return username == null ? null : users.get(normalize(username));
    }

    /** Returns the user on success, otherwise null. */
    public User authenticate(String username, String password) {
        User user = get(username);
        String hash = user != null ? user.passwordHash : dummyHash;
        boolean valid;
        try {
            valid = BCrypt.checkpw(password, hash);
        } catch (IllegalArgumentException e) {
            valid = false;
        }
        return valid && user != null ? user : null;
    }

    private String hash(String plain) {
        return BCrypt.hashpw(plain, BCrypt.gensalt(BCRYPT_ROUNDS));
    }

    private String normalize(String username) {
        return username.trim().toLowerCase(Locale.ROOT);
    }

    private String randomPassword(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(PASSWORD_CHARS.charAt(random.nextInt(PASSWORD_CHARS.length())));
        }
        return sb.toString();
    }
}