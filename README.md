# ConnectionScope

**High-performance, explicit JDBC connection & transaction management.**

**The fastest and safest way to execute multiple queries on a single DB connection** — delivering maximum performance
from short-lived request flows to long-running transactions, with full low-level JDBC power, explicit commit/rollback
control, and zero lifecycle-leak risk.

✨ **Zero reflection. Zero proxies. Zero annotations. Zero dependency\*.**

---

## Why ConnectionScope?

JDBC is powerful but easy to misuse:

- Hidden auto-commits
- Multiple pooled connections inside one workflow
- Hard-to-reproduce bugs across repository boundaries
- Transaction rules tied to frameworks (Spring)
- Magic behaviors with proxies & reflection

**ConnectionScope eliminates all of these problems** with a **single, thread-confined connection** that you fully
control:

- Exactly one connection per unit of work
- Explicit commit/rollback
- Zero AOP, zero proxy overhead
- No runtime dependency — 100% pure Java
- Guaranteed thread confinement
- Works anywhere: Java SE, Spring Boot, microservices, CLI, servlet container
- Predictable leak-free lifecycle

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
GET
→
EXECUTE
→ RETURN (pool)
```

This causes:

- 4 different physical DB connections
- Hidden auto-commits
- Impossible to enforce one logical session

## ConnectionScope solution

### Solution: One connection, shared session

```java
void main() {
    try (ConnectionScope scope = ConnectionScope.open(dataSource)) {
        scope.execute(c ->
                c.update("DELETE FROM users WHERE username = ?", "john.doe")
        );

        scope.execute(c ->
                c.query("SELECT * FROM users WHERE is_active = ?", new UserMapper(), true)
        ).getAsList().forEach(System.out::println);
    }
// Single connection, closed once — always predictable
}
```

### Transactional — without magic

```java
void main() {
    try (ConnectionScope scope = ConnectionScope.openTransactional(dataSource)) {
        scope.execute(c ->
                c.update("UPDATE users SET active = false WHERE username = ?", "john")
        );
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
        scope.rollback(); // rollback only second part
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

Prevents accidental writes (DB-enforced when supported).

### Spring Transactional vs ConnectionScope

| Feature                          | Spring `@Transactional`                                        | ConnectionScope                                                     |
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

### Positioning: Where ConnectionScope wins

ConnectionScope is not a replacement for declarative Spring-style cross-cutting transactions.
It is the superior tool when you need:

- High-performance JDBC
- Exact and visible DB session boundaries
- Portable code outside Spring
- Deterministic behavior under load
- Debuggable and testable infrastructure

Great for: CQRS handlers, microservices, CLI, repositories, DDD aggregates, schedulers, integration flows.

## Installation

```xml

<dependency>
    <groupId>io.github.llamasystems</groupId>
    <artifactId>connection-scope</artifactId>
    <version>1.0.0</version>
</dependency>

```

## ⭐ If you like this project, please star the repo!