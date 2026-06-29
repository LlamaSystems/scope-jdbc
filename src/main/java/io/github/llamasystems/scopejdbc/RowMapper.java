
package io.github.llamasystems.scopejdbc;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Maps the current row of a {@link ResultSet} to a target value.
 *
 * <p>The provided {@link ResultSet} is positioned on a valid row and must not be advanced
 * by the mapper. Implementations should read only the columns required to construct the
 * mapped value.
 *
 * @param <T> mapped result type
 */
@FunctionalInterface
public interface RowMapper<T> {

    /**
     * Maps the current row of the given {@link ResultSet}.
     *
     * @param resultSet current JDBC result set row
     * @return mapped value
     * @throws SQLException if column access fails
     */
    T map(ResultSet resultSet) throws SQLException;
}