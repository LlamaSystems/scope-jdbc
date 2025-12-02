package io.github.llamasystems.connectionscope;

import java.util.List;

/// # Result
/// Represents the result of a JDBC operation executed via [JdbcClient].
///
/// Provides methods to access a single value or the full list of results.
///
/// @author Aliabbos Ashurov
/// @since 1.0.0
public interface Result<T> {

    /// Returns the first result or `null` if no results exist.
    ///
    /// @return the first result or `null`
    T get();

    /// Returns all results as an unmodifiable list.
    ///
    /// @return a list of results, never `null`
    List<T> getAsList();
}
