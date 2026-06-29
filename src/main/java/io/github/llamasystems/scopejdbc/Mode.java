package io.github.llamasystems.scopejdbc;

/**
 * Transaction mode for transactional {@link ConnectionScope} instances.
 */
public enum Mode {
    READ_ONLY,
    READ_WRITE;

    /**
     * Returns whether this mode represents a read-only transaction.
     *
     * @return {@code true} if read-only; {@code false} otherwise
     */
    public boolean isReadOnly() {
        return this == READ_ONLY;
    }
}
