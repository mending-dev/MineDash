package dev.mending.minedash.modules.files;

import java.security.SecureRandom;

/** Names for temporary files that live next to their final target. */
final class TempFiles {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String HEX = "0123456789abcdef";

    private TempFiles() {
    }

    static String newName() {
        byte[] bytes = new byte[4];
        RANDOM.nextBytes(bytes);
        StringBuilder name = new StringBuilder(".minedash-");
        for (byte b : bytes) {
            name.append(HEX.charAt((b >> 4) & 0xF)).append(HEX.charAt(b & 0xF));
        }
        return name.append(".tmp").toString();
    }
}