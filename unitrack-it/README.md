# unitrack-it — integration tasks

Spring-backed, on-demand operational/verification **tasks** that run against a real UniTrack
instance — instead of one-off shell scripts. Each task is a small JUnit `@SpringBootTest` on a
shared context that provides reusable building blocks (`UniTrackApiClient`, `Screenshots`). The
**target environment is a Spring config profile**, not a flag, so the same task runs against local,
lab, or prod.

Not published to Maven Central (it lives in the `default` reactor profile, which `-Prelease` drops).

## Environments (Spring profiles)

| Profile | Target | File |
|---|---|---|
| _(default)_ | `http://localhost:8080` (a locally-running instance) | `application.yml` |
| `lab` | `https://unitrack.lab.alexmond.org` | `application-lab.yml` |
| `prod` | `https://unitrack.alexmond.org` | `application-prod.yml` |

Closed-mode instances need a token for `/api/**` — set `UNITRACK_IT_TOKEN`.

## Running tasks

Tasks are tagged `it` and **excluded from a normal `verify`** (the module only compiles by default —
no browser or live target required). Clear the exclusion on the command line to run them (a CLI `-D`
wins and, unlike a `-P` profile, doesn't deactivate the `default` reactor profile):

```bash
# All tasks against the lab deploy
mvn -pl unitrack-it test -Dunitrack.it.excluded-groups= -Dspring.profiles.active=lab

# One task against prod
mvn -pl unitrack-it test -Dunitrack.it.excluded-groups= -Dspring.profiles.active=prod -Dtest=SmokeTasks
```

## Tasks

- **`SmokeTasks`** — public endpoints (`/actuator/health`, `/login`, `/`) respond on the target.
- **`ScreenshotTasks`** — capture public pages to `target/screenshots/` (self-skips when no browser).

## Adding a task

A task is a class on `ItConfig`'s context: inject `UniTrackApiClient` / `Screenshots`, tag it
`@Tag("it")`, and assert/capture. No new script, no new harness.

## Not yet (follow-ups)

- Ephemeral in-test boot of the web app (needs `unitrack-web` exposed as a plain library jar —
  exec-classifier + a Containerfile tweak).
- Authenticated-page screenshots (a login step that carries the session cookie into the browser).
- A typed ingest helper on `UniTrackApiClient` for API-driven seed/trigger tasks.
