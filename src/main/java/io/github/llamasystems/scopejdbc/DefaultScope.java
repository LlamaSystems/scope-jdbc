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
            throw Failures.wrap("Failed to initialize non-transactional scope", e, closeFailure);
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
    protected ConnectionScopeException performClose() {
        return Failures.wrap("Failed to close JDBC connection", closePhysicalConnection());
    }
}