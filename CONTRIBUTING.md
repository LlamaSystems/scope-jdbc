# How to Contribute

Thank you for considering contributing to this project! There are many ways you can help improve it, including:

- Updating documentation
- Improving tests
- Fixing bugs
- Enhancing features
- Improving tooling and automation

## Building and verifying changes

This is a plain Maven project with zero runtime dependencies. To build and verify locally:

```bash
mvn clean verify
```

This compiles the project, attaches source and Javadoc jars, and fails the build if Javadoc
generation errors out. There is currently no automated test suite; please compile-check your
changes and, where practical, exercise them manually against a real `DataSource` (e.g. an
in-memory database) before opening a pull request. Contributions that add test coverage are
especially welcome.

## Design philosophy

ScopeJDBC stays intentionally small and close to JDBC. When contributing, please preserve:

- **No reflection, proxying, annotation processing, or other implicit/"magic" behavior.** Every
  code path should be traceable by reading the source.
- **Minimal dependencies.** The main artifact has none; avoid adding runtime dependencies unless
  there is no reasonable alternative.
- **A small, deliberate public surface.** `ConnectionScope`, `JdbcClient`, `RowMapper`, `Mode`,
  and `ConnectionScopeException` are the entire public API. New public types or methods should be
  proposed via an issue before a pull request, since the project treats binary/source
  compatibility as a hard constraint.
- **Sealed type hierarchies** for the scope implementations (`AbstractConnectionScope`,
  `DefaultScope`, `TransactionalScope`) — keep new scope variants exhaustive and package-private
  unless there's a strong reason to expose them.
- **Exceptions must remain observable and precise.** Failures during rollback, connection-state
  restoration, or close are aggregated via cause + suppressed exceptions rather than swallowed; if
  you touch this logic, preserve that a caller can inspect every failure that occurred, not just
  the first.

## Recommended Workflow

Here’s a step-by-step guide to contributing to this project:

1. **Find or Create an Issue**
    - Check the project's issue tracker (e.g., GitHub Issues) to see if there’s already an issue describing the work
      you’d like to do.
    - If no issue exists, create one to describe the problem or feature you want to work on. This helps coordinate
      efforts and ensures everyone is on the same page.

2. **Fork the Repository**
    - Create a personal fork of the project’s repository on GitHub (or your preferred Git hosting platform).

3. **Clone Your Fork**
    - Clone your fork into your local development environment:
      ```bash
      git clone https://github.com/LlamaSystems/scope-jdbc.git
      cd scope-jdbc
      ```

4. **Create a Feature Branch**
    - Create a new branch for your changes. Use a descriptive name for your branch to make it easier to track. For
      example:
      ```
      your-name/issue-description/base-branch
      ```
      Example:
      ```
      jcshepherd/update-readme/main
      ```
      This convention helps manage multiple collaborators and keeps branches organized.

5. **Make Your Changes**
    - Implement your changes, ensuring they follow the project’s coding standards and guidelines.
    - Build and test your changes locally to ensure they work as expected.
    - Perform a self-review of your code before submitting it.

6. **Submit Your Changes**
    - Push your feature branch to your fork:
      ```bash
      git push origin your-name/issue-description/base-branch
      ```
    - Open a Pull Request (PR) against the main repository. Provide a clear description of your changes and reference
      the relevant issue (if applicable).
    - If you’re using GitHub, you can directly link your PR to the issue by mentioning the issue number in the PR
      description (e.g., `Fixes #123`).

7. **Code Review**
    - Participate in the code review process by addressing feedback from maintainers or other contributors.
    - Once your changes are approved, they will be merged into the main branch.