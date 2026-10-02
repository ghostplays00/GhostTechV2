package org.ghostplays.ghosttech.api.database;

import java.sql.Connection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * The plugin's SQLite database.
 *
 * <p>The schema is owned by the plugin and created on first run. Modules get their own tables
 * through {@link #execute(String, Object...)} and can namespace them, e.g.
 * {@code module1_blocks}. Everything a module persists goes through here rather than opening its
 * own connection, which keeps the database to one writer and avoids lock contention.
 *
 * <p>All write methods are asynchronous and return a future, because SQLite writes must not happen
 * on the main server thread. Reads that touch the main thread state are also asynchronous for the
 * same reason.
 *
 * <p>Values are bound with {@link PreparedStatement} parameter placeholders, never string
 * concatenation, so module code cannot introduce SQL injection.
 */
public interface DatabaseService {

    /**
     * Runs an arbitrary statement.
     *
     * <p>Use {@code ?} placeholders for every value: {@code execute("INSERT INTO t VALUES (?, ?)", a, b)}.
     *
     * @param sql    the statement
     * @param params bound parameters, may be empty
     * @return a future completing with the number of affected rows
     */
    CompletableFuture<Integer> execute(String sql, Object... params);

    /**
     * Runs a query and maps each row.
     *
     * <p>Example:
     * <pre>{@code
     * database.query("SELECT name FROM blocks WHERE id = ?",
     *         row -> row.getString("name"),
     *         blockId);
     * }</pre>
     *
     * @param sql     the query
     * @param mapper  converts a row to a result value
     * @param params  bound parameters
     * @param <T>     the mapped type
     * @return a future completing with every mapped row, empty list when there are none
     */
    <T> CompletableFuture<List<T>> query(String sql, RowMapper<T> mapper, Object... params);

    /**
     * Runs a query expected to match at most one row.
     *
     * @return a future completing with the mapped row, or {@code null} when there is none
     */
    <T> CompletableFuture<T> queryOne(String sql, RowMapper<T> mapper, Object... params);

    /**
     * Creates a table if it does not exist.
     *
     * <p>Called during startup for the plugin's own schema. Modules should call this from their
     * {@code onLoad} rather than on every startup.
     *
     * @param table  table name, which should be namespaced by module
     * @param columns column definitions joined by commas, e.g. {@code "id TEXT PRIMARY KEY, data BLOB"}
     */
    CompletableFuture<Void> createTable(String table, String columns);

    /**
     * Checks whether a table exists.
     *
     * @return a future completing with the result
     */
    CompletableFuture<Boolean> hasTable(String table);

    /** The connection this service uses. Escape hatch for module code needing full JDBC. */
    Connection connection();

    /** Flushes pending writes and closes the database. Called on plugin disable. */
    void shutdown();

    /**
     * Maps one row of a result set.
     *
     * <p>Implementations receive a live {@link java.sql.ResultSet} positioned on the current row.
     * Read only the columns you need.
     *
     * @param <T> the mapped type
     */
    @FunctionalInterface
    interface RowMapper<T> {

        /**
         * @param row the current row
         * @return the mapped value
         * @throws java.sql.SQLException if reading fails
         */
        T map(java.sql.ResultSet row) throws java.sql.SQLException;
    }

    /** Convenience alias so callers can write {@code Map<String, Object>} mappings inline. */
    static RowMapper<Map<String, Object>> toMap() {
        return row -> {
            java.sql.ResultSetMetaData meta = row.getMetaData();
            int columns = meta.getColumnCount();
            Map<String, Object> values = new java.util.LinkedHashMap<>(columns);
            for (int i = 1; i <= columns; i++) {
                values.put(meta.getColumnLabel(i), row.getObject(i));
            }
            return values;
        };
    }
}