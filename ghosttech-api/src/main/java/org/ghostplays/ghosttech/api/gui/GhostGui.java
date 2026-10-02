package org.ghostplays.ghosttech.api.gui;

import java.util.List;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * A GUI page built with the Triumph library.
 *
 * <p>Wraps Triumph so modules never import it directly. That keeps the GUI library swappable and
 * means a module compiled against this API keeps working if Triumph is replaced.
 *
 * <p>All methods must be called on the main server thread.
 */
public interface GhostGui {

    /** Rows of content, 1 to 6. */
    int rows();

    /** The current title. */
    Component title();

    /**
     * Places an item at an absolute slot.
     *
     * @param slot slot index, 0-based
     * @param item the item to show
     * @param onClick click handler, {@code null} for a decorative item
     */
    void setItem(int slot, ItemStack item, Consumer<org.bukkit.event.inventory.InventoryClickEvent> onClick);

    /** Places a decorative item, ignoring clicks. */
    void setItem(int slot, ItemStack item);

    /** Appends an item to the first free slot. */
    void addItem(ItemStack item, Consumer<org.bukkit.event.inventory.InventoryClickEvent> onClick);

    /** Fills every empty slot with this item, typically a background pane. */
    void fillEmpty(ItemStack filler);

    /** Registers a handler for a whole row range, e.g. {@code addRowAction(0, 9, handler)}. */
    void addSlotAction(int fromSlot, int toSlot,
            Consumer<org.bukkit.event.inventory.InventoryClickEvent> handler);

    /** Blocks placing, taking, swapping and dropping items, the default for menu GUIs. */
    GhostGui lock();

    /** Opens this GUI for a player. */
    void open(Player player);

    /** Closes the GUI for a player. */
    void close(Player player);

    /**
     * Runs a callback when the player closes the GUI.
     *
     * <p>This is where per-player state, such as an edited storage inventory, is persisted.
     *
     * @param callback receives the player
     */
    void onClose(Consumer<Player> callback);

    /**
     * A paginated GUI.
     *
     * <p>Items added through {@link #addItem} flow into pages automatically, and navigation
     * buttons are inserted at the bottom row.
     */
    interface Paginated extends GhostGui {

        /** Adds an item to the current page. */
        void addPageItem(ItemStack item, Consumer<org.bukkit.event.inventory.InventoryClickEvent> onClick);

        /** Jumps to a page, clamped to the valid range. */
        void openPage(Player player, int page);

        /** Total number of pages, at least 1. */
        int pages();

        /** The page currently shown, 1-based. */
        int currentPage();

        /**
         * Replaces the whole content and resets to page 1.
         *
         * @param items the new content, already paginated by the caller
         */
        void setContent(List<ItemStack> items);
    }

    /** A GUI backed by a persistent storage inventory, e.g. a player backpack. */
    interface Storage extends GhostGui {

        /**
         * Loads the stored contents and opens the GUI.
         *
         * <p>Implementations read from the SQLite storage table, so contents survive restarts.
         */
        void openStored(Player player, org.ghostplays.ghosttech.api.util.GhostId storageId);

        /**
         * Reads the current contents back out.
         *
         * <p>Call before persisting so in-flight player edits are captured.
         */
        List<ItemStack> contents();

        /** Persists the current contents under the given ID. */
        void save(org.ghostplays.ghosttech.api.util.GhostId storageId);
    }
}