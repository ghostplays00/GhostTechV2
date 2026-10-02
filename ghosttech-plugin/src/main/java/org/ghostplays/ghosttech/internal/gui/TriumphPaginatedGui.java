package org.ghostplays.ghosttech.internal.gui;

import dev.triumphteam.gui.guis.GuiItem;
import dev.triumphteam.gui.guis.PaginatedGui;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.ghostplays.ghosttech.api.gui.GhostGui;

/**
 * {@link GhostGui.Paginated} backed by a Triumph paginated GUI.
 *
 * <p>Items added here flow into pages automatically. The navigation row sits at the bottom, which
 * is why the usable page size is the total slots minus nine.
 */
public final class TriumphPaginatedGui implements GhostGui.Paginated {

    private static final int NAVIGATION_ROW = 9;

    private final PaginatedGui gui;
    private final int rows;
    private final int pageSize;

    /**
     * @param gui      the Triumph GUI to delegate to
     * @param rows     total rows including the navigation row
     * @param pageSize items per page, excluding the navigation row
     */
    public TriumphPaginatedGui(PaginatedGui gui, int rows, int pageSize) {
        this.gui = gui;
        this.rows = rows;
        this.pageSize = pageSize;
    }

    @Override
    public int rows() {
        return rows;
    }

    @Override
    public Component title() {
        return gui.title();
    }

    @Override
    public void addPageItem(ItemStack item, Consumer<InventoryClickEvent> onClick) {
        gui.addItem(TriumphChestGui.toGuiItem(item, onClick));
    }

    @Override
    public void openPage(Player player, int page) {
        // Page numbers are clamped rather than rejected so a stale page index cannot throw.
        int target = Math.max(1, Math.min(page, gui.getPagesNum()));
        gui.open(player, target - 1);
    }

    @Override
    public int pages() {
        return gui.getPagesNum();
    }

    @Override
    public int currentPage() {
        return gui.getCurrentPageNum() + 1;
    }

    @Override
    public void setContent(List<ItemStack> items) {
        gui.clearPageItems();
        for (ItemStack item : items == null ? List.<ItemStack>of() : items) {
            gui.addItem(new GuiItem(item));
        }
        gui.update();
    }

    @Override
    public void setItem(int slot, ItemStack item, Consumer<InventoryClickEvent> onClick) {
        gui.setItem(slot, TriumphChestGui.toGuiItem(item, onClick));
    }

    @Override
    public void setItem(int slot, ItemStack item) {
        gui.setItem(slot, new GuiItem(item));
    }

    @Override
    public void addItem(ItemStack item, Consumer<InventoryClickEvent> onClick) {
        gui.addItem(TriumphChestGui.toGuiItem(item, onClick));
    }

    @Override
    public void fillEmpty(ItemStack filler) {
        for (int slot = 0; slot < gui.getInventory().getSize(); slot++) {
            if (gui.getInventory().getItem(slot) == null) {
                gui.setItem(slot, new GuiItem(filler));
            }
        }
    }

    @Override
    public void addSlotAction(int fromSlot, int toSlot, Consumer<InventoryClickEvent> handler) {
        gui.addSlotAction(fromSlot, toSlot, TriumphChestGui.toGuiAction(handler));
    }

    @Override
    public GhostGui lock() {
        gui.disableAllInteractions();
        return this;
    }

    @Override
    public void open(Player player) {
        gui.open(player);
    }

    @Override
    public void close(Player player) {
        gui.close(player);
    }

    @Override
    public void onClose(Consumer<Player> callback) {
        gui.setCloseGuiAction(event -> {
            if (event.getPlayer() instanceof Player player) {
                callback.accept(player);
            }
        });
    }

    /**
     * Builds the previous / next navigation row.
     *
     * <p>Slot indices are absolute, calculated from the GUI size, so the buttons land correctly for
     * any row count.
     *
     * @param previousHandler invoked when the previous button is clicked
     * @param nextHandler     invoked when the next button is clicked
     */
    public void buildNavigation(Consumer<InventoryClickEvent> previousHandler,
            Consumer<InventoryClickEvent> nextHandler) {
        int base = gui.getInventory().getSize() - NAVIGATION_ROW;
        gui.setItem(base, new GuiItem(navigationItem(Material.ARROW, "&aPrevious page"),
                TriumphChestGui.toGuiAction(previousHandler)));
        gui.setItem(base + NAVIGATION_ROW - 1, new GuiItem(
                navigationItem(Material.ARROW, "&aNext page"),
                TriumphChestGui.toGuiAction(nextHandler)));
    }

    /**
     * Creates a navigation item.
     *
     * @param material the icon material
     * @param label    MiniMessage label
     * @return the item
     */
    public static ItemStack navigationItem(Material material, String label) {
        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> meta.displayName(
                org.ghostplays.ghosttech.internal.util.Messages.parse(label)));
        return item;
    }

    /** The page size this GUI was configured with. */
    public int pageSize() {
        return pageSize;
    }

    /** Reads the currently displayed page's items. */
    public List<ItemStack> currentPageItems() {
        List<ItemStack> items = new ArrayList<>();
        gui.getCurrentPageItems().forEach((slot, item) -> items.add(item.getItemStack()));
        return items;
    }
}