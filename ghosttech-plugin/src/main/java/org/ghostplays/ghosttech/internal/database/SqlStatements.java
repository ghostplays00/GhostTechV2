package org.ghostplays.ghosttech.internal.database;

import java.sql.PreparedStatement;
import java.sql.SQLException;

/** Helpers shared by the database implementations. */
final class SqlStatements {

    /**
     * Binds parameters with their correct JDBC type.
     *
     * <p>Explicit typing matters for SQLite: it compares values loosely, so a bound string
     * {@code '5'} would otherwise match an INTEGER column holding {@code 5}, and a text column
     * holding {@code '5'} would not match the integer 5. Binding each type properly avoids both
     * surprises.
     *
     * @param statement the prepared statement to bind to
     * @param params    values in placeholder order, may be {@code null}
     * @throws SQLException if a value cannot be bound
     */
    static void bind(PreparedStatement statement, Object... params) throws SQLException {
        if (params == null) {
            return;
        }
        for (int index = 0; index < params.length; index++) {
            Object value = params[index];
            int position = index + 1;
            if (value == null) {
                statement.setObject(position, null);
            } else if (value instanceof Integer number) {
                statement.setInt(position, number);
            } else if (value instanceof Long number) {
                statement.setLong(position, number);
            } else if (value instanceof Double number) {
                statement.setDouble(position, number);
            } else if (value instanceof Float number) {
                statement.setFloat(position, number);
            } else if (value instanceof Short number) {
                statement.setShort(position, number);
            } else if (value instanceof Byte number) {
                statement.setByte(position, number);
            } else if (value instanceof Boolean flag) {
                statement.setBoolean(position, flag);
            } else if (value instanceof byte[] blob) {
                statement.setBytes(position, blob);
            } else if (value instanceof java.util.UUID uuid) {
                statement.setString(position, uuid.toString());
            } else if (value instanceof Enum<?> enumeration) {
                statement.setString(position, enumeration.name());
            } else if (value instanceof java.time.Instant instant) {
                statement.setString(position, instant.toString());
            } else {
                statement.setString(position, value.toString());
            }
        }
    }

    /**
     * Rejects anything that is not a plain SQL identifier.
     *
     * <p>Table names cannot be bound as parameters, so they are validated instead. This stops a
     * module from injecting SQL through a table name.
     *
     * @param table the requested table name
     * @return the validated name
     * @throws IllegalArgumentException if the name is not a safe identifier
     */
    static String sanitiseTable(String table) {
        if (table == null || !table.matches("[A-Za-z_][A-Za-z0-9_]{0,63}")) {
            throw new IllegalArgumentException("Invalid table name: " + table);
        }
        return table;
    }

    private SqlStatements() {
    }
}