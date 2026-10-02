package org.ghostplays.ghosttech.internal.module;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Classloader for a single module jar.
 *
 * <h2>Delegation model</h2>
 * Module code is loaded child-last for its own classes and parent-first for everything the plugin
 * owns. The API classes must come from the plugin's classloader, otherwise a module would end up
 * with its own copies of the API types and {@code instanceof} checks against the plugin's classes
 * would fail at runtime.
 *
 * <p>This is the standard isolation setup: the module can freely use the API, Bukkit and Adventure,
 * but two modules cannot see each other's classes, which is what forces them to communicate through
 * {@link org.ghostplays.ghosttech.api.module.ModuleMessaging} instead of direct references.
 */
public final class ModuleClassLoader extends URLClassLoader {

    /** Packages that must always resolve from the plugin, never from the module jar. */
    private static final Set<String> SHARED_PACKAGES = Set.of(
            "org.ghostplays.ghosttech.api.",
            "org.bukkit.",
            "net.kyori.",
            "com.google.gson.",
            "org.jetbrains.annotations.");

    static {
        registerAsParallelCapable();
    }

    /**
     * @param jar      the module jar
     * @param parent   the plugin classloader
     * @param manifest additional URLs the module declared, e.g. shaded libraries
     * @throws IOException if the jar cannot be opened
     */
    public ModuleClassLoader(URL jar, ClassLoader parent, List<URL> manifest) throws IOException {
        super(buildUrls(jar, manifest), parent);
    }

    /**
     * Combines the module jar with any extra libraries into a single URL list.
     *
     * @param jar      the module jar
     * @param manifest extra library URLs
     * @return the URLs to load
     */
    private static URL[] buildUrls(URL jar, List<URL> manifest) {
        List<URL> urls = new ArrayList<>();
        urls.add(jar);
        if (manifest != null) {
            urls.addAll(manifest);
        }
        return urls.toArray(URL[]::new);
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            Class<?> loaded = findLoadedClass(name);
            if (loaded != null) {
                return resolveIfNeeded(resolve, loaded);
            }

            // Shared packages always come from the parent, so API identity is consistent.
            if (isShared(name)) {
                return resolveIfNeeded(resolve, super.loadClass(name, false));
            }

            // Child-first for the module's own classes, so a module can ship its own version of a
            // helper class without affecting the plugin or other modules.
            try {
                Class<?> own = findClass(name);
                return resolveIfNeeded(resolve, own);
            } catch (ClassNotFoundException exception) {
                // Fall through to the parent for anything not in the module jar.
                return resolveIfNeeded(resolve, super.loadClass(name, false));
            }
        }
    }

    /**
     * Resolves the class if requested.
     *
     * @param resolve whether to link the class
     * @param loaded  the class
     * @return the class
     */
    private Class<?> resolveIfNeeded(boolean resolve, Class<?> loaded) {
        if (resolve) {
            resolveClass(loaded);
        }
        return loaded;
    }

    /**
     * Whether a class must be loaded from the plugin rather than the module.
     *
     * @param name fully qualified class name
     * @return {@code true} for shared packages
     */
    private static boolean isShared(String name) {
        for (String prefix : SHARED_PACKAGES) {
            if (name.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}