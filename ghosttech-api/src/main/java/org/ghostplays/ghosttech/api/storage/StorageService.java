package org.ghostplays.ghosttech.api.storage;

import java.util.List;
import java.util.Optional;
import org.bukkit.inventory.ItemStack;
import org.ghostplays.ghosttech.api.util.GhostId;

/**
 * Persistent inventory storage backed by SQLite.
 *
 * <p>Used for player backpacks, machine inventories and shared team storage. Contents are
 * serialized with Bukkit's own stream format, so any item including ones from other plugins round
 * trips faithfully.
 *
 * <p>Items are stored as serialized blobs rather than as SQL columns, which keeps the schema stable
 * no matter what item types exist. Reads and writes go through the async executor, so large
 * inventories do not stall the main thread.
 */
public interface StorageService {

    /**
     * Loads the contents of a storage.
     *
     * <p>Asynchronous by design: the database runs on its own thread, so blocking here would freeze
     * the server. The future completes on that thread, so hop to the main thread before touching
     * the world or opening a GUI.
     *
     * @param storageId the storage ID, namespaced per module, e.g. {@code module1:backpack}
     * @return a future completing with the stored slots, or an empty optional when the storage does
     *         not exist yet
     */
    java.util.concurrent.CompletableFuture<java.util.Optional<java.util.List<ItemStack>>> load(
            GhostId storageId);

    /**
     * Saves a storage, replacing whatever was there.
     *
     * @param storageId the storage ID
     * @param contents  the slots to store; {@code null} entries are stored as empty
     */
    void save(GhostId storageId, List<ItemStack> contents);

    /**
     * The player who owns a storage, when it is a personal one.
     *
     * @param storageId the storage ID
     * @return a future completing with the owner, or an empty optional for shared storages
     */
    java.util.concurrent.CompletableFuture<java.util.Optional<java.util.UUID>> owner(GhostId storageId);

    /**
     * Attaches an owner to a storage, so only that player can open it.
     *
     * <p>Called on first use by personal storage GUIs.
     */
    void setOwner(GhostId storageId, java.util.UUID owner);

    /** Every storage ID belonging to a namespace, e.g. all of a module's backpacks. */
    java.util.concurrent.CompletableFuture<java.util.List<GhostId>> storagesInNamespace(
            String namespace);

    /**
     * Deletes a storage entirely.
     *
     * @param storageId the storage ID
     * @return a future completing with {@code true} once a row was removed
     */
    java.util.concurrent.CompletableFuture<Boolean> delete(GhostId storageId);

    /** Total number of stored inventories. */
    int count();

    /**
     * Builds a namespaced player storage ID.
     *
     * <p>Convenience: {@code playerStorage("module1", "backpack", playerId)} produces
     * {@code module1:backpack_<uuid>}.
     *
     * @param namespace the module namespace
     * @param name      the storage name
     * @param playerId  the owning player
     */
    static GhostId playerStorage(String namespace, String name, java.util.UUID playerId) {
        return GhostId.parse(namespace + ":" + name + "_" + playerId);
    }
}