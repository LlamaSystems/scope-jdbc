package io.github.llamasystems.connectionscope;

import java.util.Collections;
import java.util.List;

/// # ResultImpl
/// Immutable implementation of [Result] that wraps a list of rows returned from a query.
///
/// Guarantees that the internal list is unmodifiable.
///
/// @author Aliabbos Ashurov
/// @since 1.0.0
@SuppressWarnings("ClassCanBeRecord")
final class QueryResult<T> implements Result<T> {
    private final List<T> rows;

    QueryResult(List<T> rows) {
        this.rows = Collections.unmodifiableList(rows);
    }

    @Override
    public T get() {
        return rows.isEmpty() ? null : rows.get(0);
    }

    @Override
    public List<T> getAsList() {
        return rows;
    }
}