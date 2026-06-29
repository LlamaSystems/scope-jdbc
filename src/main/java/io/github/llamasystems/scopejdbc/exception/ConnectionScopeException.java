package io.github.llamasystems.scopejdbc.exception;

/**
 * Unchecked exception raised by ScopeJDBC when connection acquisition, statement execution,
 * transaction control, or resource cleanup fails.
 *
 * <p>This exception is used to translate checked JDBC failures into a minimal runtime model
 * while preserving the original cause. Suppressed exceptions may be attached when multiple
 * failure paths occur during rollback or close.
 */
public class ConnectionScopeException extends RuntimeException {

    /**
     * Creates an exception with a message.
     *
     * @param message detail message
     */
    public ConnectionScopeException(String message) {
        super(message);
    }

    /**
     * Creates an exception with a cause.
     *
     * @param cause underlying cause
     */
    public ConnectionScopeException(Throwable cause) {
        super(cause);
    }

    /**
     * Creates an exception with a message and cause.
     *
     * @param message detail message
     * @param cause   underlying cause
     */
    public ConnectionScopeException(String message, Throwable cause) {
        super(message, cause);
    }
}