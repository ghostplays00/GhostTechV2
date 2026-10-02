package org.ghostplays.ghosttech.internal.database;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.ghostplays.ghosttech.api.database.DatabaseService;

/**
 * SQLite implementation of {@link DatabaseService}.
 *
 * <h2>Threading</h2>
 * SQLite allows only one writer at a time, and blocking the main thread stalls the whole server.
 * Every operation therefore runs on a small dedicated pool rather than on the main thread or
 * Bukkit's async pool.
 *
 * <h2>Durability</h2>
 * The journal mode is WAL, so a crash mid-write cannot corrupt the database. Statements are
 * committed individually rather than batched.
 */
public final class SqliteDatabaseService implements DatabaseService, AutoCloseable {

    private final Connection connection;
    private final ExecutorService executor;
    private final Logger logger;
    private volatile boolean closed;

    /**
     * Opens, or creates, the database.
     *
     * @param file   the SQLite file; its parent directory is created when missing
     * @param logger logger for connection and statement failures
     * @throws SQLException if the file cannot be opened
     */
    public SqliteDatabaseService(File file, Logger logger) throws SQLException {
        this.logger = logger;
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new SQLException("Could not create database directory: " + parent);
        }

        // Load the driver explicitly: some server environments do not auto-register JDBC drivers
        // from the plugin classloader, which would otherwise surface as "no suitable driver".
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException exception) {
            throw new SQLException("SQLite JDBC driver is not on the classpath", exception);
        }

        this.connection = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
        this.connection.setAutoCommit(true);

        try (var statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode=WAL");
            statement.execute("PRAGMA synchronous=NORMAL");
            statement.execute("PRAGMA foreign_keys=ON");
            statement.execute("PRAGMA busy_timeout=5000");
        }

        // One thread is enough and avoids write contention: SQLite serialises writers anyway, and a
        // single writer keeps statement ordering deterministic.
        this.executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "GhostTech-Database");
            thread.setDaemon(true);
            return thread;
        });

        logger.info("SQLite database ready at " + file.getName());
    }
    @Override
    public CompletableFuture<Integer> execute(String sql, Object... params) {
        return supply("execute", () -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                SqlStatements.bind(statement, params);
                return statement.executeUpdate();
            }
        });
    }

    @Override
    public <T> CompletableFuture<List<T>> query(String sql, RowMapper<T> mapper, Object... params) {
        return supply("query", () -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                SqlStatements.bind(statement, params);
                try (ResultSet rows = statement.executeQuery()) {
                    List<T> results = new ArrayList<>();
                    while (rows.next()) {
                        results.add(mapper.map(rows));
                    }
                    return List.copyOf(results);
                }
            }
        });
    }

    @Override
    public <T> CompletableFuture<T> queryOne(String sql, RowMapper<T> mapper, Object... params) {
        return query(sql, mapper, params).thenApply(rows -> rows.isEmpty() ? null : rows.get(0));
    }

    @Override
    public CompletableFuture<Void> createTable(String table, String columns) {
        String sql = "CREATE TABLE IF NOT EXISTS " + SqlStatements.sanitiseTable(table) + " (" + columns + ")";
        return execute(sql).thenApply(ignored -> null);
    }

    @Override
    public CompletableFuture<Boolean> hasTable(String table) {
        return supply("hasTable", () -> {
            String sql = "SELECT 1 FROM sqlite_master WHERE type='table' AND name=?";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, SqlStatements.sanitiseTable(table));
                try (ResultSet rows = statement.executeQuery()) {
                    return rows.next();
                }
            }
        });
    }

    @Override
    public Connection connection() {
        return connection;
    }

    /**
     * Runs a blocking action on the database thread.
     *
     * <p>Exposed for module code needing raw JDBC that is already off the main thread.
     *
     * @param label short description used in error logs
     * @param work  the action
     * @param <T>   result type
     * @return a future completing with the result
     */
    public <T> CompletableFuture<T> supply(String label, SqlSupplier<T> work) {
        if (closed) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Database is closed: " + label));
        }
        CompletableFuture<T> future = new CompletableFuture<>();
        executor.execute(() -> {
            try {
                future.complete(work.get());
            } catch (Throwable throwable) {
                logger.log(Level.SEVERE, "Database operation failed: " + label, throwable);
                future.completeExceptionally(throwable);
            }
        });
        return future;
    }

    @Override
    public void shutdown() {
        if (closed) {
            return;
        }
        closed = true;
        executor.shutdown();
        try {
            // Let queued writes finish so nothing is lost on a clean shutdown.
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                logger.warning("Database executor did not finish in time, forcing shutdown");
                executor.shutdownNow();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
        try {
            if (!connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException exception) {
            logger.log(Level.WARNING, "Failed to close the database connection", exception);
        }
    }

    @Override
    public void close() {
        shutdown();
    }

    /** A blocking JDBC action, used by {@link #supply(String, SqlSupplier)}. */
    @FunctionalInterface
    public interface SqlSupplier<T> {

        /**
         * @return the result
         * @throws SQLException if the statement fails
         */
        T get() throws SQLException;
    }
}