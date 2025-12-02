package io.github.llamasystems.connectionscope;

import java.sql.ResultSet;
import java.sql.SQLException;

/// # ResultSetMapper
/// Functional interface to map a [ResultSet] row into a Java object.
///
/// Typically used by [JdbcClient#query(String, RowMapper, Object...)] to convert query results
/// into domain objects.
///
/// @author Aliabbos Ashurov
/// @since 1.0.0
@FunctionalInterface
public interface RowMapper<T> {

    /// Maps the current row of the given [ResultSet] to an object of type `T`.
    ///
    /// @param rs the result set, positioned at the current row
    /// @return the mapped object
    /// @throws SQLException if any SQL error occurs
    T map(ResultSet rs) throws SQLException;
}
