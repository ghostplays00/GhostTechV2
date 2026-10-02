package org.ghostplays.ghosttech.internal.gui;

import dev.triumphteam.gui.guis.GuiItem;
import dev.triumphteam.gui.guis.StorageGui;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.ghostplays.ghosttech.api.gui.GhostGui;
import org.ghostplays.ghosttech.api.storage.StorageService;
import org.ghostplays.ghosttech.api.util.GhostId;

/**
 * {@link GhostGui.Storage} backed by a Triumph storage GUI and SQLite.
 *
 * <p>Contents are loaded when the GUI opens and written back when it closes. Because the storage is
 * read on the database thread and applied on the main thread, opening is asynchronous: the GUI is
 * populated as soon as the read resolves.
 */
public final class TriumphStorageGui implements GhostGui.Storage {

    private final StorageGui gui;
    private final StorageService storageService;
    private final int slots;
    private GhostId storageId;

    /**
     * @param gui            the Triumph storage GUI
     * @param storageService used to load and persist contents
     * @param slots          the number of slots, i.e. rows times nine
     */
    public TriumphStorageGui(StorageGui gui, StorageService storageService, int slots) {
        this.gui = gui;
        this.storageService = storageService;
        this.slots = slots;
    }

    @Override
    public int rows() {
        return slots / 9;
    }

    @Override
    public Component title() {
        return gui.title();
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
        gui.setItem(gui.getInventory().firstEmpty(), TriumphChestGui.toGuiItem(item, onClick));
    }

    @Override
    public void fillEmpty(ItemStack filler) {
        // Deliberately not filling a storage GUI: empty slots must stay empty so players can store
        // items in them.
    }

    @Override
    public void addSlotAction(int fromSlot, int toSlot, Consumer<InventoryClickEvent> handler) {
        gui.addSlotAction(fromSlot, toSlot, TriumphChestGui.toGuiAction(handler));
    }

    @Override
    public GhostGui lock() {
        // Storage GUIs are intentionally not locked: the contents are the point of the GUI.
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

    @Override
    public void openStored(Player player, GhostId storageId) {
        this.storageId = storageId;
        GhostId id = storageId;

        // Persist on close so the player's latest edits are never lost, including on a clean quit.
        onClose(closingPlayer -> save(id));

        storageService.load(id).thenAccept(loaded -> org.bukkit.Bukkit.getScheduler()
                .runTask(org.bukkit.Bukkit.getPluginManager().getPlugin("GhostTechV2"), () -> {
                    if (!player.isOnline()) {
                        return;
                    }
                    List<ItemStack> contents = loaded.orElse(List.of());
                    if (contents.isEmpty()) {
                        gui.open(player);
                        return;
                    }
                    // addItem returns where each stack landed, which is what makes loading an
                    // arbitrary saved layout possible without tracking slot indices.
                    gui.addItem(contents);
                    gui.open(player);
                }));
    }

    @Override
    public List<ItemStack> contents() {
        List<ItemStack> contents = new ArrayList<>(slots);
        for (int slot = 0; slot < slots; slot++) {
            ItemStack item = gui.getInventory().getItem(slot);
            contents.add(item == null ? null : item.clone());
        }
        return contents;
    }

    @Override
    public void save(GhostId storageId) {
        storageService.save(storageId, contents());
    }

    /** The storage this GUI last loaded from, for diagnostics. */
    public GhostId storageId() {
        return storageId;
    }
}