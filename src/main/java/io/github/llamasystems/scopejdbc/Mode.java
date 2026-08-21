package io.github.llamasystems.scopejdbc;

/**
 * Transaction mode for transactional {@link ConnectionScope} instances.
 */
public enum Mode {
    /**
     * Transaction is read-only; write operations may be rejected by the driver or database.
     */
    READ_ONLY,

    /**
     * Transaction permits both reads and writes.
     */
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
