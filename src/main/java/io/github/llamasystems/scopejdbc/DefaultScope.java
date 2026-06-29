package io.github.llamasystems.scopejdbc;

import io.github.llamasystems.scopejdbc.exception.ConnectionScopeException;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

final class DefaultScope extends AbstractConnectionScope {

    DefaultScope(DataSource dataSource) {
        super(dataSource);

        try {
            connection.setAutoCommit(true);
        } catch (SQLException e) {
            SQLException closeFailure = closePhysicalConnection();
            markTerminated();

            ConnectionScopeException exception =
                    new ConnectionScopeException("Failed to initialize non-transactional scope", e);
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
        return block.apply(client);
    }

    @Override
    public void executeVoid(Consumer<JdbcClient> block) {
        Objects.requireNonNull(block, "block");
        checkThreadConfined();
        checkActive();
        block.accept(client);
    }

    @Override
    public void commit() {
        throw new ConnectionScopeException("commit() is not supported for non-transactional scopes");
    }

    @Override
    public void rollback() {
        throw new ConnectionScopeException("rollback() is not supported for non-transactional scopes");
    }

    @Override
    public void close() {
        if (state == State.TERMINATED) {
            return;
        }

        markTerminating();

        SQLException closeFailure = closePhysicalConnection();
        markTerminated();

        if (closeFailure != null) {
            throw new ConnectionScopeException("Failed to close JDBC connection", closeFailure);
        }
    }
}