# Branching workflow

`master` is the single long-lived product and integration branch for FuoEvolve.

Use one short-lived branch per logical change / pull request:

- `feature/<name>`
- `fix/<name>`
- `refactor/<name>`
- `test/<name>`
- `docs/<name>`
- `chore/<name>`
- `hotfix/<name>`
- `release/<version>` only for temporary release stabilization

Do not create permanent `dev`, `staging`, provider, player, UI, module, or agent branches. Agent-generated branches are working branches and must be merged or discarded rather than becoming permanent architecture boundaries.

Branch from the current `master`, merge back into `master`, then delete the branch. Stacked pull requests are allowed only for genuine temporary dependencies.

The authoritative roadmap and architecture planning must live on `master`. Use Git tags/releases for version history instead of permanent release branches.
