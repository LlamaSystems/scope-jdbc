package io.github.llamasystems.connectionscope;

import io.github.llamasystems.connectionscope.exception.ConnectionScopeException;

import java.sql.Connection;

/// # JdbcClient
/// Abstraction over JDBC operations used by [ConnectionScope].
///
/// All operations are executed on the single [Connection] owned by
/// the parent [ConnectionScope].
///
/// @author Aliabbos Ashurov
/// @since 1.0.0
public interface JdbcClient {

    /// Executes a SELECT query and maps each row using the provided [RowMapper].
    ///
    /// @param sql    the SQL query to execute
    /// @param mapper row mapper that converts a [java.sql.ResultSet] row into `T`
    /// @param params positional parameters (may be empty)
    /// @param <T>    the target type
    /// @return an immutable [Result] containing mapped rows
    /// @throws ConnectionScopeException if query execution fails
    <T> Result<T> query(String sql, RowMapper<T> mapper, Object... params);

    /// Executes an INSERT, UPDATE, or DELETE statement.
    ///
    /// @param sql    the SQL statement
    /// @param params positional parameters (may be empty)
    /// @return a [Result] containing the number of affected rows
    /// @throws ConnectionScopeException if execution fails
    Result<Integer> update(String sql, Object... params);

    /// Executes an INSERT statement that may generate keys and returns the first generated key
    /// if available; otherwise returns the number of affected rows.
    ///
    /// @param sql    the INSERT statement
    /// @param params positional parameters
    /// @return a [Result] containing the generated key (if any) or affected row count
    /// @throws ConnectionScopeException if execution fails
    Result<Integer> updateReturningKey(String sql, Object... params);
}