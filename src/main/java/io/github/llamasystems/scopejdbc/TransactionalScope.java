package io.github.llamasystems.scopejdbc;

import io.github.llamasystems.scopejdbc.exception.ConnectionScopeException;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.function.Function;

/// # TransactionalScope
///
/// @author Aliabbos Ashurov
/// @since 1.0.0
final class TransactionalScope extends AbstractConnectionScope {

    private final boolean readOnly;

    TransactionalScope(DataSource dataSource, boolean readOnly) {
        super(dataSource);
        this.readOnly = readOnly;
        try {
            connection.setReadOnly(readOnly);
            connection.setAutoCommit(false);
        } catch (SQLException e) {
            restoreAutoCommit();
            closeConnection();
            throw new ConnectionScopeException("Failed to initialize transactional scope", e);
        }
    }

    @Override
    public <T> T execute(Function<JdbcClient, T> block) {
        checkThreadConfined();
        checkActive();
        try {
            return block.apply(client);
        } catch (RuntimeException | Error e) {
            rollbackOnException(e);
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
            rollbackOnException(e);
            throw new ConnectionScopeException("Failed to commit transaction", e);
        }
    }

    @Override
    public void rollback() {
        checkThreadConfined();
        checkActive();
        if (readOnly) {
            return;
        }
        try {
            connection.rollback();
        } catch (SQLException e) {
            throw new ConnectionScopeException("Failed to rollback transaction", e);
        }
    }

    @Override
    public void close() {
        if (state == State.TERMINATED) return;

        markTerminating();
        try {
            if (!readOnly) {
                try {
                    connection.rollback();
                } catch (SQLException _) {
                    // Ignored on close to avoid masking prior exceptions
                }
            }
            restoreAutoCommit();
        } finally {
            closeConnection();
        }
    }

    private void rollbackOnException(Throwable e) {
        if (state != State.ACTIVE) return;

        try {
            connection.rollback();
        } catch (SQLException ex) {
            e.addSuppressed(ex);
        }
    }
}