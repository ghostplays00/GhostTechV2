package org.ghostplays.ghosttech.internal.database;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.ghostplays.ghosttech.api.util.GhostId;

/**
 * The plugin's database schema.
 *
 * <p>All tables are created with {@code CREATE TABLE IF NOT EXISTS} on startup, so a fresh install
 * needs no separate migration step.
 *
 * <h2>Why custom blocks are stored in SQL</h2>
 * Vanilla blocks such as stone or dirt are not tile entities, so they have no
 * {@code PersistentDataContainer}. Storing custom block identity per position in SQLite is the only
 * way to make arbitrary vanilla materials behave as custom blocks. Item forms do use the item's
 * persistent data, which travels with the item normally.
 */
public final class Schema {

    /** Positions of custom blocks placed in the world. */
    public static final String TABLE_BLOCKS = "ghosttech_blocks";

    /** Formed multiblock instances, one row per instance anchor. */
    public static final String TABLE_MULTIBLOCKS = "ghosttech_multiblocks";

    /** Which pattern key each block of a multiblock occupies, so routing survives restarts. */
    public static final String TABLE_MULTIBLOCK_PARTS = "ghosttech_multiblock_parts";

    /** Persistent inventory storage. */
    public static final String TABLE_STORAGE = "ghosttech_storage";

    /** Directly granted permission nodes per player. */
    public static final String TABLE_PERMISSION_GRANTS = "ghosttech_permission_grants";

    /** Group membership per player. */
    public static final String TABLE_PERMISSION_GROUPS = "ghosttech_permission_groups";

    /** Registered modules and their state, so a broken module is reported rather than silently retried. */
    public static final String TABLE_MODULES = "ghosttech_modules";

    /**
     * Creates every table the plugin owns.
     *
     * @param database the database to initialise
     * @return a future completing once all tables exist
     */
    public static CompletableFuture<Void> create(SqliteDatabaseService database) {
        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);

        chain = chain.thenCompose(ignored -> database.createTable(TABLE_BLOCKS, """
                world       TEXT    NOT NULL,
                x           INTEGER NOT NULL,
                y           INTEGER NOT NULL,
                z           INTEGER NOT NULL,
                block_id    TEXT    NOT NULL,
                placed_by   TEXT,
                placed_at   INTEGER NOT NULL,
                PRIMARY KEY (world, x, y, z)
                """));

        chain = chain.thenCompose(ignored -> database.createTable(TABLE_MULTIBLOCKS, """
                world          TEXT    NOT NULL,
                x              INTEGER NOT NULL,
                y              INTEGER NOT NULL,
                z              INTEGER NOT NULL,
                multiblock_id  TEXT    NOT NULL,
                formed_at      INTEGER NOT NULL,
                PRIMARY KEY (world, x, y, z)
                """));

        chain = chain.thenCompose(ignored -> database.createTable(TABLE_MULTIBLOCK_PARTS, """
                world          TEXT    NOT NULL,
                x              INTEGER NOT NULL,
                y              INTEGER NOT NULL,
                z              INTEGER NOT NULL,
                multiblock_id  TEXT    NOT NULL,
                part_key       TEXT,
                PRIMARY KEY (world, x, y, z)
                """));

        chain = chain.thenCompose(ignored -> database.createTable(TABLE_STORAGE, """
                storage_id  TEXT    PRIMARY KEY,
                owner       TEXT,
                contents    TEXT,
                slots       INTEGER NOT NULL DEFAULT 54,
                updated_at  INTEGER NOT NULL
                """));

        chain = chain.thenCompose(ignored -> database.createTable(TABLE_PERMISSION_GRANTS, """
                player_uuid  TEXT NOT NULL,
                node         TEXT NOT NULL,
                granted_at   INTEGER NOT NULL,
                PRIMARY KEY (player_uuid, node)
                """));

        chain = chain.thenCompose(ignored -> database.createTable(TABLE_PERMISSION_GROUPS, """
                player_uuid  TEXT NOT NULL,
                group_name   TEXT NOT NULL,
                assigned_at  INTEGER NOT NULL,
                PRIMARY KEY (player_uuid, group_name)
                """));

        chain = chain.thenCompose(ignored -> database.createTable(TABLE_MODULES, """
                module_id   TEXT    PRIMARY KEY,
                name        TEXT    NOT NULL,
                version     TEXT    NOT NULL,
                state       TEXT    NOT NULL,
                jar_name    TEXT,
                loaded_at   INTEGER NOT NULL
                """));

        return chain;
    }
    /**
     * Converts a location into the columns shared by the position tables.
     *
     * @param location the world position
     * @return world name and block coordinates, in the order the schema expects
     */
    public static Object[] positionArgs(Location location) {
        World world = location.getWorld();
        return new Object[]{
                world == null ? "unknown" : world.getName(),
                location.getBlockX(),
                location.getBlockY(),
                location.getBlockZ()
        };
    }

    /**
     * The WHERE clause matching a position, paired with {@link #positionArgs}.
     *
     * @return the clause, e.g. {@code "world=? AND x=? AND y=? AND z=?"}
     */
    public static String positionClause() {
        return "world=? AND x=? AND y=? AND z=?";
    }

    /** Columns in {@link #positionArgs} order, for use in an INSERT. */
    public static String positionColumns() {
        return "world, x, y, z";
    }

    /**
     * Serialises a namespaced ID for storage.
     *
     * @param id the ID
     * @return its string form
     */
    public static String encode(GhostId id) {
        return id.toString();
    }

    /**
     * Reads a namespaced ID back from storage.
     *
     * @param raw the stored value
     * @return the parsed ID, or {@code null} when absent or malformed
     */
    public static GhostId decode(String raw) {
        return raw == null ? null : GhostId.parseOrNull(raw);
    }

    /**
     * Schedules a callback on the main thread once a database future resolves.
     *
     * <p>Results that touch the world, such as a block ID read, must be applied on the main thread.
     * Centralising that keeps the requirement from being forgotten at individual call sites.
     *
     * @param future the database future
     * @param action the action to run on the main thread
     * @param <T>    the value type
     * @return a future completing after the action ran
     */
    public static <T> CompletableFuture<T> onMain(CompletableFuture<T> future,
            java.util.function.Consumer<T> action) {
        org.bukkit.plugin.Plugin plugin = Bukkit.getPluginManager().getPlugin("GhostTechV2");
        // thenAccept returns a future of the consumer's type, so it is completed manually rather
        // than relying on the composed future's inferred type parameter.
        CompletableFuture<T> result = new CompletableFuture<>();
        future.whenComplete((value, throwable) -> {
            if (throwable != null) {
                result.completeExceptionally(throwable);
                return;
            }
            if (plugin != null && plugin.isEnabled()) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    action.accept(value);
                    result.complete(value);
                });
            } else {
                action.accept(value);
                result.complete(value);
            }
        });
        return result;
    }

    /** Every table the plugin owns, used by diagnostics. */
    public static List<String> tables() {
        return List.of(
                TABLE_BLOCKS,
                TABLE_MULTIBLOCKS,
                TABLE_MULTIBLOCK_PARTS,
                TABLE_STORAGE,
                TABLE_PERMISSION_GRANTS,
                TABLE_PERMISSION_GROUPS,
                TABLE_MODULES);
    }

    private Schema() {
    }
}