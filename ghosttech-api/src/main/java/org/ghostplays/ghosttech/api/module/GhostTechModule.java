package org.ghostplays.ghosttech.api.module;

/**
 * Entry point every module jar must implement.
 *
 * <p>The plugin instantiates the class named by {@code main} in {@code module.yml} using the
 * module's own classloader, then drives it through this lifecycle:
 *
 * <ol>
 *   <li>{@link #onLoad(ModuleContext)} — register blocks, multiblocks, commands, GUI, permissions.
 *       Safe place to talk to other modules via {@link #onLoad} + {@link ModuleMessaging}.</li>
 *   <li>{@link #onEnable()} — start listeners and behaviour.</li>
 *   <li>{@link #onDisable()} — release resources. Called on shutdown and on reload.</li>
 * </ol>
 *
 * <p>Registration should happen in {@code onLoad}: the plugin reads all content after every module
 * has loaded, so a block registered in {@code onEnable} would be missed by other modules that query
 * it during their own load.
 */
public interface GhostTechModule {

    /**
     * Called once when the module is loaded, before any module is enabled.
     *
     * <p>Register content here: custom blocks, multiblocks, items, GUI definitions, commands,
     * permissions and event listeners.
     *
     * @param context the module context, never {@code null}
     * @throws Exception any failure marks the module as {@link ModuleState#FAILED} and it will not
     *                   be enabled
     */
    void onLoad(ModuleContext context) throws Exception;

    /** Called after every module has loaded. Start behaviour here. */
    default void onEnable() throws Exception {
    }

    /** Called on shutdown and before a reload. Release resources here. */
    default void onDisable() throws Exception {
    }
}