![Scope JDBC](/assets/scope-jdbc-banner-long.png)

[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE.txt)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.llamasystems/scope-jdbc.svg)](https://central.sonatype.com/artifact/io.github.llamasystems/scope-jdbc)
[![Issues](https://img.shields.io/github/issues/LlamaSystems/scope-jdbc)](https://github.com/LlamaSystems/scope-jdbc/issues)
[![Language](https://img.shields.io/github/languages/top/LlamaSystems/scope-jdbc)](https://github.com/LlamaSystems/scope-jdbc)
[![Contributions Welcome](https://img.shields.io/badge/contributions-welcome-brightgreen.svg)](CONTRIBUTING.md)

# ScopeJDBC

**Explicit JDBC connection and transaction scoping for applications that want low-level control without framework magic.
**

ScopeJDBC is a small library for running multiple JDBC operations on the same physical `Connection` with explicit
lifecycle and transaction boundaries.

It is designed for teams that want:

- predictable connection ownership
- explicit transaction control
- minimal abstraction over JDBC
- lightweight internals
- clean integration with any `DataSource`

ScopeJDBC does **not** try to be an ORM, SQL builder, or repository framework.  
It stays intentionally close to JDBC while removing repetitive connection and transaction boilerplate.

---

## Why ScopeJDBC

Plain JDBC is powerful, but once an application needs to execute multiple operations on the same connection, transaction
management and cleanup logic tend to spread across the codebase.

Typical problems include:

- connection acquisition and release repeated everywhere
- inconsistent commit/rollback behavior
- accidental mixing of transactional and auto-commit flows
- hard-to-read low-level lifecycle code
- hidden behavior introduced by larger frameworks

ScopeJDBC gives you one clear unit of work:

- open a scope
- execute operations on the same connection
- commit or rollback when needed
- close the scope

That is the entire model.

---

## Core characteristics

### One connection per scope

Each `ConnectionScope` owns exactly one JDBC `Connection` for its full lifetime.

### Explicit lifecycle

A scope is opened explicitly and closed explicitly, typically via try-with-resources.

### Explicit transaction control

Transactional scopes disable auto-commit and require explicit `commit()` or `rollback()`.

### Minimal abstraction

The public API is intentionally small and remains close to JDBC concepts.

### No hidden runtime behavior

There is no reflection, proxying, AOP, annotation processing, or implicit transaction model.

### Works with any JDBC `DataSource`

Use it with HikariCP, Tomcat JDBC Pool, JNDI data sources, container-managed data sources, test data sources, or custom
implementations.

---

## API overview

Main public types:

- `ConnectionScope`
- `JdbcClient`
- `RowMapper`
- `Mode`
- `ConnectionScopeException`

Main entry points:

- `ConnectionScope.open(dataSource)`
- `ConnectionScope.openTransactional(dataSource)`
- `ConnectionScope.openTransactional(dataSource, Mode.READ_ONLY)`
- `scope.execute(client -> ...)`
- `scope.commit()`
- `scope.rollback()`

---

## Installation

### Maven

```xml
<dependency>
    <groupId>io.github.llamasystems</groupId>
    <artifactId>scope-jdbc</artifactId>
    <version>2.0.0</version>
</dependency>
```

### Gradle

```kotlin
dependencies {
    implementation "io.github.llamasystems:scope-jdbc:2.0.0"
}
```

## Quick start

### Non-transactional scope

Use open(...) when auto-commit behavior is sufficient.

```java
import io.github.llamasystems.scopejdbc.ConnectionScope;

import javax.sql.DataSource;
import java.util.List;

public final class UserRepository {

    private final DataSource dataSource;

    public UserRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public List<String> findActiveUsernames() {
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
}
```

### Transactional scope

Use openTransactional(...) when multiple operations must succeed or fail together.

```java
import io.github.llamasystems.scopejdbc.ConnectionScope;

import javax.sql.DataSource;

public final class TransferService {

    private final DataSource dataSource;

    public TransferService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void transfer(long fromAccountId, long toAccountId, int amount) {
        try (ConnectionScope scope = ConnectionScope.openTransactional(dataSource)) {
            scope.execute(client -> {
                client.update(
                        "UPDATE accounts SET balance = balance - ? WHERE id = ?",
                        amount,
                        fromAccountId
                );

                client.update(
                        "UPDATE accounts SET balance = balance + ? WHERE id = ?",
                        amount,
                        toAccountId
                );

                return null;
            });

            scope.commit();  // <-- If commit() is not called, the transactional scope rolls back uncommitted work during close().
        }
    }
}
```

## Usage guide

### Query rows

JdbcClient.query(...) returns a List<T>.

```java
List<User> users = scope.execute(client ->
        client.query(
                "SELECT id, username, active FROM users WHERE active = ?",
                rs -> new User(
                        rs.getLong("id"),
                        rs.getString("username"),
                        rs.getBoolean("active")
                ),
                true
        )
);
public record User(long id, String username, boolean active) {
}
```

### Execute updates

JdbcClient.update(...) returns the affected row count.

```java
int updated = scope.execute(client ->
        client.update(
                "UPDATE users SET active = ? WHERE id = ?",
                false,
                42L
        )
);
```

### Insert and read generated key

JdbcClient.updateReturningKey(...) requests generated keys and returns:

- the first generated key if the driver provides one
- otherwise the affected row count

```java
int id = scope.execute(client ->
        client.updateReturningKey(
                "INSERT INTO accounts(email) VALUES (?)",
                "user@example.com"
        )
);
```

### Multi-phase transactional workflow

A scope remains active after commit() or rollback().
This allows staged workflows on the same connection.

```java
try (ConnectionScope scope = ConnectionScope.openTransactional(dataSource)) {
    scope.execute(client -> {
        client.update("DELETE FROM sessions WHERE expires_at < CURRENT_TIMESTAMP");
        return null;
    });
    scope.commit();

    scope.execute(client -> {
        client.update("DELETE FROM audit_logs WHERE created_at < CURRENT_TIMESTAMP - INTERVAL '30 days'");
        return null;
    });
    scope.commit();
}
```

### Manual rollback

```java
try (ConnectionScope scope = ConnectionScope.openTransactional(dataSource)) {
    scope.execute(client -> {
        client.update(
                "UPDATE invoices SET processed = ? WHERE id = ?",
                true,
                invoiceId
        );
        return null;
    });

    if (shouldCancel) {
        scope.rollback();
        return;
    }

    scope.commit();
}
```

### Read-only transactional scope

For drivers and databases that support it, a transactional scope can be opened in read-only mode.

```java
import io.github.llamasystems.scopejdbc.Mode;

try (ConnectionScope scope = ConnectionScope.openTransactional(dataSource, Mode.READ_ONLY)) {
    List<Long> counts = scope.execute(client ->
            client.query(
                    "SELECT COUNT(*) FROM orders WHERE status = ?",
                    rs -> rs.getLong(1),
                    "SHIPPED"
            )
    );

    long count = counts.isEmpty() ? 0L : counts.get(0);
    scope.commit();

    return count;
}
```

## Advanced usage

### Direct access to the underlying connection

```java
ConnectionScope.getConnection() is available as an explicit escape hatch for advanced JDBC usage.
try (ConnectionScope scope = ConnectionScope.open(dataSource)) {
    Connection connection = scope.getConnection();

    try (PreparedStatement stmt =
                 connection.prepareStatement("UPDATE users SET touched = ? WHERE id = ?")) {
        stmt.setBoolean(1, true);
        stmt.setLong(2, userId);
        stmt.executeUpdate();
    }
}
```

This is intentionally supported, but with important rules:
- do not close the connection manually
- do not call commit() or rollback() on the connection directly
- do not change auto-commit, read-only mode, or other connection-level transactional settings unless you fully understand the consequences
- prefer JdbcClient unless you genuinely need direct JDBC access

---

## Error model

ScopeJDBC uses ConnectionScopeException as its runtime failure type for:
- connection acquisition failures
- statement execution failures
- commit/rollback failures
- cleanup failures

General behavior:
- SQL execution failures are wrapped in ConnectionScopeException
- commit() and rollback() failures surface immediately
- transactional scopes roll back uncommitted work on close()
- if a RuntimeException or Error escapes scope.execute(...) in a transactional scope, ScopeJDBC attempts to roll back the current uncommitted work before rethrowing the original failure
- suppressed exceptions may be attached when rollback or close also fails

Example:

```java
try (ConnectionScope scope = ConnectionScope.openTransactional(dataSource)) {
    scope.execute(client -> {
        client.update("UPDATE accounts SET balance = balance - ? WHERE id = ?", 100, 1);
        throw new IllegalStateException("business rule failed");
    });
    scope.commit();
}
```

In this case, the original exception is rethrown, and ScopeJDBC attempts rollback first.

## Threading model

`ConnectionScope` instances are not thread-safe and are intentionally thread-confined.

A scope must only be used by the thread that created it. This includes:

- `execute(...)`
- `commit()`
- `rollback()`
- `close()`
- direct use of `getConnection()`

Do not pass a scope to worker threads, executors, async callbacks, or parallel streams.

If concurrent work needs database access, open a separate `ConnectionScope` per thread or task.

---

## License

This project is licensed under the **Apache License 2.0**. See the [LICENSE](LICENSE.txt) file for details.

---

## Code of Conduct

We are committed to fostering an open and welcoming environment. Please read our [Code of Conduct](CODE_OF_CONDUCT.md)
to understand the standards of behavior expected in this community.

---

## Contributing

We welcome contributions from everyone! Whether you're fixing bugs, improving documentation, or adding new features,
your help is appreciated. Please read our [Contributing Guidelines](CONTRIBUTING.md) to get started.

---

## Security

If you discover a security vulnerability, please follow our [Security Policy](SECURITY.md) to report it responsibly.

---