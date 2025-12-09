package io.github.llamasystems.scopejdbc;

import io.github.llamasystems.scopejdbc.exception.ConnectionScopeException;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.function.Function;

/// # DefaultScope
///
/// @author Aliabbos Ashurov
/// @since 1.0.0
final class DefaultScope extends AbstractConnectionScope {

    DefaultScope(DataSource dataSource) {
        super(dataSource);
        try {
            connection.setAutoCommit(true);
        } catch (SQLException e) {
            closeConnection();
            throw new ConnectionScopeException("Failed to set auto-commit for non-transactional scope", e);
        }
    }

    @Override
    public <T> T execute(Function<JdbcClient, T> block) {
        checkThreadConfined();
        checkActive();
        return block.apply(client);
    }

    @Override
    public void commit() {
        throw new ConnectionScopeException("Cannot commit a non-transactional ConnectionScope");
    }

    @Override
    public void rollback() {
        throw new ConnectionScopeException("Cannot rollback a non-transactional ConnectionScope");
    }

    @Override
    public void close() {
        if (state != State.TERMINATED) {
            markTerminating();
            closeConnection();
        }
    }
}