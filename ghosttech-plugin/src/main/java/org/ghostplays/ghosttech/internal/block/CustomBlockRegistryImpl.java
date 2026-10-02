package org.ghostplays.ghosttech.internal.block;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.ghostplays.ghosttech.api.block.CustomBlock;
import org.ghostplays.ghosttech.api.block.CustomBlockRegistry;
import org.ghostplays.ghosttech.api.content.ContentType;
import org.ghostplays.ghosttech.api.util.GhostId;
import org.ghostplays.ghosttech.internal.content.ContentRegistryImpl;
import org.ghostplays.ghosttech.internal.database.Schema;
import org.ghostplays.ghosttech.internal.database.SqliteDatabaseService;

/**
 * {@link CustomBlockRegistry} backed by a SQLite position store.
 *
 * <h2>How custom blocks are tracked</h2>
 * A custom block is a real vanilla block, so the world alone cannot say which blocks are custom.
 * Placed positions are therefore recorded in SQLite, keyed by world and block coordinates, and
 * cached in memory for fast lookups during gameplay.
 *
 * <p>The item form carries its ID in a {@link org.bukkit.persistence.PersistentDataContainer},
 * which travels with the item when it is dropped, picked up, stored in a chest or given to a
 * player. That is how a placed block is recognised as custom in the first place.
 *
 * <h2>Cache consistency</h2>
 * The in-memory cache is authoritative during gameplay and the database is the durable copy. Writes
 * update both: the cache immediately, the database asynchronously. That keeps block interaction
 * instant without making the main thread wait on disk.
 */
public final class CustomBlockRegistryImpl implements CustomBlockRegistry {

    private final Map<GhostId, CustomBlock> definitions = new ConcurrentHashMap<>();

    /** Position key to block ID. Mirrors {@link Schema#TABLE_BLOCKS}. */
    private final Map<String, GhostId> positions = new ConcurrentHashMap<>();

    private final SqliteDatabaseService database;
    private final ContentRegistryImpl content;
    private final Logger logger;
    private final NamespacedKey itemKey;

    /**
     * @param plugin   owning plugin, used for the persistent data key
     * @param database the position store
     * @param content  the shared content registry
     * @param logger   logger for load warnings
     */
    public CustomBlockRegistryImpl(Plugin plugin, SqliteDatabaseService database,
            ContentRegistryImpl content, Logger logger) {
        this.database = database;
        this.content = content;
        this.logger = logger;
        this.itemKey = new NamespacedKey(plugin, "custom_block_id");
    }

    @Override
    public void register(CustomBlock block) {
        if (block.id() == null) {
            throw new IllegalArgumentException("Custom block must have an ID");
        }
        if (block.material() == null || block.material().isAir() || !block.material().isBlock()) {
            throw new IllegalArgumentException(
                    "Custom block " + block.id() + " needs a placeable block material, got "
                            + block.material());
        }
        if (definitions.putIfAbsent(block.id(), block) != null) {
            throw new IllegalArgumentException(
                    "Custom block " + block.id() + " is already registered");
        }
        content.register(ContentType.BLOCK, block.id(), block, block.texture());
    }

    @Override
    public Optional<CustomBlock> block(GhostId id) {
        return Optional.ofNullable(definitions.get(id));
    }

    @Override
    public Collection<CustomBlock> blocks() {
        List<CustomBlock> all = new ArrayList<>(definitions.values());
        all.sort((left, right) -> left.id().compareTo(right.id()));
        return List.copyOf(all);
    }

    @Override
    public boolean isCustomBlock(Location location) {
        return location.getWorld() != null && positions.containsKey(positionKey(location));
    }

    @Override
    public Optional<GhostId> blockIdAt(Location location) {
        if (location.getWorld() == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(positions.get(positionKey(location)));
    }
    @Override
    public ItemStack createItem(CustomBlock block) {
        ItemStack item = new ItemStack(block.material());
        item.editMeta(meta -> {
            meta.displayName(block.displayName() == null
                    ? Component.text(block.id().toString())
                    : block.displayName());
            List<Component> lore = block.lore();
            if (lore != null && !lore.isEmpty()) {
                meta.lore(lore);
            }
            // The ID in persistent data is what identifies the item as a custom block later.
            meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING,
                    block.id().toString());
        });
        return item;
    }

    @Override
    public Optional<GhostId> itemId(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return Optional.empty();
        }
        String raw = item.getItemMeta().getPersistentDataContainer()
                .get(itemKey, PersistentDataType.STRING);
        // parseOrNull returns null for a malformed value, which also becomes an empty optional.
        return raw == null ? Optional.empty() : Optional.ofNullable(GhostId.parseOrNull(raw));
    }

    @Override
    public void track(Location location, GhostId id) {
        if (location.getWorld() == null) {
            return;
        }
        // The cache is updated first so gameplay sees the block immediately.
        positions.put(positionKey(location), id);
        database.execute(
                "INSERT OR REPLACE INTO " + Schema.TABLE_BLOCKS
                        + " (world, x, y, z, block_id, placed_by, placed_at)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?)",
                Schema.positionArgs(location)[0],
                location.getBlockX(), location.getBlockY(), location.getBlockZ(),
                id.toString(), null, System.currentTimeMillis());
    }

    @Override
    public void untrack(Location location) {
        if (location.getWorld() == null) {
            return;
        }
        positions.remove(positionKey(location));
        database.execute("DELETE FROM " + Schema.TABLE_BLOCKS + " WHERE "
                + Schema.positionClause(), Schema.positionArgs(location));
    }

    @Override
    public Optional<CustomBlock> byMaterial(Material material) {
        for (CustomBlock block : definitions.values()) {
            if (block.material() == material) {
                return Optional.of(block);
            }
        }
        return Optional.empty();
    }

    /**
     * Records who placed a block, so it can later be protected.
     *
     * @param location the block position
     * @param player   the placer
     */
    public void setPlacer(Location location, Player player) {
        database.execute("UPDATE " + Schema.TABLE_BLOCKS + " SET placed_by=? WHERE "
                        + Schema.positionClause(),
                player.getUniqueId().toString(),
                Schema.positionArgs(location));
    }
    /**
     * Loads all tracked positions from the database into the cache.
     *
     * <p>Called once during startup and again after a reload.
     *
     * @return a future completing once the cache is warm
     */
    public CompletableFuture<Void> loadAll() {
        return database.query(
                        "SELECT world, x, y, z, block_id FROM " + Schema.TABLE_BLOCKS,
                        row -> new Object[]{
                                row.getString("world"),
                                row.getInt("x"),
                                row.getInt("y"),
                                row.getInt("z"),
                                row.getString("block_id")
                        })
                .thenAccept(rows -> {
                    positions.clear();
                    for (Object[] row : rows) {
                        GhostId id = GhostId.parseOrNull((String) row[4]);
                        if (id == null) {
                            // The owning module may have been removed, so drop the stale row.
                            logger.warning("Dropping tracked block with unknown ID: " + row[4]);
                            continue;
                        }
                        positions.put(rawKey((String) row[0], (Integer) row[1],
                                (Integer) row[2], (Integer) row[3]), id);
                    }
                    logger.info("Loaded " + positions.size() + " tracked custom blocks");
                });
    }

    /**
     * Removes every block a module registered, used when a module is unloaded.
     *
     * @param namespace the module namespace
     * @return how many definitions were removed
     */
    public int unregisterNamespace(String namespace) {
        List<GhostId> owned = definitions.keySet().stream()
                .filter(id -> id.namespace().equals(namespace))
                .toList();
        for (GhostId id : owned) {
            definitions.remove(id);
            content.unregister(ContentType.BLOCK, id);
        }
        // Drop cached positions belonging to removed blocks as well.
        positions.entrySet().removeIf(entry -> entry.getValue().namespace().equals(namespace));
        return owned.size();
    }

    /** Number of blocks currently tracked in the world. */
    public int trackedCount() {
        return positions.size();
    }

    /** The persistent data key holding a custom block ID on items. */
    public NamespacedKey itemKey() {
        return itemKey;
    }

    /**
     * Builds the cache key for a position.
     *
     * @param location the position
     * @return the key
     */
    private static String positionKey(Location location) {
        return rawKey(location.getWorld().getName(), location.getBlockX(),
                location.getBlockY(), location.getBlockZ());
    }

    /**
     * Builds a cache key from raw parts.
     *
     * @param world world name
     * @param x     block X
     * @param y     block Y
     * @param z     block Z
     * @return the key
     */
    private static String rawKey(String world, int x, int y, int z) {
        return world + ":" + x + ":" + y + ":" + z;
    }
}