package org.ghostplays.ghosttech.api.module;

import java.util.Optional;
import org.ghostplays.ghosttech.api.util.GhostId;

/**
 * Read-only view of the loaded modules.
 *
 * <p>Modules can inspect each other through this registry, but should prefer
 * {@link ModuleMessaging} over poking at another module's objects directly.
 */
public interface ModuleRegistry {

    /** Every module currently loaded, in load order. */
    java.util.Collection<ModuleHandle> modules();

    /** Looks up a module by its ID, e.g. {@code example}. */
    Optional<ModuleHandle> module(GhostId id);

    /** Convenience lookup returning {@code false} when absent or not {@link ModuleState#ENABLED}. */
    boolean isEnabled(GhostId id);

    /**
     * Finds whichever module registered a content ID, by checking the module namespaces.
     *
     * <p>Given {@code module2:rubysword} this returns the {@code module2} module.
     */
    Optional<ModuleHandle> ownerOf(GhostId contentId);
}