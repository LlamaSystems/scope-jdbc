package io.github.llamasystems.connectionscope;

import java.util.List;

/// # UpdateResultImpl
/// Immutable implementation of [Result] for SQL update operations (INSERT, UPDATE, DELETE).
///
/// Wraps the number of affected rows returned by the JDBC update operation.
///
/// @author Aliabbos Ashurov
/// @since 1.0.0
@SuppressWarnings("ClassCanBeRecord")
final class UpdateResult implements Result<Integer> {
    private final int affected;

    UpdateResult(int affected) {
        this.affected = affected;
    }

    @Override
    public Integer get() {
        return affected;
    }

    @Override
    public List<Integer> getAsList() {
        return List.of(affected);
    }
}