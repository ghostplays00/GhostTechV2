package org.ghostplays.ghosttech.internal.gui;

import dev.triumphteam.gui.components.GuiAction;
import dev.triumphteam.gui.guis.BaseGui;
import dev.triumphteam.gui.guis.Gui;
import dev.triumphteam.gui.guis.GuiItem;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.ghostplays.ghosttech.api.gui.GhostGui;

/**
 * {@link GhostGui} backed by a Triumph chest GUI.
 *
 * <p>Keeps the Triumph types entirely inside this package: modules only ever see {@link GhostGui},
 * so the underlying library can be swapped without recompiling them.
 */
public final class TriumphChestGui implements GhostGui {

    private final Gui gui;

    /**
     * @param gui the Triumph GUI to delegate to
     */
    public TriumphChestGui(Gui gui) {
        this.gui = gui;
    }

    @Override
    public int rows() {
        return gui.getInventory().getSize() / 9;
    }

    @Override
    public Component title() {
        return gui.title();
    }

    @Override
    public void setItem(int slot, ItemStack item, Consumer<InventoryClickEvent> onClick) {
        gui.setItem(slot, toGuiItem(item, onClick));
    }

    @Override
    public void setItem(int slot, ItemStack item) {
        gui.setItem(slot, new GuiItem(item));
    }

    @Override
    public void addItem(ItemStack item, Consumer<InventoryClickEvent> onClick) {
        gui.addItem(toGuiItem(item, onClick));
    }

    @Override
    public void fillEmpty(ItemStack filler) {
        // A single setItem per empty slot is simpler and safer than relying on an inventory filler,
        // and it keeps behaviour identical across Triumph versions.
        for (int slot = 0; slot < gui.getInventory().getSize(); slot++) {
            if (gui.getInventory().getItem(slot) == null) {
                gui.setItem(slot, new GuiItem(filler));
            }
        }
    }

    @Override
    public void addSlotAction(int fromSlot, int toSlot, Consumer<InventoryClickEvent> handler) {
        gui.addSlotAction(fromSlot, toSlot, toGuiAction(handler));
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
        // Triumph exposes close handling through a single action rather than a listener method, so
        // this replaces any previously installed close handler.
        gui.setCloseGuiAction(event -> {
            if (event.getPlayer() instanceof Player player) {
                callback.accept(player);
            }
        });
    }

    /**
     * Wraps an item and optional click handler into a Triumph GUI item.
     *
     * @param item    the item to display
     * @param onClick click handler, may be {@code null} for a decorative item
     * @return the Triumph item
     */
    static GuiItem toGuiItem(ItemStack item, Consumer<InventoryClickEvent> onClick) {
        if (onClick == null) {
            return new GuiItem(item);
        }
        return new GuiItem(item, toGuiAction(onClick));
    }

    /**
     * Wraps a consumer into a Triumph action.
     *
     * <p>Clicks are cancelled by default on menus: a player must never be able to pull an item out
     * of a display GUI or shift-click items into it.
     *
     * @param handler the handler to invoke
     * @return the Triumph action
     */
    static GuiAction<InventoryClickEvent> toGuiAction(Consumer<InventoryClickEvent> handler) {
        return event -> {
            event.setCancelled(true);
            handler.accept(event);
        };
    }

    /** The underlying Triumph GUI, used by subclasses that need direct access. */
    protected BaseGui base() {
        return gui;
    }

    /** Reads the current contents as a list, padding to the GUI size with nulls. */
    public List<ItemStack> snapshot() {
        int size = gui.getInventory().getSize();
        List<ItemStack> contents = new ArrayList<>(size);
        for (int slot = 0; slot < size; slot++) {
            ItemStack item = gui.getInventory().getItem(slot);
            contents.add(item == null ? null : item.clone());
        }
        return contents;
    }
}