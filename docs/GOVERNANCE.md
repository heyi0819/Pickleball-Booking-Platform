# Repository Governance

`main` is protected by an active GitHub repository ruleset.

- Changes enter `main` through a pull request.
- Pull request conversations must be resolved before merging.
- The branch must be up to date and these checks must pass: `Backend build and test`, `Frontend checks`, `Clean and forward PostgreSQL migration`, `Container and optional GCP reference validation`, and `Zero-cost production-like smoke`.
- The current solo-maintainer policy requires zero approvals. A future maintainer review policy may raise this requirement after a non-author reviewer is available.
- Merge commits are the default for product slices and multi-commit infrastructure work. Squash merge remains available for small, focused maintenance or documentation changes. Rebase merge is disabled.
- Force pushes and deletion of `main` are blocked. There are no permanent bypass actors.

Deployment, environment secrets, and production operations remain separately authorized workflows and are not granted by this policy.
