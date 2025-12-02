package io.github.llamasystems.connectionscope;

import java.sql.Connection;

/// # JdbcClient
/// Abstraction over JDBC operations used by [ConnectionScope].
///
/// Provides methods for executing queries and updates with parameter binding and mapping results
/// to Java objects. Also exposes the underlying [Connection] for advanced use cases.
///
/// @author Aliabbos Ashurov
/// @since 1.0.0
public interface JdbcClient {

    /// Executes a SQL query and maps the result set to Java objects.
    ///
    /// @param sql    the SQL query
    /// @param mapper the row mapper to convert ResultSet rows into objects
    /// @param params query parameters
    /// @param <T>    the type of objects returned
    /// @return a [Result] containing the mapped objects
    <T> Result<T> query(String sql, RowMapper<T> mapper, Object... params);

    /// Executes a SQL update (INSERT, UPDATE, DELETE).
    ///
    /// @param sql    the SQL update statement
    /// @param params update parameters
    /// @return a [Result] containing the number of affected rows
    Result<Integer> update(String sql, Object... params);

    /// Returns the underlying JDBC [Connection] used by this client.
    ///
    /// Use with caution: modifying the connection state directly may interfere with
    /// transaction management by [ConnectionScope].
    ///
    /// @return the JDBC connection
    Connection getConnection();
}