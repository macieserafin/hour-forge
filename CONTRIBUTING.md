# Contributing to HourForge

Thank you for helping. This page covers how to set up, what to check before you open a pull request and how to write
commit messages.

## Before you start

- Look through the [open issues](https://github.com/macieserafin/hour-forge/issues). For a larger change, open an issue
  first and describe what you want to do, so we can agree on the direction before you write code.
- Report security problems privately, as described in [SECURITY.md](SECURITY.md).
- Read [Developing HourForge](docs/development.md) for the local setup and [Architecture](docs/architecture.md) for the
  layout of the code.

## Submit a change

1. Fork the repository and clone your fork.
2. Create a branch from `main`, one branch per fix or feature:

   ```bash
   git checkout -b fix-timer-rounding
   ```

3. Make the change, with tests. Reproduce a bug in a test before you fix it. Cover permissions and error cases, not only
   the happy path.
4. Run the checks for the part you changed (below).
5. Commit, push to your fork and open a pull request against `main`. Fill in the pull request template.

A maintainer reviews each pull request. CI must pass before merge.

## Checks to run

| Changed | Run |
|---|---|
| `backend` | `./mvnw verify` in `backend` (Docker must run) |
| `frontend` | `npm run lint`, `npm run typecheck`, `npm test` and `npm run build` in `frontend` |
| User flows | `npm run test:e2e -- --project=desktop <file>` for the flows you changed |
| Database schema | A new Flyway migration; never edit one that is already on `main` |
| `deploy`, `docs` | Check that commands and links still work |

Do not weaken a test, a validation or a security setting to get a green result. If you think a check is wrong, say so
in the pull request.

## Commit messages

Use one line: `type: a full sentence in English`.

```
feat: let owners and admins give a team a colour
fix: keep a timer running when the browser reloads
docs: explain how to rotate the two-step sign-in key
```

- Types: `feat`, `fix`, `test`, `docs`, `chore`, `refactor`.
- No scope in parentheses and no body under the title. One commit covers a whole change: API, web app and tests.
- No issue or ticket numbers in the message. Link the issue in the pull request instead.
- Keep history linear: rebase on `main` instead of merging `main` into your branch.

## Code style

- Backend: follow the style of the surrounding Java code. Put new behavior in the package that owns the area.
- Frontend: Biome sets formatting and lint rules. `npm run format` applies them.
- Write user-facing text and documentation in plain, direct English. Name the actor, use the active voice and skip
  filler.

## Documentation

Change the docs in the same pull request as the behavior they describe. Settings go in the
[settings table](docs/self-hosting.md#settings), operational steps in [`docs/operations`](docs/operations).

## License

By contributing, you agree that your work is released under the [MIT License](LICENSE).
