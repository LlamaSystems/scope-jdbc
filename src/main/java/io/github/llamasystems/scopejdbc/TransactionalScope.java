package io.github.llamasystems.scopejdbc;

import io.github.llamasystems.scopejdbc.exception.ConnectionScopeException;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

final class TransactionalScope extends AbstractConnectionScope {

    private final boolean readOnly;

    TransactionalScope(DataSource dataSource, boolean readOnly) {
        super(dataSource);
        this.readOnly = readOnly;

        SQLException failure = null;

        try {
            connection.setReadOnly(readOnly);
        } catch (SQLException e) {
            failure = e;
        }

        if (failure == null) {
            try {
                connection.setAutoCommit(false);
            } catch (SQLException e) {
                failure = e;
            }
        }

        if (failure != null) {
            SQLException restoreFailure = restoreConnectionState();
            SQLException closeFailure = closePhysicalConnection();
            markTerminated();

            ConnectionScopeException exception =
                    new ConnectionScopeException("Failed to initialize transactional scope", failure);
            if (restoreFailure != null) {
                exception.addSuppressed(restoreFailure);
            }
            if (closeFailure != null) {
                exception.addSuppressed(closeFailure);
            }
            throw exception;
        }
    }

    @Override
    public <T> T execute(Function<JdbcClient, T> block) {
        Objects.requireNonNull(block, "block");
        checkThreadConfined();
        checkActive();

        try {
            return block.apply(client);
        } catch (RuntimeException | Error e) {
            rollbackOnExecutionFailure(e);
            throw e;
        }
    }

    @Override
    public void executeVoid(Consumer<JdbcClient> block) {
        Objects.requireNonNull(block, "block");
        checkThreadConfined();
        checkActive();

        try {
            block.accept(client);
        } catch (RuntimeException | Error e) {
            rollbackOnExecutionFailure(e);
            throw e;
        }
    }

    @Override
    public void commit() {
        checkThreadConfined();
        checkActive();

        try {
            connection.commit();
        } catch (SQLException e) {
            throw new ConnectionScopeException("Failed to commit transaction", e);
        }
    }

    @Override
    public void rollback() {
        checkThreadConfined();
        checkActive();

        try {
            connection.rollback();
        } catch (SQLException e) {
            throw new ConnectionScopeException("Failed to roll back transaction", e);
        }
    }

    @Override
    public void close() {
        if (state == State.TERMINATED) {
            return;
        }

        markTerminating();

        SQLException rollbackFailure = null;
        SQLException restoreFailure = null;
        SQLException closeFailure = null;

        try {
            try {
                connection.rollback();
            } catch (SQLException e) {
                rollbackFailure = e;
            }

            restoreFailure = restoreConnectionState();
            closeFailure = closePhysicalConnection();
        } finally {
            markTerminated();
        }

        if (rollbackFailure != null || restoreFailure != null || closeFailure != null) {
            Throwable primary = rollbackFailure != null ? rollbackFailure
                    : restoreFailure != null ? restoreFailure
                      : closeFailure;

            Throwable secondary = rollbackFailure != null && primary != rollbackFailure
                    ? rollbackFailure
                    : restoreFailure != null && primary != restoreFailure
                      ? restoreFailure
                      : closeFailure != null && primary != closeFailure
                        ? closeFailure
                        : null;

            Throwable tertiary = null;
            if (rollbackFailure != null && rollbackFailure != primary && rollbackFailure != secondary) {
                tertiary = rollbackFailure;
            } else if (restoreFailure != null && restoreFailure != primary && restoreFailure != secondary) {
                tertiary = restoreFailure;
            } else if (closeFailure != null && closeFailure != primary && closeFailure != secondary) {
                tertiary = closeFailure;
            }

            throw closeFailure(
                    "Failed to close transactional scope cleanly",
                    primary,
                    secondary,
                    tertiary
            );
        }
    }

    private void rollbackOnExecutionFailure(Throwable original) {
        if (state != State.ACTIVE) {
            return;
        }

        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            original.addSuppressed(rollbackFailure);
        }
    }
}