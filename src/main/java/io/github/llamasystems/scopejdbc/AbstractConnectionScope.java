package io.github.llamasystems.scopejdbc;

import io.github.llamasystems.scopejdbc.exception.ConnectionScopeException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;

/// # AbstractConnectionScope
///
/// @author Aliabbos Ashurov
/// @since 1.0.0
abstract sealed class AbstractConnectionScope implements ConnectionScope
        permits DefaultScope, TransactionalScope {

    protected static final ThreadLocal<ConnectionScope> ACTIVE_SCOPE = new ThreadLocal<>();

    protected final Connection connection;
    protected final JdbcClient client;
    protected final Thread ownerThread;
    protected volatile State state = State.ACTIVE;

    protected AbstractConnectionScope(DataSource dataSource) {
        if (ACTIVE_SCOPE.get() != null) {
            throw new ConnectionScopeException("Nested ConnectionScope on the same thread is not allowed");
        }
        try {
            this.connection = Objects.requireNonNull(dataSource, "dataSource").getConnection();
            this.ownerThread = Thread.currentThread();
            this.client = new JdbcClientImpl(connection);
            ACTIVE_SCOPE.set(this);
        } catch (SQLException e) {
            ACTIVE_SCOPE.remove();
            throw new ConnectionScopeException("Failed to open connection", e);
        }
    }

    @Override
    public State getState() {
        return state;
    }

    @Override
    public Connection getConnection() {
        return connection;
    }

    protected void checkThreadConfined() {
        if (Thread.currentThread() != ownerThread) {
            throw new ConnectionScopeException("ConnectionScope must not be used from a different thread");
        }
    }

    protected void checkActive() {
        if (state != State.ACTIVE) {
            throw new ConnectionScopeException("ConnectionScope is no longer active");
        }
    }

    protected void markTerminating() {
        state = State.TERMINATING;
    }

    protected void markTerminated() {
        state = State.TERMINATED;
        ACTIVE_SCOPE.remove();
    }

    protected void restoreAutoCommit() {
        try {
            connection.setAutoCommit(true);
        } catch (SQLException ex) {
            throw new ConnectionScopeException(
                    "Failed to restore connection to auto-commit mode",
                    ex
            );
        }
    }

    protected void closeConnection() {
        try {
            connection.close();
        } catch (SQLException ex) {
            throw new ConnectionScopeException(
                    "Failed to close JDBC connection",
                    ex
            );
        } finally {
            markTerminated();
        }
    }
}
