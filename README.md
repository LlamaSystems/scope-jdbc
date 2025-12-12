![Scope JDBC](/assets/scope-jdbc-banner-long.png)

[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE.txt)
[![Issues](https://img.shields.io/github/issues/LlamaSystems/connection-scope)](https://github.com/LlamaSystems/connection-scope/issues)
[![Language](https://img.shields.io/github/languages/top/LlamaSystems/connection-scope)](https://github.com/LlamaSystems/connection-scope)
[![Contributions Welcome](https://img.shields.io/badge/contributions-welcome-brightgreen.svg)](CONTRIBUTING.md)
[![Known Vulnerabilities](https://snyk.io/test/github/LlamaSystems/connection-scope/badge.svg)](https://snyk.io/test/github/LlamaSystems/connection-scope)

**ScopeJDBC — Explicit, High-Performance JDBC Session & Transaction Control**<br>
The fastest and safest way to execute multiple queries on a single DB connection, maximizing throughput for everything
from short-lived request pipelines to long-running transactional flows. ScopeJDBC provides deterministic, low-overhead
JDBC connection and transaction management with **zero reflection, zero proxies, zero annotations, and zero
dependencies.**

**ScopeJDBC** is engineered for maximum efficiency on the JVM under real workloads.
Its design minimizes CPU cost, memory allocation, GC pressure, and synchronization overhead.
It provides full observability, full explicitness, and low-level control-ideal for systems where
performance, determinism, and clarity matter more than abstraction layers.
It does not replace ORMs, mappers, or SQL builders.
ScopeJDBC is the foundation for building predictable, high-performance database workflows.
---

## Features

### Connection Scope

- Exactly one JDBC Connection per scope
- Fully explicit lifecycle boundaries
- Compatible with any JDBC DataSource (HikariCP, Tomcat, JNDI, pgSimple, etc.)

### Transaction Scope

- Explicit openTransactional()
- Mandatory commit (auto-rollback for safety)
- Fine-grained rollback segments
- Complex, multi-step operations within the same physical session

### Execution Model

- Thin, allocation-friendly API
- Direct access to low-level JDBC primitives
- Deterministic error behavior
- Clean functional execution: `scope. execute (c -> ...)`

### No Magic

- No reflection
- No proxies
- No AOP
- No annotation processing
- No hidden lifecycle behavior

### Runs Everywhere

- Any relational database
- Spring or non-Spring environments
- Microservices, CLIs, schedulers
- Serverless runtimes
- DDD/CQRS layers
- High-throughput job processors
- AI/ML training and inference pipelines

---

### Benchmark Summary (High-Level)

![Heavy Multi-Row Select](/assets/HeavySelect_Database_Benchmark_Results_Analysis_2025.png)
![Mix DML](/assets/MixDML_Database_Benchmark_Results_Analysis_2025.png)

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

### Basic Usage - One connection, shared session

```java
try (ConnectionScope scope = ConnectionScope.open(dataSource)) {
    scope.execute(c -> c.update("DELETE FROM users WHERE username = ?", "john.doe"));

    List<User> users = scope.execute(c ->
        c.query("SELECT * FROM users WHERE active = ?", new UserMapper(), true)
    ).getAsList();
}
```

### Transactional Usage - Fully Explicit Workflow

```java
try (ConnectionScope scope = ConnectionScope.openTransactional(dataSource)) {

    scope.execute(c -> c.update("UPDATE accounts SET balance = balance - ? WHERE id = ?", 100, 42));
    scope.execute(c -> c.update("UPDATE accounts SET balance = balance + ? WHERE id = ?", 100, 84));

    scope.commit();
} // missing commit → automatic rollback
```

### Partial Rollback Segments

```java
try (ConnectionScope scope = ConnectionScope.openTransactional(dataSource)) {
    scope.execute(c -> c.update("DELETE FROM orders WHERE archived = false"));
    scope.commit(); // first phase persisted

    scope.execute(c -> c.update("DELETE FROM logs WHERE level = 'DEBUG'"));

    if (condition) {
        scope.rollback(); // revert second phase
    }
}
```

### Read-Only Transaction

```java
try (ConnectionScope scope = ConnectionScope.openTransactional(dataSource, Mode.READ_ONLY)) {

    long count = scope.execute(c -> c.query("SELECT COUNT(*) FROM orders WHERE status = 'SHIPPED'",
                rs -> rs.getLong(1))
    ).get();
}
```

### Error Semantics

- SQLExceptions propagate directly (no wrapping unless you add mapping)
- Commit/rollback failures surface immediately
- Safe cleanup on all failure paths
- Thread confinement guarantees prevent cross-thread misuse
- No hidden retry/rollback semantics

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