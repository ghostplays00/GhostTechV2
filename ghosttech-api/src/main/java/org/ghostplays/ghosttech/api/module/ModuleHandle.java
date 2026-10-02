package org.ghostplays.ghosttech.api.module;

/**
 * A loaded module: its descriptor, current state and lifecycle callbacks.
 *
 * <p>Obtained from {@link ModuleRegistry}. Modules normally only read their own handle, but can
 * inspect others for capability discovery.
 */
public interface ModuleHandle {

    /** The descriptor parsed from {@code module.yml}. */
    ModuleDescriptor descriptor();

    /** The module's current lifecycle state. */
    ModuleState state();

    /** The live module instance. */
    GhostTechModule instance();

    /**
     * Calls {@link GhostTechModule#onEnable()} if the module is currently
     * {@link ModuleState#LOADED}.
     *
     * @return {@code true} if the module transitioned to {@link ModuleState#ENABLED}
     */
    boolean enable();

    /**
     * Calls {@link GhostTechModule#onDisable()} if the module is currently
     * {@link ModuleState#ENABLED}, then unloads its content.
     *
     * @return {@code true} if the module was enabled and is now {@link ModuleState#DISABLED}
     */
    boolean disable();
}