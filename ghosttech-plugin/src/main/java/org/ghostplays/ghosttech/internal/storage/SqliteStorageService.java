package org.ghostplays.ghosttech.internal.storage;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.bukkit.inventory.ItemStack;
import org.ghostplays.ghosttech.api.storage.StorageService;
import org.ghostplays.ghosttech.api.util.GhostId;
import org.ghostplays.ghosttech.internal.database.Schema;
import org.ghostplays.ghosttech.internal.database.SqliteDatabaseService;
import org.ghostplays.ghosttech.internal.util.ItemSerializer;

/**
 * SQLite-backed {@link StorageService}.
 *
 * <p>Inventories are stored as one Base64 blob per storage. That keeps the schema stable regardless
 * of which items exist, and avoids a join per slot.
 *
 * <p>Writes are asynchronous. Callers that need the data to be visible to a following read, such as
 * save-then-reload in a GUI, should chain the returned futures rather than assuming ordering.
 */
public final class SqliteStorageService implements StorageService {

    /** Slot count used when a storage is created without an explicit size. */
    private static final int DEFAULT_SLOTS = 54;

    private final SqliteDatabaseService database;

    /**
     * @param database the plugin database
     */
    public SqliteStorageService(SqliteDatabaseService database) {
        this.database = database;
    }

    @Override
    public CompletableFuture<Optional<List<ItemStack>>> load(GhostId storageId) {
        return database.queryOne(
                "SELECT contents, slots FROM " + Schema.TABLE_STORAGE + " WHERE storage_id=?",
                row -> (List<ItemStack>) ItemSerializer.deserialize(
                        row.getString("contents"), row.getInt("slots")),
                storageId.toString())
                // queryOne yields null for a missing row, which becomes an empty optional.
                .thenApply(found -> Optional.ofNullable(found));
    }

    @Override
    public void save(GhostId storageId, List<ItemStack> contents) {
        List<ItemStack> safeContents = contents == null ? List.of() : contents;
        String encoded = ItemSerializer.serialize(safeContents);
        database.execute(
                "INSERT OR REPLACE INTO " + Schema.TABLE_STORAGE
                        + " (storage_id, owner, contents, slots, updated_at) VALUES (?,"
                        + " (SELECT owner FROM " + Schema.TABLE_STORAGE + " WHERE storage_id=?),"
                        + " ?, ?, ?)",
                storageId.toString(),
                storageId.toString(),
                encoded,
                safeContents.size(),
                System.currentTimeMillis());
    }

    @Override
    public CompletableFuture<Optional<UUID>> owner(GhostId storageId) {
        return database.queryOne(
                "SELECT owner FROM " + Schema.TABLE_STORAGE + " WHERE storage_id=?",
                row -> {
                    String raw = row.getString("owner");
                    return raw == null || raw.isBlank() ? null : UUID.fromString(raw);
                },
                storageId.toString())
                .thenApply(found -> Optional.ofNullable(found));
    }

    @Override
    public void setOwner(GhostId storageId, UUID owner) {
        database.execute(
                "INSERT OR REPLACE INTO " + Schema.TABLE_STORAGE
                        + " (storage_id, owner, contents, slots, updated_at) VALUES (?, ?, NULL, ?, ?)",
                storageId.toString(),
                owner == null ? null : owner.toString(),
                DEFAULT_SLOTS,
                System.currentTimeMillis());
    }

    @Override
    public CompletableFuture<List<GhostId>> storagesInNamespace(String namespace) {
        String prefix = namespace + ":";
        return database.query(
                "SELECT storage_id FROM " + Schema.TABLE_STORAGE + " WHERE storage_id LIKE ?",
                row -> GhostId.parseOrNull(row.getString("storage_id")),
                prefix + "%")
                .thenApply(ids -> ids.stream()
                        .filter(java.util.Objects::nonNull)
                        .toList());
    }

    @Override
    public CompletableFuture<Boolean> delete(GhostId storageId) {
        return database.execute(
                "DELETE FROM " + Schema.TABLE_STORAGE + " WHERE storage_id=?",
                storageId.toString())
                .thenApply(affected -> affected > 0);
    }

    @Override
    public int count() {
        // Counting synchronously would block the main thread on the database thread. Callers needing a
        // real number should use countAsync, which is safe from anywhere.
        return 0;
    }

    /**
     * Counts stored inventories asynchronously.
     *
     * <p>{@link #count()} cannot block the main thread, so this is the variant callers should use
     * when they need an accurate figure.
     *
     * @return a future completing with the number of storages
     */
    public CompletableFuture<Integer> countAsync() {
        return database.queryOne(
                "SELECT COUNT(*) AS total FROM " + Schema.TABLE_STORAGE,
                row -> row.getInt("total"))
                .thenApply(total -> total == null ? 0 : total);
    }

    /**
     * Creates an empty storage with a given slot count, if it does not exist yet.
     *
     * <p>Used by GUIs that want a fixed size to be present before the first save.
     *
     * @param storageId the storage to create
     * @param slots     the slot count to record
     * @return a future completing with {@code true} when a row was created
     */
    public CompletableFuture<Boolean> createIfAbsent(GhostId storageId, int slots) {
        return database.execute(
                "INSERT OR IGNORE INTO " + Schema.TABLE_STORAGE
                        + " (storage_id, owner, contents, slots, updated_at) VALUES (?, NULL, NULL, ?, ?)",
                storageId.toString(), slots, System.currentTimeMillis())
                .thenApply(affected -> affected > 0);
    }

    /**
     * Pads a contents list to a slot count with nulls.
     *
     * <p>Stored layouts should be a fixed size so slot indices stay meaningful across restarts.
     *
     * @param contents the current contents
     * @param slots    the desired length
     * @return the padded list
     */
    public static List<ItemStack> pad(List<ItemStack> contents, int slots) {
        List<ItemStack> padded = new ArrayList<>(contents == null ? List.of() : contents);
        while (padded.size() < slots) {
            padded.add(null);
        }
        return padded;
    }
}