package org.ghostplays.ghosttech.internal.gui;

import dev.triumphteam.gui.guis.Gui;
import dev.triumphteam.gui.guis.PaginatedGui;
import dev.triumphteam.gui.guis.StorageGui;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import org.bukkit.inventory.ItemStack;
import org.ghostplays.ghosttech.api.gui.GhostGui;
import org.ghostplays.ghosttech.api.gui.GuiFactory;
import org.ghostplays.ghosttech.api.storage.StorageService;

/**
 * {@link GuiFactory} backed by Triumph.
 *
 * <p>Row counts are clamped to the 1-6 range a chest inventory allows, rather than throwing, so a
 * module computing a row count from content cannot break a GUI.
 */
public final class TriumphGuiFactory implements GuiFactory {

    private static final int MAX_ROWS = 6;
    private static final int SLOTS_PER_ROW = 9;

    private final StorageService storageService;

    /**
     * @param storageService handed to storage GUIs so they can persist their contents
     */
    public TriumphGuiFactory(StorageService storageService) {
        this.storageService = storageService;
    }

    @Override
    public GhostGui chest(int rows, Component title, Consumer<GhostGui> configurator) {
        int safeRows = clampRows(rows);
        Gui gui = Gui.gui().title(title).rows(safeRows).create();
        TriumphChestGui wrapped = new TriumphChestGui(gui);
        if (configurator != null) {
            configurator.accept(wrapped);
        }
        return wrapped;
    }

    @Override
    public GhostGui.Paginated paginated(int rows, int pageSize, Component title,
            Consumer<GhostGui.Paginated> configurator) {
        int safeRows = clampRows(rows);
        // A page must hold at least one item, otherwise pagination cannot make progress.
        int capacity = safeRows * SLOTS_PER_ROW;
        int safePageSize = Math.max(1, Math.min(pageSize, capacity));

        PaginatedGui gui = Gui.paginated().title(title).rows(safeRows).pageSize(safePageSize).create();
        TriumphPaginatedGui wrapped = new TriumphPaginatedGui(gui, safeRows, safePageSize);
        if (configurator != null) {
            configurator.accept(wrapped);
        }
        return wrapped;
    }

    @Override
    public GhostGui.Storage storage(int rows, Component title, Consumer<GhostGui.Storage> configurator) {
        int safeRows = clampRows(rows);
        StorageGui gui = Gui.storage().title(title).rows(safeRows).create();
        TriumphStorageGui wrapped = new TriumphStorageGui(gui, storageService, safeRows * SLOTS_PER_ROW);
        if (configurator != null) {
            configurator.accept(wrapped);
        }
        return wrapped;
    }

    /**
     * Convenience for building a paginated GUI with navigation buttons already wired.
     *
     * @param rows     total rows including the navigation row
     * @param pageSize items per page
     * @param title    the window title
     * @return the configured GUI, with working previous and next buttons
     */
    public TriumphPaginatedGui paginatedWithNavigation(int rows, int pageSize, Component title) {
        TriumphPaginatedGui[] holder = new TriumphPaginatedGui[1];
        holder[0] = (TriumphPaginatedGui) paginated(rows, pageSize, title, null);
        holder[0].buildNavigation(
                event -> holder[0].close((org.bukkit.entity.Player) event.getWhoClicked()),
                event -> holder[0].close((org.bukkit.entity.Player) event.getWhoClicked()));
        return holder[0];
    }

    /** Clamps a requested row count into the 1-6 range chest inventories allow. */
    private static int clampRows(int rows) {
        return Math.max(1, Math.min(rows, MAX_ROWS));
    }

    /** Builds a plain filler item, exposed for module convenience. */
    public static ItemStack filler(org.bukkit.Material material, String name) {
        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> meta.displayName(
                org.ghostplays.ghosttech.internal.util.Messages.parse(name)));
        return item;
    }
}