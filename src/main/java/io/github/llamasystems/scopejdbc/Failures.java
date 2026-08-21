package io.github.llamasystems.scopejdbc;

import io.github.llamasystems.scopejdbc.exception.ConnectionScopeException;

import java.sql.SQLException;

/**
 * Aggregates zero or more failures collected during a multi-step JDBC operation (such as
 * transaction rollback followed by connection-state restoration and physical close) into a
 * single {@link ConnectionScopeException}.
 *
 * <p>The first non-{@code null} candidate becomes the exception cause; any remaining
 * non-{@code null} candidates are attached as suppressed exceptions, preserving encounter order.
 */
final class Failures {

    private Failures() {
    }

    /**
     * Builds an aggregated failure from the given candidates, or returns {@code null} if every
     * candidate is {@code null}.
     *
     * @param message    exception message used for the resulting exception
     * @param candidates failures to aggregate, in priority order; {@code null} entries are ignored
     * @return aggregated exception, or {@code null} if no candidate was non-{@code null}
     */
    static ConnectionScopeException wrap(String message, SQLException... candidates) {
        ConnectionScopeException exception = null;

        for (SQLException candidate : candidates) {
            if (candidate == null) {
                continue;
            }

            if (exception == null) {
                exception = new ConnectionScopeException(message, candidate);
            } else {
                exception.addSuppressed(candidate);
            }
        }

        return exception;
    }
}
