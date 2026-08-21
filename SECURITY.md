# Security Policy

## Scope

ScopeJDBC is a thin layer over `java.sql`/`javax.sql`: it does not perform network I/O,
deserialization, reflection, or dynamic code generation of its own. The library's own attack
surface is limited to connection lifecycle and statement-parameter binding (`PreparedStatement`
with positional `setObject` calls; ScopeJDBC never concatenates caller-supplied values into SQL
text). Most real-world risk in applications using ScopeJDBC originates from the caller-supplied
`DataSource`/JDBC driver, connection credentials and pooling configuration, or from SQL text
constructed insecurely by the calling application — please direct reports about those to the
relevant driver, pool, or application project. Reports specific to ScopeJDBC's own code
(connection handling, transaction control, exception aggregation) are welcome here.

## Supported Versions

Security fixes are provided for the latest released minor version. Users are encouraged to stay
on the most recent release available on Maven Central.

## Reporting a Vulnerability

We take the security of this project seriously. If you discover a security vulnerability, please report it responsibly
by following these steps:

1. **Do not** disclose the vulnerability publicly (e.g., in GitHub Issues, forums, or social media).
2. Send an email to our security team at `aliabbosashurov.forwork@gmail.com` with the following details:
    - A clear and concise description of the vulnerability.
    - Steps to reproduce the issue.
    - Any relevant code snippets, screenshots, or logs.
3. We will acknowledge receipt of your report within 5 business days and work with you to resolve the issue promptly.

## Responsible Disclosure

We appreciate your efforts to disclose security issues responsibly. Once a vulnerability has been resolved, we will
credit your contribution (if desired) and provide a timeline for public disclosure.

## Acknowledgments

We thank all security researchers and contributors who help improve the safety and integrity of this project.

For more information, see our [Code of Conduct](CODE_OF_CONDUCT.md).