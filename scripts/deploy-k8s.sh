#!/usr/bin/env bash
#
# Build & publish the UniTrack image, then install/upgrade the Helm chart on a Kubernetes
# cluster and health-check the rollout. The k8s counterpart to scripts/deploy-remote.sh.
#
# Usage:
#   scripts/deploy-k8s.sh [options]
#     --registry HOST[:PORT]  image registry              (default: localhost:5000)
#     --tag TAG               image tag                   (default: project version from Maven)
#     --release NAME          Helm release name           (default: unitrack)
#     --namespace NS          target namespace            (default: unitrack)
#     --values FILE           extra Helm values file      (default: none — pass your env overrides)
#     --builder podman|buildpacks  how to build the image (default: podman)
#     --no-build              skip the build (reuse the image already in the registry)
#     --no-push              build the image but don't publish (for a local/in-cluster daemon)
#
# Builders:
#   podman      - build deploy/Containerfile (a fat-jar JRE image) with podman/docker.
#                 Works under rootless Podman; reproducible from the repo. (default)
#   buildpacks  - Spring Boot's Cloud Native Buildpacks via -Pdocker. Needs a real Docker
#                 daemon (fails under rootless Podman, see #211).
#
# Requires: kubectl + a Helm CLI pointed at the target cluster, podman or docker for the build,
# and (for publish) a prior `podman login <registry>` / `docker login <registry>`.
#
# Helm CLI override: set $HELM to use a different Helm implementation for the chart step
# (default: the Go `helm` binary). Multi-word values are supported, so you can point it at
# jhelm (the Java Helm impl):
#   HELM="java -jar $HOME/.local/share/jhelm/jhelm-1.0.1.jar" scripts/deploy-k8s.sh --values ...
# The namespace is created up-front via kubectl (not `--create-namespace`, which jhelm lacks),
# so the same upgrade call works with either implementation.

set -euo pipefail
cd "$(dirname "$0")/.."

REGISTRY=localhost:5000
TAG=""
RELEASE=unitrack
NAMESPACE=unitrack
VALUES=""
BUILDER=podman
BUILD=1
PUBLISH=1

# Helm CLI, overridable via $HELM (e.g. jhelm). Split on whitespace so a multi-word command
# like "java -jar /path/jhelm.jar" works; defaults to the Go `helm` binary.
read -r -a HELM_CMD <<< "${HELM:-helm}"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --registry)  REGISTRY="$2"; shift 2 ;;
    --tag)       TAG="$2"; shift 2 ;;
    --release)   RELEASE="$2"; shift 2 ;;
    --namespace) NAMESPACE="$2"; shift 2 ;;
    --values)    VALUES="$2"; shift 2 ;;
    --builder)   BUILDER="$2"; shift 2 ;;
    --no-build)  BUILD=0; shift ;;
    --no-push)   PUBLISH=0; shift ;;
    -h|--help)   sed -n '2,32p' "$0"; exit 0 ;;
    *) echo "Unknown argument: $1" >&2; exit 2 ;;
  esac
done

if [[ -z "$TAG" ]]; then
  TAG="$(./mvnw -q help:evaluate -Dexpression=project.version -DforceStdout -pl . 2>/dev/null | tail -1)"
fi
IMAGE="${REGISTRY}/unitrack:${TAG}"

# Pick the container CLI for the podman builder (podman preferred, docker fallback).
CONTAINER_CLI="$(command -v podman || command -v docker || true)"
TLS_FLAG=""
[[ "$CONTAINER_CLI" == *podman ]] && TLS_FLAG="--tls-verify=false"

if [[ "$BUILD" -eq 1 ]]; then
  case "$BUILDER" in
    podman)
      [[ -n "$CONTAINER_CLI" ]] || { echo "!! need podman or docker for --builder podman" >&2; exit 1; }
      echo "==> Building jar..."
      ./mvnw -q -pl unitrack-web -am -DskipTests package
      # Stage the jvmlens monitor agent into the build context (baked into the image; loaded only
      # when the chart sets JAVA_TOOL_OPTIONS via jvmlens.enabled). Prefer the local sibling build.
      if [[ -f "$HOME/IdeaProjects/jvmlens/jvmlens-agent/target/jvmlens-agent.jar" ]]; then
        cp "$HOME/IdeaProjects/jvmlens/jvmlens-agent/target/jvmlens-agent.jar" unitrack-web/target/jvmlens-agent.jar
      else
        curl -fsSL -o unitrack-web/target/jvmlens-agent.jar \
          https://github.com/alexmond/jvmlens/releases/download/latest/jvmlens-agent.jar
      fi
      echo "==> Building image $IMAGE via $CONTAINER_CLI + deploy/Containerfile..."
      "$CONTAINER_CLI" build -t "$IMAGE" -f deploy/Containerfile unitrack-web/target
      if [[ "$PUBLISH" -eq 1 ]]; then
        echo "==> Pushing $IMAGE..."
        "$CONTAINER_CLI" push $TLS_FLAG "$IMAGE"
      fi
      ;;
    buildpacks)
      echo "==> Building image $IMAGE via buildpacks (publish=$PUBLISH)..."
      ./mvnw -q -pl unitrack-web -am -Pdocker -DskipTests package \
        -Ddocker.image.name="$IMAGE" \
        -Ddocker.publish="$([[ $PUBLISH -eq 1 ]] && echo true || echo false)"
      ;;
    *) echo "Unknown --builder: $BUILDER (use podman or buildpacks)" >&2; exit 2 ;;
  esac
fi

# Ensure the namespace exists (replaces `helm --create-namespace`, which jhelm doesn't support).
kubectl get namespace "$NAMESPACE" >/dev/null 2>&1 || kubectl create namespace "$NAMESPACE"

echo "==> ${HELM_CMD[*]} upgrade --install $RELEASE (ns: $NAMESPACE)..."
"${HELM_CMD[@]}" upgrade --install "$RELEASE" deploy/helm/unitrack \
  -n "$NAMESPACE" \
  --set image.repository="${REGISTRY}/unitrack" \
  --set image.tag="$TAG" \
  ${VALUES:+-f "$VALUES"}

echo "==> Waiting for rollout..."
kubectl -n "$NAMESPACE" rollout status "deploy/${RELEASE}" --timeout=180s

echo "==> Smoke test..."
"${HELM_CMD[@]}" test "$RELEASE" -n "$NAMESPACE"
echo "==> Done. UniTrack is up (release '$RELEASE', namespace '$NAMESPACE')."
