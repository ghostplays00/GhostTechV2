package org.ghostplays.ghosttech.api.module;

/**
 * Lifecycle state of a module inside the {@link ModuleRegistry}.
 *
 * <p>The registry walks modules through these states in order. A module that throws during
 * {@link #ENABLING} is moved to {@link #FAILED} and everything that depended on it is skipped, so
 * one broken module cannot take the server down.
 */
public enum ModuleState {

    /** Found on disk and its {@code module.yml} parsed, but not loaded yet. */
    DISCOVERED,

    /** Class instantiated and {@link GhostTechModule#onLoad} returned successfully. */
    LOADED,

    /** {@link GhostTechModule#onEnable} returned successfully. */
    ENABLED,

    /** {@link GhostTechModule#onDisable} has run, or the module was never enabled. */
    DISABLED,

    /** Loading or enabling threw. Inspect the module logger for the cause. */
    FAILED;

    /** {@code true} while the module may serve content and receive events. */
    public boolean active() {
        return this == ENABLED;
    }
}