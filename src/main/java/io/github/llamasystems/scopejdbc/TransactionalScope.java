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
            throw Failures.wrap("Failed to initialize transactional scope", failure, restoreFailure, closeFailure);
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
    protected ConnectionScopeException performClose() {
        SQLException rollbackFailure = null;
        try {
            connection.rollback();
        } catch (SQLException e) {
            rollbackFailure = e;
        }

        SQLException restoreFailure = restoreConnectionState();
        SQLException closeFailure = closePhysicalConnection();

        return Failures.wrap(
                "Failed to close transactional scope cleanly",
                rollbackFailure,
                restoreFailure,
                closeFailure
        );
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