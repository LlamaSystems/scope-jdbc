package io.github.llamasystems.connectionscope;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import java.util.function.Function;

/// # ConnectionScopeImpl
///
/// @author Aliabbos Ashurov
/// @since 1.0.0
final class ConnectionScopeImpl implements ConnectionScope {

    private final Connection connection;
    private final JdbcClient client;
    private final boolean transactional;
    private boolean completed = false;
    private final Thread ownerThread;

    // --------------------------- Factory ---------------------------
    public static ConnectionScopeImpl open(DataSource dataSource) {
        return new ConnectionScopeImpl(dataSource, false);
    }

    public static ConnectionScopeImpl openTransactional(DataSource dataSource) {
        return new ConnectionScopeImpl(dataSource, true);
    }

    ConnectionScopeImpl(DataSource dataSource, boolean transactional) {
        try {
            this.connection = Objects.requireNonNull(dataSource, "dataSource").getConnection();
            this.transactional = transactional;
            this.ownerThread = Thread.currentThread();
            if (transactional) {
                this.connection.setAutoCommit(false);
            }
            this.client = new JdbcClientImpl(connection);
        } catch (SQLException e) {
            throw new ConnectionScopeException("Failed to open connection", e);
        }
    }

    @Override
    public <T> T execute(Function<JdbcClient, T> block) {
        checkThreadConfined();
        try {
            return block.apply(client);
        } catch (RuntimeException | Error e) { // NOSONAR
            if (transactional && !completed) {
                try {
                    connection.rollback();
                } catch (SQLException ex) {
                    e.addSuppressed(ex);
                }
                completed = true;
            }
            throw e;
        }
    }

    @Override
    public void commit() {
        checkThreadConfined();
        if (!transactional) {
            throw new ConnectionScopeException("Cannot commit a non-transactional ConnectionScopeImpl");
        }
        try {
            connection.commit();
            completed = true;
        } catch (SQLException e) {
            throw new ConnectionScopeException("Failed to commit transaction", e);
        }
    }

    @Override
    public void rollback() {
        checkThreadConfined();
        if (!transactional) {
            throw new ConnectionScopeException("Cannot rollback a non-transactional ConnectionScopeImpl");
        }
        try {
            connection.rollback();
            completed = true;
        } catch (SQLException e) {
            throw new ConnectionScopeException("Failed to rollback transaction", e);
        }
    }

    @Override
    public void close() {
        checkThreadConfined();
        try {
            if (transactional && !completed) {
                try {
                    connection.rollback();
                } catch (SQLException e) {
                    throw new ConnectionScopeException("Failed to rollback on close", e);
                }
            }
        } finally {
            try {
                connection.close();
            } catch (SQLException e) {
                throw new ConnectionScopeException("Failed to close connection", e); //NOSONAR
            }
        }
    }

    private void checkThreadConfined() {
        if (Thread.currentThread() != ownerThread) {
            throw new ConnectionScopeException(
                    "ConnectionScopeImpl must not be used from a different thread"
            );
        }
    }
}
