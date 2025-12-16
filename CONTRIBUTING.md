# Contributing

Thank you for considering a contribution to this project!

By submitting code, documentation, or any other materials, you assert that you have the necessary rights to contribute the material and you agree that your contribution is made under the project’s license (GPL-3.0-or-later, see `LICENSE`). No separate DCO sign-off is required.

## Tech stack and requirements

- Gradle: 9.x
- Java: 21 (for building and running tests)
- Test framework: JUnit 5

## How to contribute

1. Fork the repository and create a topic branch.
   - Preferred branch name prefixes: `fix/...` or `feature/...` (not strictly enforced).
2. Make your changes following the guidelines below.
3. Ensure the build is green and tests pass locally.
4. Open a GitHub Pull Request (PR) against the main branch.
   - Clearly describe the problem, the approach you took, and any trade-offs.

## Contribution guidelines

- Dependencies: Avoid adding external dependencies if possible. Keep the project lightweight.
- Public API docs: All public methods must be documented (Javadoc).
- Testing: New functionality must be covered by unit tests. Add negative/edge cases where it makes sense.
- Code style: Follow standard Java conventions.

## Reporting bugs

When opening a bug report, please include a minimal reproducing example and the environment details. Run Gradle with `--stacktrace` and `--debug` and attach the relevant logs.

Please use the following template as a guide:

```
Title: <concise summary of the problem>

Description
-----------
What did you do? Include steps to reproduce and a minimal reproducing project (link or zipped attachment if possible).

Expected result
---------------
Describe what you expected to happen.

Actual result
-------------
Describe what actually happened.

Why it seems wrong
------------------
Explain why you believe the actual result is incorrect.

Environment
-----------
- OS and version: <e.g., macOS 15.0 / Windows 11 / Ubuntu 24.04>
- Java version: <output of `java -version`>
- Gradle version: <`gradle --version`>

Build/Run logs
--------------
Please attach relevant logs. Run with `--stacktrace` and `--debug` and include the output.
```

## License

By contributing to this repository, you agree that your contributions will be licensed under the GPL-3.0-or-later license.
