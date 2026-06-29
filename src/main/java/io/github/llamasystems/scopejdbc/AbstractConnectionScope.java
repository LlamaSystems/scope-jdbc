package io.github.llamasystems.scopejdbc;

import io.github.llamasystems.scopejdbc.exception.ConnectionScopeException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;

abstract sealed class AbstractConnectionScope implements ConnectionScope
        permits DefaultScope, TransactionalScope {

    protected final Connection connection;
    protected final JdbcClient client;
    protected final Thread ownerThread;
    protected volatile State state = State.ACTIVE;

    protected AbstractConnectionScope(DataSource dataSource) {
        Objects.requireNonNull(dataSource, "dataSource");
        this.ownerThread = Thread.currentThread();

        try {
            this.connection = dataSource.getConnection();
            this.client = new JdbcClientImpl(connection);
        } catch (SQLException e) {
            throw new ConnectionScopeException("Failed to open JDBC connection", e);
        }
    }

    @Override
    public final State getState() {
        return state;
    }

    @Override
    public final Connection getConnection() {
        return connection;
    }

    protected final void checkThreadConfined() {
        if (Thread.currentThread() != ownerThread) {
            throw new ConnectionScopeException("ConnectionScope must only be used from its owner thread");
        }
    }

    protected final void checkActive() {
        if (state != State.ACTIVE) {
            throw new ConnectionScopeException("ConnectionScope is no longer active");
        }
    }

    protected final void markTerminating() {
        state = State.TERMINATING;
    }

    protected final void markTerminated() {
        state = State.TERMINATED;
    }

    protected final ConnectionScopeException closeFailure(
            String message,
            Throwable primary,
            Throwable secondary,
            Throwable tertiary
    ) {
        ConnectionScopeException exception = new ConnectionScopeException(message, primary);
        if (secondary != null) {
            exception.addSuppressed(secondary);
        }
        if (tertiary != null) {
            exception.addSuppressed(tertiary);
        }
        return exception;
    }

    protected final SQLException restoreConnectionState() {
        SQLException failure = null;

        try {
            connection.setAutoCommit(true);
        } catch (SQLException e) {
            failure = e;
        }

        try {
            connection.setReadOnly(false);
        } catch (SQLException e) {
            if (failure == null) {
                failure = e;
            } else {
                failure.addSuppressed(e);
            }
        }

        return failure;
    }

    protected final SQLException closePhysicalConnection() {
        try {
            connection.close();
            return null;
        } catch (SQLException e) {
            return e;
        }
    }
}