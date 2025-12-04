![Scope JDBC](/assets/scope-jdbc-banner-long.png)

[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE.txt)
[![Issues](https://img.shields.io/github/issues/LlamaSystems/connection-scope)](https://github.com/LlamaSystems/connection-scope/issues)
[![Language](https://img.shields.io/github/languages/top/LlamaSystems/connection-scope)](https://github.com/LlamaSystems/connection-scope)
[![Contributions Welcome](https://img.shields.io/badge/contributions-welcome-brightgreen.svg)](CONTRIBUTING.md)
[![Known Vulnerabilities](https://snyk.io/test/github/LlamaSystems/connection-scope/badge.svg)](https://snyk.io/test/github/LlamaSystems/connection-scope)

**ScopeJDBC — High-performance, explicit JDBC connection & transaction management.**<br>
The fastest and safest way to execute multiple queries on a single DB connection — maximizing throughput in everything
from short-lived request pipelines to long-running transactional flows. Full low-level JDBC control with explicit
commit/rollback and zero risk of lifecycle leaks.

**Zero reflection. Zero proxies. Zero annotations. Zero dependency.**

---

## Why ScopeJDBC?

JDBC is powerful — but misused by default:

- Silent auto-commits
- Multiple pooled connections in one logical flow
- Cross-repository transaction hazards
- Framework-imposed transaction models
- Unpredictable proxy + reflection behavior

ScopeJDBC fixes all of it with **one explicit, thread-confined Connection** you fully control:

- Exactly one connection per unit of work
- Explicit commit / rollback — never implicit
- Zero reflection, zero proxies, zero annotations
- No dependencies — pure, portable Java
- Guaranteed thread confinement by design
- Works anywhere: SE, Boot, microservices, CLI, servlet
- Deterministic, leak-free lifecycle

**You always know:**

| Behavior                             | Guaranteed by                                        |
|--------------------------------------|------------------------------------------------------|
| When a connection opens              | `open()`                                             |
| When it closes                       | `close()` / try-with-resources                       |
| Which queries share the same session | All inside the scope                                 |
| Commit / rollback timing             | Your explicit calls                                  |
| Thread safety                        | Enforcement: wrong thread → exception                |
| No connection leaks                  | `@MustBeClosed` (static analysis) + strong lifecycle |

---

## ❌ Real-world problem example

Typical service logic (Spring, JPA, repository pattern):

```java
public void deleteUser(Long id) {
    boolean exists = exists(id);            // Connection #1
    orderRepo.deleteByUserId(id);          // Connection #2
    paymentRepo.deleteByUserId(id);        // Connection #3
    deleteById(id);                        // Connection #4
}
```

Each call does:

```sql
GET → EXECUTE → RETURN (pool)
```

This causes:

- 4 different physical DB connections
- Hidden auto-commits
- Impossible to enforce one logical session

## ScopeJDBC solution

### Solution: One connection, shared session

```java
void main() {
    try (ConnectionScope scope = ConnectionScope.open(dataSource)) {
        scope.execute(c -> c.update("DELETE FROM users WHERE username = ?", "john.doe"));
        // delete....

        scope.execute(c -> c.query("SELECT * FROM users WHERE is_active = ?", new UserMapper(), true))
                .getAsList()
                .forEach(System.out::println);
    } // Single connection, closed once — always predictable
}
```

### Transactional — without magic

```java
void main() {
    try (ConnectionScope scope = ConnectionScope.openTransactional(dataSource)) {
        scope.execute(c -> c.update("UPDATE users SET active = false WHERE username = ?", "john"));
        scope.commit(); // explicit, required
    } // forget commit() → auto-rollback for safety
} 
```

### Fine-grained rollback

```java
void main() {
    try (ConnectionScope scope = ConnectionScope.openTransactional(dataSource)) {
        scope.execute(c -> c.update("DELETE FROM users WHERE active = true"));
        scope.commit();   // first part saved

        scope.execute(c -> c.update("DELETE FROM notifications WHERE message = ?", "Hi"));
        if (condition) { // custom condition
            scope.rollback(); // rollback only second part
        }
    }
}
```

### Read-only mode — DB enforced (when supported)

```java
void main() {
    try (ConnectionScope scope = ConnectionScope.openTransactional(dataSource, Mode.READ_ONLY)) {
        scope.execute(c -> c.query("SELECT 1", rs -> rs.getInt(1)));
    }
}
```

You can solve: one connection for multiple queries in spring with `@Transactional` annotation, but...

### Spring Transactional vs ScopeJDBC

| Feature                          | Spring `@Transactional`                                        | ScopeJDBC                                                           |
|----------------------------------|----------------------------------------------------------------|---------------------------------------------------------------------|
| Framework requirement            | Requires Spring context, proxies, AOP                          | Works anywhere with any JDBC `DataSource`                           |
| Dependencies                     | Spring Transaction Manager, AOP infra                          | **Zero** (optional compile-time annotation)                         |
| Supported DataSources            | Any, but usually Spring-managed                                | Any JDBC DataSource: HikariCP, Tomcat, SimpleDataSource, JNDI, etc. |
| Connection lifecycle             | Managed automatically (hidden); may open multiple¹ connections | Fully explicit: **exactly one** connection borrowed + returned      |
| Transaction scope                | Method-level proxy boundaries                                  | Any code block: multiple statements, nested logic                   |
| Internal/private method usage    | ❌ No transaction                                               | ✔ Always in transaction                                             |
| Per-statement rollback control   | Limited — `noRollbackFor` only works for exceptions            | Full control — rollback based on business logic or conditions       |
| Error handling / recovery        | Mostly global: any exception → rollback all                    | Fine-grained: commit/rollback independently within same scope       |
| Batch processing                 | Indirect (via JdbcTemplate / TransactionTemplate)              | Built-in — single connection ensures optimal batching               |
| Logging / auditing               | Hard to intercept real SQL boundaries                          | Can log every execution clearly                                     |
| Atomic operations outside Spring | ❌ Very hard                                                    | ✔ Simple: works in plain Java or any framework                      |
| Overhead                         | Proxy creation + reflection + context lookup                   | Minimal — direct JDBC call path                                     |
| Performance                      | Slightly slower per transaction                                | Fast — no reflection, no proxy hops                                 |

### Before: What Spring can do

Everything above can be achieved with Spring — but only through:

- Proxy wrapping
- Platform Transaction Manager
- Reflection & AOP interception
- Hidden lifecycle rules
- Annotation-driven behavior

### Positioning: Where ScopeJDBC wins

ScopeJDBC is not a replacement for declarative Spring-style cross-cutting transactions.
It is the superior tool when you need:

- High-performance JDBC
- Exact and visible DB session boundaries
- Portable code outside Spring
- Deterministic behavior under load
- Debuggable and testable infrastructure

Great for: CQRS handlers, microservices, CLI, repositories, DDD aggregates, schedulers, integration flows.


---

## License

This project is licensed under the **Apache License 2.0**. See the [LICENSE.txt](LICENSE.txt) file for details.

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

## Getting Started

### Maven

```xml

<dependency>
    <groupId>io.github.llamasystems</groupId>
    <artifactId>scope-jdbc</artifactId>
    <version>1.0.0</version>
</dependency>
```

### Gradle

```gradle
dependencies {
    implementation 'io.github.llamasystems:scope-jdbc:1.0.0'
}
```