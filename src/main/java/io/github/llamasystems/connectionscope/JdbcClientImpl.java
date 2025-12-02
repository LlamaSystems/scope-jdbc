package io.github.llamasystems.connectionscope;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/// # JdbcClientImpl
///
/// @author Aliabbos Ashurov
/// @since 1.0.0
@SuppressWarnings("ClassCanBeRecord")
final class JdbcClientImpl implements JdbcClient {

    private final Connection connection;

    JdbcClientImpl(Connection connection) {
        this.connection = connection;
    }

    @Override
    public <T> Result<T> query(String sql, RowMapper<T> mapper, Object... params) {
        Objects.requireNonNull(sql, "sql");
        Objects.requireNonNull(mapper, "mapper");
        try (PreparedStatement ps = prepareStatement(connection, sql, params);
             ResultSet rs = ps.executeQuery()) {
            List<T> rows = new ArrayList<>();
            while (rs.next()) {
                rows.add(mapper.map(rs));
            }
            return new QueryResult<>(rows);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Result<Integer> update(String sql, Object... params) {
        Objects.requireNonNull(sql, "sql");
        try (PreparedStatement ps = prepareStatement(connection, sql, params)) {
            int affected = ps.executeUpdate();
            return new UpdateResult(affected);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Connection getConnection() {
        return connection;
    }

    private static PreparedStatement prepareStatement(Connection conn, String sql, Object... params) throws SQLException {
        PreparedStatement ps = conn.prepareStatement(sql);
        if (params != null) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
        }
        return ps;
    }
}
