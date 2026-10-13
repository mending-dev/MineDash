package dev.mending.minedash.core.module;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLConnection;
import java.nio.file.Path;

final class ModuleClassLoader extends URLClassLoader {

    ModuleClassLoader(Path jar, ClassLoader parent) throws IOException {
        super(new URL[]{jar.toUri().toURL()}, parent);
    }

    /** Opens a resource from the module jar only, never from the parent loader. */
    InputStream openOwnResource(String name) throws IOException {
        URL url = findResource(name);
        if (url == null) {
            return null;
        }
        URLConnection connection = url.openConnection();
        // Without this the jar stays cached and locked after the loader is closed
        connection.setUseCaches(false);
        return connection.getInputStream();
    }
}