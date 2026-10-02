package org.ghostplays.ghosttech.api.module;

import java.io.File;
import java.util.logging.Logger;
import org.ghostplays.ghosttech.api.GhostTechApi;

/**
 * Everything the plugin hands to a module at load time.
 *
 * <p>The context is the module's only view of the plugin. A module reaches functionality through
 * {@link #api()} and can do its own file handling through {@link #dataFolder()}.
 */
public interface ModuleContext {

    /** The descriptor this module was loaded from. */
    ModuleDescriptor descriptor();

    /** The full plugin API. Never {@code null}. */
    GhostTechApi api();

    /**
     * Private, writable directory for this module, created on demand.
     *
     * <p>Each module gets its own folder so configs and databases never collide.
     */
    File dataFolder();

    /**
     * Logger prefixed with the module name, so module output is distinguishable in console.
     *
     * <p>Prefer this over {@code Bukkit.getLogger()} inside modules.
     */
    Logger logger();

    /**
     * Copies a resource bundled inside the module jar into {@link #dataFolder()} if absent.
     *
     * @param resourcePath path inside the module jar, e.g. {@code config.yml}
     * @return {@code true} if the file was written, {@code false} if it already existed
     */
    boolean saveResource(String resourcePath);

    /**
     * Reads a resource bundled inside the module jar.
     *
     * @param resourcePath path inside the module jar
     * @return the open stream, or {@code null} when the resource does not exist. The caller owns
     *         the stream and must close it.
     */
    java.io.InputStream getResource(String resourcePath);
}