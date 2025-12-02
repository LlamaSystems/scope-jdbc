package io.github.llamasystems.connectionscope;

import javax.sql.DataSource;
import java.util.function.Function;

/// # ConnectionScope
/// Represents a scoped JDBC connection that can be either transactional or non-transactional.
///
/// A `ConnectionScope` manages a single connection from a [DataSource] for the
/// duration of the scope. It ensures proper resource management by automatically returning
/// the connection to the pool when the scope is closed.
///
/// For transactional scopes, multiple JDBC operations can be executed atomically, with
/// automatic rollback on any failure unless [#commit()] is explicitly called.
/// Non-transactional scopes execute each statement immediately without transaction boundaries.
///
/// @author Aliabbos Ashurov
/// @since 1.0.0
public sealed interface ConnectionScope extends AutoCloseable
        permits ConnectionScopeImpl {

    /// Opens a non-transactional ConnectionScope.
    ///
    /// @param dataSource the data source to obtain the connection from
    /// @return a new non-transactional ConnectionScope
    static ConnectionScope open(DataSource dataSource) {
        return new ConnectionScopeImpl(dataSource, false);
    }

    /// Opens a transactional ConnectionScope.
    ///
    /// @param dataSource the data source to obtain the connection from
    /// @return a new transactional ConnectionScope
    static ConnectionScope openTransactional(DataSource dataSource) {
        return new ConnectionScopeImpl(dataSource, true);
    }

    /// Executes a JDBC operation using the internal client.
    ///
    /// For transactional scopes, any exception during execution triggers an automatic rollback.
    ///
    /// @param block the operation to execute
    /// @param <T>   the result type
    /// @return the result of the operation
    <T> T execute(Function<JdbcClient, T> block);

    /// Commits the transaction. Only valid for transactional scopes.
    ///
    /// @throws ConnectionScopeException if the scope is non-transactional or commit fails
    void commit();

    /// Rolls back the transaction. Only valid for transactional scopes.
    ///
    /// @throws ConnectionScopeException if the scope is non-transactional or rollback fails
    void rollback();

    /// Closes the scope and releases the connection.
    ///
    /// Any uncommitted transaction is automatically rolled back. Must be called from
    /// the creating thread.
    ///
    /// @throws ConnectionScopeException if rollback or connection close fails
    @Override
    void close();
}
