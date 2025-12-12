package io.github.llamasystems.scopejdbc;

import com.google.errorprone.annotations.DoNotCall;
import com.google.errorprone.annotations.MustBeClosed;
import io.github.llamasystems.scopejdbc.exception.ConnectionScopeException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.function.Function;

/// # ConnectionScope
/// A [ConnectionScope] provides a controlled context in which multiple queries must
/// share a single [Connection] obtained from a [DataSource]. This interface
/// ensures predictable and safe management of JDBC connections and transactions, avoiding
/// connection leaks, hidden commits, and unnecessary pooling overhead.
///
/// Connection scopes guarantee explicit control over the lifecycle of the underlying
/// connection, the execution of queries, and transactional boundaries. All operations
/// executed through a scope share the same connection, and the scope enforces strict
/// thread confinement: a scope may only be used from the thread that created it.
///
/// @author Aliabbos Ashurov
/// @since 1.0.0
public sealed interface ConnectionScope extends AutoCloseable
        permits AbstractConnectionScope {

    /// Represents the lifecycle state of a [ConnectionScope].
    enum State {

        /// The scope is active and may execute operations.
        /// Queries can be executed, transactions can be committed or rolled back.
        ACTIVE,

        /// The scope is in the process of terminating.
        /// This occurs during commit, rollback, or close operations.
        /// No new operations should be started while in this state.
        TERMINATING,

        /// The scope is terminated and no further operations are permitted.
        /// The underlying connection has been returned to the pool or closed.
        TERMINATED
    }

    /// Opens a non-transactional connection scope.
    ///
    /// The returned scope executes all operations in auto-commit mode.
    /// Only one connection is borrowed from the pool and released when the scope is closed.
    ///
    /// @param dataSource the [DataSource] to obtain the connection from
    /// @return a [ConnectionScope] with non-transactional behavior
    /// @throws ConnectionScopeException if a connection cannot be obtained
    @MustBeClosed
    static ConnectionScope open(DataSource dataSource) {
        return new DefaultScope(dataSource);
    }

    /// Opens a transactional connection scope in read-write mode.
    ///
    /// The returned scope executes operations within a single transaction.
    /// The user must call [#commit()] or [#rollback()] to finalize the transaction,
    /// or rely on [#close()] to automatically roll back uncommitted changes.
    ///
    /// @param dataSource the [DataSource] to obtain the connection from
    /// @return a [ConnectionScope] with transactional read-write behavior
    /// @throws ConnectionScopeException if a connection cannot be obtained
    @MustBeClosed
    static ConnectionScope openTransactional(DataSource dataSource) {
        return new TransactionalScope(dataSource, false);
    }

    /// Opens a transactional connection scope with specified read-only or read-write mode.
    ///
    /// Read-only scopes may execute queries but will fail on modifications if the database enforces read-only.
    /// Read-write scopes allow full transactional operations.
    ///
    /// @param dataSource the [DataSource] to obtain the connection from
    /// @param readOnly   `true` for read-only mode, `false` for read-write
    /// @return a [ConnectionScope] with the specified transactional mode
    /// @throws ConnectionScopeException if a connection cannot be obtained
    @MustBeClosed
    static ConnectionScope openTransactional(DataSource dataSource, boolean readOnly) {
        return new TransactionalScope(dataSource, readOnly);
    }

    /// Opens a transactional connection scope with a [Mode] enum.
    ///
    /// Provides type-safe specification of read-only or read-write behavior.
    /// Improves readability and avoids passing raw booleans.
    ///
    /// @param dataSource the [DataSource] to obtain the connection from
    /// @param mode       the [Mode] indicating read-only or read-write transactional mode
    /// @return a [ConnectionScope] with the specified transactional mode
    /// @throws ConnectionScopeException if a connection cannot be obtained
    @MustBeClosed
    static ConnectionScope openTransactional(DataSource dataSource, Mode mode) {
        return new TransactionalScope(dataSource, mode.isReadOnly());
    }

    /// If an unchecked exception or error escapes the block in a transactional scope,
    /// the current transaction branch is automatically rolled back.
    /// The scope remains active and may be reused for further operations.
    /// This allows recovery after partial failure.
    ///
    /// Note: checked [java.sql.SQLException]s are not caught — you must handle them explicitly.
    ///
    /// @param block a function that receives a [JdbcClient] and returns a result of type `T`
    /// @param <T>   the return type of the executed block
    /// @return the result produced by the block
    /// @throws ConnectionScopeException if the scope is not active, the thread is invalid,or the execution fails
    <T> T execute(Function<JdbcClient, T> block);

    /// Commits the current transaction branch.
    ///
    /// The scope remains active after commit. You may continue executing
    /// queries and start new transaction branches (via further commit/rollback).
    /// Only [#close()] finally terminates the scope and returns the connection.
    ///
    ///
    /// **Note:** Calling `commit()` on a **read-only** transactional scope
    /// is allowed and is effectively a no-op for most databases (some drivers require an explicit commit
    /// even when no changes were made).
    ///
    /// @throws ConnectionScopeException if this is not a transactional scope, the scope is not active,
    ///                                  the calling thread is wrong, or the commit fails
    void commit();

    /// Rolls back the current transaction branch.
    ///
    /// The scope remains active after rollback. You may continue executing
    /// queries and start new transactions again.
    /// Only [#close()] finally terminates the scope and returns the connection.
    ///
    ///
    /// **Note:** Calling `rollback()` on a **read-only** transactional scope
    /// is a **no-op** — it returns immediately without performing any action,
    /// because read-only transactions have no changes to roll back.
    ///
    /// @throws ConnectionScopeException if this is not a transactional scope, the scope is not active,
    ///                                  the calling thread is wrong, or the rollback fails
    ///                                  (rollback errors can only occur in read-write mode)
    void rollback();

    /// Returns the current lifecycle state of the scope.
    ///
    /// @return the [State] of this scope
    State getState();

    /// Provides direct access to the underlying [Connection].
    ///
    /// The connection is owned and managed by this scope and MUST NOT be closed or used
    /// to change transactional state (for example: setAutoCommit, setTransactionIsolation).
    /// Doing so will result in undefined behaviour and may throw [ConnectionScopeException].
    /// This method is intended only for advanced usage.
    ///
    /// @return the [Connection] bound to this scope
    @DoNotCall("Unsafe: advanced usage only, may throw ConnectionScopeException")
    Connection getConnection();

    /// Closes the scope and returns the connection to the pool.
    ///
    /// For transactional scopes: if the current transaction branch has not been
    /// explicitly committed, any uncommitted changes are rolled back
    /// (only relevant for read-write mode).
    ///
    /// After [#close()], the scope is in [State#TERMINATED] and no further operations
    /// are permitted.
    ///
    /// @throws ConnectionScopeException if an error occurs while closing
    @Override
    void close();
}
