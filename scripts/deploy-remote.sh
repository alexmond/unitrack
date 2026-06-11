#!/usr/bin/env bash
#
# Build the Spring Boot image on a remote Docker host over SSH and deploy it — no registry,
# no `docker save | load`. Spring Boot's build-image uses the docker-java client, which does
# NOT speak ssh://, so we forward the remote Docker socket over SSH to a local unix socket and
# point DOCKER_HOST at it; the buildpacks build (and compose up) then run on the remote daemon.
#
# Usage:
#   scripts/deploy-remote.sh [options]
#     --host USER@HOST   remote docker host over SSH   (default: root@192.168.100.132)
#     --stack h2|postgres                              (default: h2)
#     --port N           host port to publish          (default: 8081)
#     --project NAME     compose project name          (default: unitrack)
#     --no-build         reuse the image already on the remote (skip the buildpacks build)
#
# Requires: SSH access to the host and a Docker daemon on the host (the build runs there).
# A local Docker daemon is NOT needed.

set -euo pipefail
cd "$(dirname "$0")/.."

HOST=root@192.168.100.132
STACK=h2
PORT=8081
PROJECT=unitrack
BUILD=1

while [[ $# -gt 0 ]]; do
  case "$1" in
    --host)     HOST="$2"; shift 2 ;;
    --stack)    STACK="$2"; shift 2 ;;
    --port)     PORT="$2"; shift 2 ;;
    --project)  PROJECT="$2"; shift 2 ;;
    --no-build) BUILD=0; shift ;;
    -h|--help)  sed -n '2,21p' "$0"; exit 0 ;;
    *) echo "Unknown argument: $1" >&2; exit 2 ;;
  esac
done

COMPOSE="deploy/compose.${STACK}.yaml"
[[ -f "$COMPOSE" ]] || { echo "No compose file: $COMPOSE" >&2; exit 1; }
HOST_ADDR="${HOST#*@}"

# Forward the remote Docker socket to a local unix socket over SSH (via a control master so
# the tunnel can be torn down cleanly on exit).
SOCK="${TMPDIR:-/tmp}/unitrack-deploy-${PROJECT}.sock"
CTL="${TMPDIR:-/tmp}/unitrack-deploy-${PROJECT}.ctl"
rm -f "$SOCK"
cleanup() { ssh -S "$CTL" -O exit "$HOST" 2>/dev/null || true; rm -f "$SOCK"; }
trap cleanup EXIT
echo "==> Forwarding the remote Docker socket from $HOST over SSH..."
ssh -fN -M -S "$CTL" -o ExitOnForwardFailure=yes -L "${SOCK}:/var/run/docker.sock" "$HOST"
export DOCKER_HOST="unix://${SOCK}"
docker version --format 'remote daemon {{.Server.Version}}' \
  || { echo "!! cannot reach the remote Docker daemon over the SSH socket" >&2; exit 1; }

if [[ "$BUILD" -eq 1 ]]; then
  echo "==> Building the image on the remote Docker daemon ($HOST) via buildpacks..."
  ./mvnw -q -pl unitrack-web -am -Pdocker -DskipTests package
fi

echo "==> Deploying '$STACK' stack on $HOST (port $PORT)..."
UNITRACK_PORT="$PORT" docker compose -f "$COMPOSE" -p "$PROJECT" up -d

echo "==> Waiting for health..."
if curl -sf --retry 45 --retry-delay 2 --retry-connrefused --max-time 160 \
    "http://${HOST_ADDR}:${PORT}/actuator/health" >/dev/null; then
  echo "==> UP: http://${HOST_ADDR}:${PORT}/"
else
  echo "!! health check did not pass; check the container logs on $HOST" >&2
  exit 1
fi
