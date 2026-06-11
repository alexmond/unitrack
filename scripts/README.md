# scripts/

Helper scripts for developing, deploying, and feeding UniTrack. All are POSIX `bash`,
`set -euo pipefail`, and `cd` to the repo root, so they can be run from anywhere
(`scripts/<name>.sh …`). Each script's own header comment is the authoritative usage.

| Script | Purpose |
|---|---|
| `dev-verify.sh` | Local pre-commit build that mirrors CI (format + full `verify`). |
| `dev-test.sh` | Run one test / a pattern without a full reactor verify. |
| `deploy-remote.sh` | Build the image **on a remote Docker host over SSH** and deploy the compose stack. |
| `unitrack-upload.sh` | Dependency-free CI uploader (JUnit + JaCoCo → a UniTrack server). |
| `unitrack-gate.sh` | Dependency-free CI quality-gate check (exit 1 when the gate fails). |

## Development

### `dev-verify.sh`
Applies `spring-javaformat` then runs the whole-reactor `verify` (compile, Checkstyle,
PMD, tests, JaCoCo 80% gate) — a green run here means a green PR. Extra args pass through
to Maven.

```bash
scripts/dev-verify.sh                      # whole reactor
scripts/dev-verify.sh -pl unitrack-web -am # forward args to Maven
```

### `dev-test.sh`
Runs a targeted `-Dtest` selector without a full verify. The **last** argument is the
test selector; anything before it is forwarded to Maven. Defaults to `unitrack-web -am`.
Routing through this script keeps it under the allowlisted `Bash(./scripts/*)` permission.

```bash
scripts/dev-test.sh CoveragePageIntegrationTest
scripts/dev-test.sh 'Gate*,*RegressionIntegrationTest'
scripts/dev-test.sh -pl unitrack-core MyCoreTest
```

## Deployment

### `deploy-remote.sh`
Builds the Spring Boot image **on the remote Docker daemon over SSH** and brings up the
compose stack there — no registry, no `docker save | load`, no local Docker needed.

Because Spring Boot's `build-image` uses the **docker-java** client (which does **not**
speak `ssh://`), the script forwards the remote `/var/run/docker.sock` over SSH to a local
unix socket and points `DOCKER_HOST` at it. The buildpacks build and `docker compose up`
then both run against the remote daemon. The image itself is built by **Cloud Native
Buildpacks (Paketo)** via `-Pdocker` — there is no Dockerfile.

```bash
scripts/deploy-remote.sh                                   # h2 stack → root@192.168.100.132:8081
scripts/deploy-remote.sh --host user@host --stack postgres --port 8080
scripts/deploy-remote.sh --no-build                        # reuse the image already on the host
```

Requires: SSH access to the host and a Docker daemon running there. Compose files live in
[`../deploy/`](../deploy). The h2 stack is a throwaway demo (stable creds, pre-seeded
sample projects when `UNITRACK_DEMO_ENABLED=true`); use the postgres stack for anything real.

## CI helpers (dependency-free)

These are the portable `bash`/`curl` fallbacks for any CI (GitLab, Jenkins, Buildkite,
local hooks). The richer path is the `unitrack-cli` runnable JAR / Docker image / GitHub
Action, but these need nothing but `bash` + `curl`.

### `unitrack-upload.sh`
Uploads JUnit (Surefire) + JaCoCo reports to a server. `--junit`/`--jacoco` take repeatable
glob patterns (quote them). Only `--project` and one `--junit` file are required.

```bash
UNITRACK_URL=http://localhost:8080 scripts/unitrack-upload.sh \
  --project myapp --branch "$GITHUB_REF_NAME" --commit "$GITHUB_SHA" \
  --junit 'target/surefire-reports/TEST-*.xml' --jacoco 'target/site/jacoco/jacoco.xml'
```

### `unitrack-gate.sh`
Looks up the latest run for a project + commit (or branch) and exits non-zero if the
quality gate did not pass — drop it after the upload step to fail a build on a gate breach.

```bash
UNITRACK_URL=http://localhost:8080 scripts/unitrack-gate.sh --project myapp --commit "$GIT_SHA"
scripts/unitrack-gate.sh --project myapp --branch main
```

Set `UNITRACK_TOKEN` for either uploader/gate script when the server requires auth.
