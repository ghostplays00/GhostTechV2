package org.ghostplays.ghosttech.api.gui;

import net.kyori.adventure.text.Component;

/**
 * Factory for the GUI types the plugin exposes.
 *
 * <p>Guessing arguments through {@code Consumer} means a module can configure an inline GUI in one
 * statement, while a module that wants to reuse a template can hold onto the created GUI and
 * configure it over several calls.
 */
public interface GuiFactory {

    /**
     * Creates a chest-style GUI.
     *
     * @param rows 1 to 6
     * @param title the window title
     * @param configurator optional post-creation configuration, may be {@code null}
     */
    GhostGui chest(int rows, Component title, java.util.function.Consumer<GhostGui> configurator);

    /** Creates a chest-style GUI with no configurator. */
    default GhostGui chest(int rows, Component title) {
        return chest(rows, title, null);
    }

    /**
     * Creates a paginated GUI with navigation controls.
     *
     * @param rows     total rows including the navigation row
     * @param pageSize items per page
     * @param title    the window title
     */
    GhostGui.Paginated paginated(int rows, int pageSize, Component title,
            java.util.function.Consumer<GhostGui.Paginated> configurator);

    /** Creates a paginated GUI with no configurator. */
    default GhostGui.Paginated paginated(int rows, int pageSize, Component title) {
        return paginated(rows, pageSize, title, null);
    }

    /**
     * Creates a persistent storage GUI.
     *
     * <p>The contents are read from and written to SQLite, so this survives restarts and server
     * moves. Use for backpacks, machine inventories and shared team storage.
     *
     * @param rows 1 to 6
     * @param title the window title
     */
    GhostGui.Storage storage(int rows, Component title,
            java.util.function.Consumer<GhostGui.Storage> configurator);

    /** Creates a storage GUI with no configurator. */
    default GhostGui.Storage storage(int rows, Component title) {
        return storage(rows, title, null);
    }
}