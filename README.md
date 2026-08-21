![ScopeJDBC](/assets/scope-jdbc-banner-long.png)

[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE.txt)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.llamasystems/scope-jdbc.svg)](https://central.sonatype.com/artifact/io.github.llamasystems/scope-jdbc)
[![Issues](https://img.shields.io/github/issues/LlamaSystems/scope-jdbc)](https://github.com/LlamaSystems/scope-jdbc/issues)

**ScopeJDBC** binds one JDBC `Connection` to one bounded block of code, with explicit transaction
control and no framework machinery underneath.

## The problem

Plain JDBC requires the same ceremony around every unit of work: acquire a connection, remember to
close every `Statement` and `ResultSet`, decide whether auto-commit is on, and make sure a failure
halfway through doesn't leave a transaction dangling. None of that is hard in isolation, but it is
easy to get subtly wrong when it is repeated across dozens of methods — a forgotten `close()`, a
missing `rollback()` on the exception path, a connection accidentally shared across threads.

ScopeJDBC does not remove JDBC or replace SQL with something else. It gives the connection and
transaction lifecycle a single, explicit owner so that boilerplate stops being rewritten (and
occasionally miswritten) at every call site.

## Core concepts

| Type                  | Role                                                                                    |
|------------------------|------------------------------------------------------------------------------------------|
| `ConnectionScope`      | Owns exactly one `Connection` for a bounded unit of work; controls its lifecycle and transaction boundary. |
| `JdbcClient`           | The operations bound to that connection: `query`, `queryForObject`, `exists`, `update`, `updateReturningKey`. |
| `RowMapper<T>`         | A functional interface you implement to turn one `ResultSet` row into a `T`.             |
| `Mode`                 | `READ_ONLY` / `READ_WRITE` — passed when opening a transactional scope.                  |
| `ConnectionScopeException` | The single unchecked exception type ScopeJDBC throws.                                |

A `ConnectionScope` is obtained only through its static factory methods — the interface is
`sealed` and its implementations are package-private, so there is exactly one way to get one and
no way to subclass it from outside the library.

## How it works

```
open()/openTransactional()  →  execute()/executeVoid() one or more times  →  [commit()/rollback()]  →  close()
```

1. **Open** — `ConnectionScope.open(dataSource)` or `ConnectionScope.openTransactional(dataSource)`
   acquires one `Connection` from the given `DataSource` and configures it (auto-commit on for a
   plain scope; auto-commit off, and optionally read-only, for a transactional scope).
2. **Execute** — `scope.execute(client -> ...)` or `scope.executeVoid(client -> ...)` runs your
   code against the scope's `JdbcClient`, on that same connection.
3. **Commit or roll back** — transactional scopes require an explicit `commit()` or `rollback()`.
   A scope stays active after either call, so it can run further work on the same connection
   (useful for multi-step jobs that commit in stages).
4. **Close** — `close()` releases the connection. For a transactional scope, any uncommitted work
   is rolled back first as a safety net, and the connection is restored to auto-commit before it
   is returned to its `DataSource`.

A scope tracks its own lifecycle as `ACTIVE` → `TERMINATING` → `TERMINATED`
(`ConnectionScope.State`, via `getState()`).

## Requirements and installation

- Java 17 or later (the public API uses a `sealed` interface).
- No runtime dependencies — the main artifact depends on nothing beyond the JDK.

**Maven**

```xml
<dependency>
    <groupId>io.github.llamasystems</groupId>
    <artifactId>scope-jdbc</artifactId>
    <version>2.1.0</version>
</dependency>
```

**Gradle (Kotlin DSL)**

```kotlin
dependencies {
    implementation("io.github.llamasystems:scope-jdbc:2.1.0")
}
```

## Quick start

```java
import io.github.llamasystems.scopejdbc.ConnectionScope;

import javax.sql.DataSource;
import java.util.List;

List<String> activeUsernames(DataSource dataSource) {
    try (ConnectionScope scope = ConnectionScope.open(dataSource)) {
        return scope.execute(client ->
                client.query(
                        "SELECT username FROM users WHERE active = ?",
                        rs -> rs.getString("username"),
                        true
                )
        );
    }
}
```

## Usage

The examples below omit imports for brevity; they use the same `ConnectionScope`/`DataSource`
imports shown in [Quick start](#quick-start), plus `io.github.llamasystems.scopejdbc.Mode` and
standard `java.sql` types (`Connection`, `PreparedStatement`, `SQLException`) where used.

### Transactions

```java
void transfer(DataSource dataSource, long fromAccountId, long toAccountId, int amountCents) {
    try (ConnectionScope scope = ConnectionScope.openTransactional(dataSource)) {
        scope.executeVoid(client -> {
            client.update("UPDATE accounts SET balance = balance - ? WHERE id = ?", amountCents, fromAccountId);
            client.update("UPDATE accounts SET balance = balance + ? WHERE id = ?", amountCents, toAccountId);
        });

        scope.commit(); // if this line is never reached, close() rolls back both updates
    }
}
```

### Mapping rows

`RowMapper<T>` is a plain functional interface — one lambda, one row.

```java
record User(long id, String username, boolean active) {}

List<User> findActive(DataSource dataSource) {
    try (ConnectionScope scope = ConnectionScope.open(dataSource)) {
        return scope.execute(client ->
                client.query(
                        "SELECT id, username, active FROM users WHERE active = ?",
                        rs -> new User(rs.getLong("id"), rs.getString("username"), rs.getBoolean("active")),
                        true
                )
        );
    }
}
```

### Single-row lookups

`queryForObject` enforces exactly-one-row cardinality — it throws `ConnectionScopeException` if
the query returns zero rows or more than one.

```java
long shippedOrderCount(DataSource dataSource) {
    try (ConnectionScope scope = ConnectionScope.openTransactional(dataSource, Mode.READ_ONLY)) {
        long count = scope.execute(client ->
                client.queryForObject(
                        "SELECT COUNT(*) FROM orders WHERE status = ?",
                        rs -> rs.getLong(1),
                        "SHIPPED"
                )
        );
        scope.commit();
        return count;
    }
}
```

### Existence checks

```java
boolean hasActiveSession(DataSource dataSource, String token) {
    try (ConnectionScope scope = ConnectionScope.open(dataSource)) {
        return scope.execute(client ->
                client.exists(
                        "SELECT 1 FROM sessions WHERE token = ? AND expires_at > CURRENT_TIMESTAMP",
                        token
                )
        );
    }
}
```

### Inserts and generated keys

`updateReturningKey` returns the first generated key when the driver supplies one, or the affected
row count otherwise — see [Error and exception behavior](#error-and-exception-behavior) for the
ambiguity this implies.

```java
long insertUser(DataSource dataSource, String email) {
    try (ConnectionScope scope = ConnectionScope.openTransactional(dataSource)) {
        long id = scope.execute(client ->
                client.updateReturningKey("INSERT INTO users(email) VALUES (?)", email)
        );
        scope.commit();
        return id;
    }
}
```

### Manual rollback

A transactional scope stays active after `rollback()`, so a decision to cancel does not have to
mean closing the scope immediately.

```java
void maybeProcessInvoice(DataSource dataSource, long invoiceId, boolean shouldCancel) {
    try (ConnectionScope scope = ConnectionScope.openTransactional(dataSource)) {
        scope.executeVoid(client ->
                client.update("UPDATE invoices SET processed = ? WHERE id = ?", true, invoiceId)
        );

        if (shouldCancel) {
            scope.rollback();
            return;
        }

        scope.commit();
    }
}
```

### Direct connection access

`getConnection()` is an explicit escape hatch for JDBC features `JdbcClient` does not cover (batch
statements, `CallableStatement`, driver-specific APIs). The connection is still owned by the
scope: do not close it, and do not call `commit()`, `rollback()`, or change auto-commit/read-only
state on it directly.

```java
void touchUser(Connection connection, long userId) throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement("UPDATE users SET touched = ? WHERE id = ?")) {
        statement.setBoolean(1, true);
        statement.setLong(2, userId);
        statement.executeUpdate();
    }
}

void run(DataSource dataSource, long userId) throws SQLException {
    try (ConnectionScope scope = ConnectionScope.open(dataSource)) {
        touchUser(scope.getConnection(), userId);
    }
}
```

## API reference

### `ConnectionScope`

| Member | Description |
|---|---|
| `static open(DataSource)` | Opens a non-transactional scope; the connection stays in auto-commit mode. |
| `static openTransactional(DataSource)` | Opens a read-write transactional scope; auto-commit is disabled. |
| `static openTransactional(DataSource, Mode)` | Opens a transactional scope with the given read-only/read-write mode. |
| `<T> execute(Function<JdbcClient, T>)` | Runs code against the scope's `JdbcClient` and returns a value. |
| `executeVoid(Consumer<JdbcClient>)` | Runs code against the scope's `JdbcClient` with no return value. |
| `commit()` | Commits the current transaction. Transactional scopes only. |
| `rollback()` | Rolls back the current transaction. Transactional scopes only. |
| `getState()` | Returns `ACTIVE`, `TERMINATING`, or `TERMINATED`. |
| `getConnection()` | Returns the scope-owned `Connection` for advanced/direct use. |
| `close()` | Releases the connection; idempotent. |

### `JdbcClient`

| Member | Description |
|---|---|
| `<T> query(String, RowMapper<T>, Object...)` | Runs a query and maps every row; returns `List<T>`, never `null`. |
| `<T> queryForObject(String, RowMapper<T>, Object...)` | Runs a query expected to return exactly one row. |
| `exists(String, Object...)` | Returns `true` if the query returns at least one row. |
| `update(String, Object...)` | Runs an INSERT/UPDATE/DELETE/DDL statement; returns the affected row count. |
| `updateReturningKey(String, Object...)` | Runs an INSERT and returns a generated key, or the affected row count if none is available. |

All `Object...` parameters are bound positionally via `PreparedStatement.setObject`.

### `Mode`

`READ_ONLY` and `READ_WRITE`, passed to `openTransactional(DataSource, Mode)`.

### `ConnectionScopeException`

Unchecked (`extends RuntimeException`); the only exception type this library throws. See below.

## Thread confinement

A `ConnectionScope` is not thread-safe and must only be used from the thread that created it.

- `execute`, `executeVoid`, `commit`, and `rollback` **enforce** this: calling any of them from a
  different thread, or after the scope has been terminated, throws `ConnectionScopeException`.
- `close()` and `getConnection()` do **not** enforce it. Calling either from a foreign thread
  compiles and runs without a checked failure, but is still a misuse of the API — the underlying
  `Connection` is not thread-safe either.

If concurrent work needs the database, open a separate `ConnectionScope` per thread or task. Do
not share one scope across threads, executors, or parallel streams.

## Error and exception behavior

- Every JDBC-level failure (`SQLException`) is wrapped in `ConnectionScopeException`, with the
  original `SQLException` set as the cause.
- `commit()` and `rollback()` failures propagate immediately as `ConnectionScopeException`.
- `queryForObject` throws `ConnectionScopeException` if a query returns zero rows or more than one
  row — cardinality is part of that method's contract, not an edge case you need to check for.
- If a `RuntimeException` or `Error` escapes the block passed to `execute`/`executeVoid` on a
  transactional scope, ScopeJDBC immediately attempts `rollback()` before rethrowing the original
  exception. The scope remains active either way. If the rollback attempt itself fails, that
  failure is attached to the original exception via `addSuppressed` rather than replacing it.
- Failures that occur in multiple steps of the same operation (for example: rollback fails, then
  restoring auto-commit also fails, then closing the connection also fails) are aggregated into
  one `ConnectionScopeException` — the first failure is the cause, the rest are suppressed
  exceptions — so no failure is silently dropped.
- `updateReturningKey` cannot distinguish "returned a generated key" from "returned an affected row
  count" through its return value alone; only rely on it when you know the driver and statement
  support generated keys.

## Design principles and limitations

ScopeJDBC is deliberately thin. It does not, and will not:

- generate SQL, provide a query DSL, or map result rows automatically — you write the SQL and the
  `RowMapper`;
- use reflection, dynamic proxies, or annotation processing;
- pool connections — bring your own `DataSource` (HikariCP, Tomcat JDBC, a container-managed pool,
  or a test data source all work);
- retry failed operations or batch statements automatically — every `JdbcClient` call is exactly
  one JDBC round trip. For batching, use `getConnection()` directly.

Known, intentional limitations:

- Thread confinement is not enforced for `close()` or `getConnection()` (see above).
- `updateReturningKey`'s return value is ambiguous, as noted above.
- There is currently no automated test suite in this repository (see [Development](#development)).

## Performance considerations

These are properties of the implementation, not measured benchmark results — this repository does
not currently ship a benchmark suite.

- Each `JdbcClient` call prepares a fresh `PreparedStatement` and closes it before returning;
  ScopeJDBC does not cache statements itself. Statement caching, if you want it, comes from your
  driver or connection pool underneath.
- Row mapping is a single forward pass over the `ResultSet` with no reflection and no intermediate
  object graph — the cost is whatever your `RowMapper` does, nothing more.
- Positional parameters are passed as `Object...`, so primitive arguments are boxed before being
  handed to `PreparedStatement.setObject`. This is a deliberate trade-off for a small,
  dependency-free API rather than a type-specific binding method per SQL type.

## Compatibility and API stability

The public API is exactly five types: `ConnectionScope`, `JdbcClient`, `RowMapper`, `Mode`, and
`ConnectionScopeException`. `ConnectionScope` is `sealed`, and its implementations are
package-private — the only way to obtain an instance is through its static factory methods, and
external code cannot implement or extend it. Changes to this public surface are treated as
breaking changes, not routine refactors; see [CONTRIBUTING.md](CONTRIBUTING.md).

## ScopeJDBC vs. plain JDBC vs. an ORM

| | Plain JDBC | ScopeJDBC | ORM (JPA/Hibernate, jOOQ, ...) |
|---|---|---|---|
| Connection/resource lifecycle | Manual, every call site | Owned by `ConnectionScope` | Managed by a session/framework |
| Transaction control | Manual `setAutoCommit`/`commit`/`rollback` + `try`/`finally` | Explicit `commit()`/`rollback()`, with rollback-on-exception and a close()-time safety net | Often implicit or declarative |
| SQL | You write it | You write it | Often generated, or expressed via a DSL |
| Result mapping | Manual `ResultSet` access | `RowMapper<T>`, one lambda per row shape | Reflection-based entity mapping |
| Abstraction overhead | None | Minimal — thin call-through, no caching | Significant — caching, dirty checking, lazy loading |

Reach for ScopeJDBC when you want to keep writing SQL and controlling transactions explicitly, but
without rewriting connection/resource boilerplate at every call site. Reach for an ORM when you
want entity mapping, a query DSL, or caching, and are fine with the added machinery that comes
with it.

## Development

```bash
mvn clean verify
```

This compiles the project and attaches source and Javadoc jars, failing the build if Javadoc
generation errors. There is currently no automated test suite; please compile-check your changes
and exercise them manually against a real `DataSource` before opening a pull request — see
[CONTRIBUTING.md](CONTRIBUTING.md) for the project's design philosophy and workflow.

## License and community

- **License:** [Apache License 2.0](LICENSE.txt)
- **Contributing:** [CONTRIBUTING.md](CONTRIBUTING.md)
- **Security:** [SECURITY.md](SECURITY.md)
- **Code of Conduct:** [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md)
