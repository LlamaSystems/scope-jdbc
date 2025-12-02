package io.github.llamasystems.connectionscope;

/// # ConnectionScopeException
/// Exception thrown by [ConnectionScope] operations.
///
/// This unchecked exception is used to indicate errors during connection scope management,
/// including failures when committing or rolling back transactions, or when closing the scope.
///
/// @author Aliabbos Ashurov
/// @since 1.0.0
public class ConnectionScopeException extends RuntimeException {

    public ConnectionScopeException(String message) {
        super(message);
    }

    public ConnectionScopeException(String message, Throwable cause) {
        super(message, cause);
    }
}
