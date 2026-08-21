package io.github.llamasystems.scopejdbc;

import io.github.llamasystems.scopejdbc.exception.ConnectionScopeException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

final class JdbcClientImpl implements JdbcClient {

    private final Connection connection;

    JdbcClientImpl(Connection connection) {
        this.connection = connection;
    }

    @Override
    public <T> List<T> query(String sql, RowMapper<T> mapper, Object... params) {
        Objects.requireNonNull(sql, "sql");
        Objects.requireNonNull(mapper, "mapper");

        return executeQuery(sql, params, "Failed to execute query", resultSet -> {
            List<T> rows = new ArrayList<>();
            while (resultSet.next()) {
                rows.add(mapper.map(resultSet));
            }
            return rows;
        });
    }

    @Override
    public <T> T queryForObject(String sql, RowMapper<T> mapper, Object... params) {
        Objects.requireNonNull(sql, "sql");
        Objects.requireNonNull(mapper, "mapper");

        return executeQuery(sql, params, "Failed to execute single-result query", resultSet -> {
            if (!resultSet.next()) {
                throw new ConnectionScopeException("Expected exactly one row but query returned no rows");
            }

            T value = mapper.map(resultSet);

            if (resultSet.next()) {
                throw new ConnectionScopeException("Expected exactly one row but query returned more than one row");
            }

            return value;
        });
    }

    @Override
    public boolean exists(String sql, Object... params) {
        Objects.requireNonNull(sql, "sql");

        return executeQuery(sql, params, "Failed to execute existence query", ResultSet::next);
    }

    @Override
    public int update(String sql, Object... params) {
        Objects.requireNonNull(sql, "sql");

        try (PreparedStatement statement = prepareStatement(connection, sql, false, params)) {
            return statement.executeUpdate();
        } catch (SQLException e) {
            throw new ConnectionScopeException("Failed to execute update", e);
        }
    }

    @Override
    public long updateReturningKey(String sql, Object... params) {
        Objects.requireNonNull(sql, "sql");

        try (PreparedStatement statement = prepareStatement(connection, sql, true, params)) {
            int affected = statement.executeUpdate();

            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    return generatedKeys.getLong(1);
                }
            }

            return affected;
        } catch (SQLException e) {
            throw new ConnectionScopeException("Failed to execute update returning key", e);
        }
    }

    private <T> T executeQuery(String sql, Object[] params, String failureMessage, ResultSetHandler<T> handler) {
        try (PreparedStatement statement = prepareStatement(connection, sql, false, params);
             ResultSet resultSet = statement.executeQuery()) {
            return handler.handle(resultSet);
        } catch (SQLException e) {
            throw new ConnectionScopeException(failureMessage, e);
        }
    }

    private static PreparedStatement prepareStatement(
            Connection connection,
            String sql,
            boolean returnGeneratedKeys,
            Object... params
    ) throws SQLException {
        PreparedStatement statement = returnGeneratedKeys
                ? connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)
                : connection.prepareStatement(sql);

        if (params != null) {
            for (int i = 0; i < params.length; i++) {
                statement.setObject(i + 1, params[i]);
            }
        }

        return statement;
    }

    /**
     * Handles a positioned {@link ResultSet} produced by {@link #executeQuery}, translating any
     * checked {@link SQLException} thrown during execution into the caller-supplied failure
     * message.
     *
     * @param <T> handler result type
     */
    @FunctionalInterface
    private interface ResultSetHandler<T> {
        T handle(ResultSet resultSet) throws SQLException;
    }
}