#!/usr/bin/env bash
#
# Build the Spring Boot image locally and deploy it to a remote Docker host over SSH.
# No registry needed — the image is streamed to the remote daemon with `docker save | load`.
#
# Usage:
#   scripts/deploy-remote.sh [options]
#     --host USER@HOST   remote docker host over SSH   (default: root@192.168.100.132)
#     --stack h2|postgres                              (default: h2)
#     --port N           host port to publish          (default: 8081)
#     --platform PLAT    image platform                (default: linux/amd64)
#     --project NAME     compose project name          (default: unitrack)
#     --no-build         reuse the existing local image (skip the buildpacks build)
#
# Requires: a local Docker daemon (for the buildpacks build) and SSH access to the host.

set -euo pipefail
cd "$(dirname "$0")/.."

HOST=root@192.168.100.132
STACK=h2
PORT=8081
PLATFORM=linux/amd64
PROJECT=unitrack
IMAGE=unitrack:0.1.0-SNAPSHOT
BUILD=1

while [[ $# -gt 0 ]]; do
  case "$1" in
    --host)     HOST="$2"; shift 2 ;;
    --stack)    STACK="$2"; shift 2 ;;
    --port)     PORT="$2"; shift 2 ;;
    --platform) PLATFORM="$2"; shift 2 ;;
    --project)  PROJECT="$2"; shift 2 ;;
    --no-build) BUILD=0; shift ;;
    -h|--help)  sed -n '2,20p' "$0"; exit 0 ;;
    *) echo "Unknown argument: $1" >&2; exit 2 ;;
  esac
done

COMPOSE="deploy/compose.${STACK}.yaml"
[[ -f "$COMPOSE" ]] || { echo "No compose file: $COMPOSE" >&2; exit 1; }
REMOTE="ssh://${HOST}"
HOST_ADDR="${HOST#*@}"

if [[ "$BUILD" -eq 1 ]]; then
  echo "==> Building $IMAGE ($PLATFORM) locally with Spring Boot buildpacks..."
  ./mvnw -q -pl unitrack-web -am -Pdocker -DskipTests package \
    -Dspring-boot.build-image.imagePlatform="$PLATFORM"
fi

echo "==> Shipping image to $HOST over SSH..."
docker save "$IMAGE" | DOCKER_HOST="$REMOTE" docker load

echo "==> Deploying '$STACK' stack on $HOST (port $PORT)..."
DOCKER_HOST="$REMOTE" UNITRACK_PORT="$PORT" \
  docker compose -f "$COMPOSE" -p "$PROJECT" up -d

echo "==> Waiting for health..."
if curl -sf --retry 45 --retry-delay 2 --retry-connrefused --max-time 160 \
    "http://${HOST_ADDR}:${PORT}/actuator/health" >/dev/null; then
  echo "==> UP: http://${HOST_ADDR}:${PORT}/"
else
  echo "!! health check did not pass; check: DOCKER_HOST=$REMOTE docker compose -p $PROJECT logs" >&2
  exit 1
fi
