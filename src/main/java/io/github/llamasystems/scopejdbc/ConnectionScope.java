package io.github.llamasystems.scopejdbc;

import io.github.llamasystems.scopejdbc.exception.ConnectionScopeException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Explicit connection scope that binds a single JDBC {@link Connection} to a bounded block
 * of application code.
 *
 * <p>A scope acquires exactly one connection from a {@link DataSource}, exposes it through a
 * minimal execution API, and releases it when {@link #close()} is called. Transactional scopes
 * disable auto-commit and require explicit {@link #commit()} or {@link #rollback()} control.
 *
 * <p>Scopes are thread-confined: a scope must only be used from the thread that created it.
 * Calling {@link #execute}, {@link #executeVoid}, {@link #commit}, or {@link #rollback} after
 * termination or from a different thread results in {@link ConnectionScopeException}.
 * {@link #getConnection()} and {@link #close()} do not themselves enforce these checks; callers
 * must still respect thread confinement and lifecycle state when using them, since violating
 * either can leave the underlying JDBC connection in an undefined state.
 *
 * <p>Typical usage:
 *
 * <pre>{@code
 * try (ConnectionScope scope = ConnectionScope.openTransactional(dataSource)) {
 *     scope.execute(client -> {
 *         client.update("UPDATE accounts SET balance = balance - ? WHERE id = ?", 100, 1);
 *         client.update("UPDATE accounts SET balance = balance + ? WHERE id = ?", 100, 2);
 *     });
 *     scope.commit();
 * }
 * }</pre>
 */
public sealed interface ConnectionScope extends AutoCloseable permits AbstractConnectionScope {

    /**
     * Lifecycle state of a {@link ConnectionScope}.
     */
    enum State {
        /**
         * Scope is open and may be used.
         */
        ACTIVE,

        /**
         * Scope is performing termination work such as rollback, state restoration, or close.
         */
        TERMINATING,

        /**
         * Scope has been fully closed and may no longer be used.
         */
        TERMINATED
    }

    /**
     * Opens a non-transactional scope.
     *
     * <p>The borrowed connection operates in auto-commit mode for the lifetime of the scope.
     *
     * @param dataSource data source used to obtain the connection
     * @return newly opened non-transactional scope
     * @throws NullPointerException     if {@code dataSource} is {@code null}
     * @throws ConnectionScopeException if connection acquisition fails
     */
    static ConnectionScope open(DataSource dataSource) {
        return new DefaultScope(dataSource);
    }

    /**
     * Opens a transactional read-write scope.
     *
     * <p>The borrowed connection is configured with auto-commit disabled. Uncommitted work is
     * rolled back automatically during {@link #close()}.
     *
     * @param dataSource data source used to obtain the connection
     * @return newly opened transactional scope
     * @throws NullPointerException     if {@code dataSource} is {@code null}
     * @throws ConnectionScopeException if connection acquisition or initialization fails
     */
    static ConnectionScope openTransactional(DataSource dataSource) {
        return new TransactionalScope(dataSource, false);
    }

    /**
     * Opens a transactional scope with the requested mode.
     *
     * @param dataSource data source used to obtain the connection
     * @param mode       transaction mode
     * @return newly opened transactional scope
     * @throws NullPointerException     if {@code dataSource} or {@code mode} is {@code null}
     * @throws ConnectionScopeException if connection acquisition or initialization fails
     */
    static ConnectionScope openTransactional(DataSource dataSource, Mode mode) {
        Objects.requireNonNull(mode, "mode");
        return new TransactionalScope(dataSource, mode.isReadOnly());
    }

    /**
     * Executes code against the scope-bound {@link JdbcClient} and returns a value.
     *
     * <p>In transactional scopes, if the supplied block throws a {@link RuntimeException} or
     * {@link Error}, ScopeJDBC immediately attempts to roll back the current uncommitted
     * transaction state before the original exception is rethrown. The scope remains
     * {@link State#ACTIVE} afterward regardless of whether that rollback attempt succeeds; if the
     * rollback itself fails, the resulting {@link java.sql.SQLException} is attached to the
     * original exception via {@link Throwable#addSuppressed}, and the original exception is what
     * propagates.
     *
     * @param block code to execute
     * @param <T>   result type
     * @return result returned by the supplied block
     * @throws NullPointerException     if {@code block} is {@code null}
     * @throws ConnectionScopeException if the scope is inactive or accessed from the wrong thread
     */
    <T> T execute(Function<JdbcClient, T> block);

    /**
     * Executes code against the scope-bound {@link JdbcClient} without producing a return value.
     *
     * <p>This method is intended for side-effect-oriented workflows, such as issuing one or more
     * update statements within the same connection scope, without forcing callers to return
     * {@code null} from a value-producing lambda.
     *
     * <p>In transactional scopes, if the supplied block throws a {@link RuntimeException} or
     * {@link Error}, ScopeJDBC immediately attempts to roll back the current uncommitted
     * transaction state before the original exception is rethrown. The scope remains
     * {@link State#ACTIVE} afterward regardless of whether that rollback attempt succeeds; if the
     * rollback itself fails, the resulting {@link java.sql.SQLException} is attached to the
     * original exception via {@link Throwable#addSuppressed}, and the original exception is what
     * propagates.
     *
     * @param block code to execute against the scope-bound client
     * @throws NullPointerException     if {@code block} is {@code null}
     * @throws ConnectionScopeException if the scope is inactive or accessed from the wrong thread
     */
    void executeVoid(Consumer<JdbcClient> block);

    /**
     * Commits the current transactional branch.
     *
     * <p>This operation is valid only for transactional scopes. The scope remains open after a
     * successful commit and may continue to execute additional work on the same connection.
     *
     * @throws ConnectionScopeException if this scope is non-transactional, inactive, accessed
     *                                  from the wrong thread, or the commit fails
     */
    void commit();

    /**
     * Rolls back the current transactional branch.
     *
     * <p>This operation is valid only for transactional scopes. The scope remains open after a
     * successful rollback and may continue to execute additional work on the same connection.
     *
     * @throws ConnectionScopeException if this scope is non-transactional, inactive, accessed
     *                                  from the wrong thread, or the rollback fails
     */
    void rollback();

    /**
     * Returns the current lifecycle state.
     *
     * @return current scope state
     */
    State getState();

    /**
     * Returns the underlying JDBC connection.
     *
     * <p>This method is an explicit escape hatch for advanced use cases. The returned connection
     * remains owned by the scope and must not be closed by the caller. Callers must not change
     * auto-commit, commit, rollback, read-only state, or other connection-level settings that
     * would violate scope invariants. This method does not check thread confinement or lifecycle
     * state; misuse from a foreign thread or after termination is not detected.
     *
     * @return scope-owned JDBC connection
     */
    Connection getConnection();

    /**
     * Closes the scope and releases the underlying connection.
     *
     * <p>For transactional scopes, uncommitted work is rolled back before the connection is
     * closed. Implementations also attempt to restore the connection to auto-commit mode before
     * returning it to the pool. Closing an already-terminated scope is a no-op; the original
     * termination failure, if any, is not replayed on a repeated call.
     *
     * <p>This method does not check thread confinement; callers should still close the scope from
     * its owning thread to avoid racing with in-flight work on the underlying connection.
     *
     * @throws ConnectionScopeException if termination fails
     */
    @Override
    void close();
}