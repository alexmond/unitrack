# UniTrack Helm chart

Deploys UniTrack on Kubernetes, with an optional bundled PostgreSQL (toggleable — point at an
external database for production). Self-contained: no external chart dependencies.

## Prerequisites

- Kubernetes 1.24+ and Helm 3.
- A container image of UniTrack in a registry your cluster can pull from.

## 1. Build & publish the image

The `docker` Maven profile builds the image with Cloud Native Buildpacks (no Dockerfile):

```bash
# Publish to a registry the cluster can reach (example: the home Zot registry).
docker login nas1.home.int:5000          # alexm / <password>
./mvnw -Pdocker -pl unitrack-web -am package \
  -Ddocker.image.name=nas1.home.int:5000/unitrack:0.1.0-SNAPSHOT \
  -Ddocker.publish=true
```

For GHCR use `-Ddocker.image.name=ghcr.io/alexmond/unitrack:<tag>` and `docker login ghcr.io`.

## 2. Install

Bundled PostgreSQL (default — good for evaluation and homelab):

```bash
helm upgrade --install unitrack deploy/helm/unitrack \
  -n unitrack --create-namespace \
  --set image.repository=nas1.home.int:5000/unitrack \
  --set image.tag=0.1.0-SNAPSHOT
```

External database (production-style):

```bash
helm upgrade --install unitrack deploy/helm/unitrack \
  -n unitrack --create-namespace \
  --set postgresql.enabled=false \
  --set externalDatabase.url='jdbc:postgresql://db:5432/unitrack' \
  --set externalDatabase.user=unitrack \
  --set externalDatabase.existingSecret=unitrack-db \
  --set externalDatabase.existingSecretPasswordKey=password
```

Home k3s cluster: use the ready-made overrides:

```bash
helm upgrade --install unitrack deploy/helm/unitrack \
  -n unitrack --create-namespace -f deploy/helm/unitrack/values-homelab.yaml
```

## 3. Verify

```bash
kubectl -n unitrack rollout status deploy/unitrack
helm test unitrack -n unitrack          # hits /actuator/health
```

## Key values

| Key | Default | Notes |
|---|---|---|
| `image.repository` / `image.tag` | `ghcr.io/alexmond/unitrack` / chart appVersion | Where the cluster pulls the app image. |
| `imagePullSecrets` | `[]` | Pull secret(s) for a private registry. |
| `postgresql.enabled` | `true` | `false` → use `externalDatabase.*`. |
| `postgresql.persistence.storageClass` | `""` (cluster default) | On NFS `all_squash`, set a node-local class (e.g. `local-path`). |
| `externalDatabase.url` | `""` | Required JDBC URL when `postgresql.enabled=false`. |
| `externalDatabase.existingSecret` | `""` | Bring your own Secret for the DB password. |
| `ingress.enabled` | `false` | Set host/className/tls to expose the UI. |
| `app.security.openMode` | `true` | `false` → UI requires login, API requires a token. Set `false` when exposing externally. |
| `app.security.requireIngestToken` | `false` | `true` → `POST /api/v1/ingest` always needs a token (CI must authenticate). |
| `secret.adminPassword` | `""` | Seeded admin password; empty = auto-generated and printed in the pod log. |
| `app.github.enabled` | `false` | GitHub commit-status; token via `secret.githubToken`. |
| `app.notifications.*` | disabled | Email notifications; SMTP host via `extraEnv`. |
| `resources.limits.memory` | `1Gi` | The buildpack JVM sizes its heap from this. |

See [`values.yaml`](values.yaml) for the full, commented set.

## Notes

- **Docker Compose lifecycle is force-disabled** in-cluster (`SPRING_DOCKER_COMPOSE_ENABLED=false`)
  — the app enables it by default for local dev.
- The DB password is auto-generated on first install and persisted in the chart Secret across
  upgrades (set `postgresql.auth.password` to pin it).
- Liveness/readiness use the app's actuator probe endpoints; a generous `startupProbe` covers
  Flyway migrations on first boot.
