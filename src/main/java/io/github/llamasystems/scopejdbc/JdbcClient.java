package io.github.llamasystems.scopejdbc;

import java.util.List;

/**
 * Low-level JDBC operations bound to the single physical connection owned by a
 * {@link ConnectionScope}.
 *
 * <p>All operations execute immediately on that connection. Statement and result set resources
 * are managed internally and are always closed before the method returns.
 *
 * <p>Implementations are intentionally minimal and do not perform reflection, entity mapping,
 * SQL generation, or retry logic.
 */
public interface JdbcClient {

    /**
     * Executes a query and maps each returned row using the supplied mapper.
     *
     * @param sql    SQL statement to execute
     * @param mapper row mapper applied to each returned row
     * @param params positional statement parameters
     * @param <T>    mapped row type
     * @return rows in encounter order; never {@code null}
     */
    <T> List<T> query(String sql, RowMapper<T> mapper, Object... params);


    /**
     * Executes a query that must return exactly one row and maps that row to a single object.
     *
     * <p>If the query returns no rows, or more than one row, this method throws
     * {@link io.github.llamasystems.scopejdbc.exception.ConnectionScopeException}.
     *
     * <p>This method is intended for lookups where the result cardinality is part of the
     * contract, such as loading a record by a unique key or reading a single aggregate value.
     *
     * @param sql    SQL statement to execute
     * @param mapper row mapper applied to the single expected row
     * @param params positional statement parameters
     * @param <T>    mapped row type
     * @return mapped object for the single returned row
     * @throws NullPointerException                                                if {@code sql} or {@code mapper} is {@code null}
     * @throws io.github.llamasystems.scopejdbc.exception.ConnectionScopeException if query execution
     *                                                                             fails, no row is returned, or more than one row is returned
     */
    <T> T queryForObject(String sql, RowMapper<T> mapper, Object... params);

    /**
     * Executes an existence check.
     *
     * <p>The query is considered successful when at least one row is returned.
     *
     * @param sql    SQL statement to execute
     * @param params positional statement parameters
     * @return {@code true} if at least one row exists; {@code false} otherwise
     */
    boolean exists(String sql, Object... params);

    /**
     * Executes an INSERT, UPDATE, DELETE, or DDL statement.
     *
     * @param sql    SQL statement to execute
     * @param params positional statement parameters
     * @return affected row count as reported by JDBC
     */
    int update(String sql, Object... params);

    /**
     * Executes a statement requesting generated keys and returns the first generated key when
     * available. If the driver does not return a generated key, the affected row count is returned.
     *
     * <p>This method preserves the original library behavior. Callers should be aware that the
     * returned integer may represent either a generated key or an affected row count.
     *
     * @param sql    SQL statement to execute
     * @param params positional statement parameters
     * @return first generated key if present; otherwise affected row count
     */
    long updateReturningKey(String sql, Object... params);
}