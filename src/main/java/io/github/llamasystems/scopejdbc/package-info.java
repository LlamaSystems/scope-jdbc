/**
 * Explicit, lightweight JDBC connection and transaction scoping.
 *
 * <p>The central type is {@link io.github.llamasystems.scopejdbc.ConnectionScope}, which binds a
 * single {@link java.sql.Connection} obtained from a {@link javax.sql.DataSource} to a bounded
 * block of code and exposes it through the minimal
 * {@link io.github.llamasystems.scopejdbc.JdbcClient} execution API.
 *
 * <p>This package intentionally does not provide entity mapping, SQL generation, connection
 * pooling, or retry logic. Callers remain responsible for SQL text and for choosing a
 * {@link javax.sql.DataSource} implementation appropriate for their deployment.
 */
package io.github.llamasystems.scopejdbc;
