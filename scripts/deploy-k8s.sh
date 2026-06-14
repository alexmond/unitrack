#!/usr/bin/env bash
#
# Build & publish the UniTrack image to a registry, then install/upgrade the Helm chart on a
# Kubernetes cluster and health-check the rollout. Mirrors scripts/deploy-remote.sh, but for k8s.
#
# Usage:
#   scripts/deploy-k8s.sh [options]
#     --registry HOST[:PORT]  image registry              (default: nas1.home.int:5000)
#     --tag TAG               image tag                   (default: project version from Maven)
#     --release NAME          Helm release name           (default: unitrack)
#     --namespace NS          target namespace            (default: unitrack)
#     --values FILE           extra Helm values file      (default: deploy/helm/unitrack/values-homelab.yaml if present)
#     --no-build              skip the buildpacks build/publish (reuse the image already in the registry)
#     --no-push               build the image but don't publish (for a local/in-cluster daemon)
#
# Requires: kubectl + helm pointed at the target cluster, a Docker daemon for the build, and
# (for publish) `docker login <registry>` already done.

set -euo pipefail
cd "$(dirname "$0")/.."

REGISTRY=nas1.home.int:5000
TAG=""
RELEASE=unitrack
NAMESPACE=unitrack
VALUES=""
BUILD=1
PUBLISH=1

while [[ $# -gt 0 ]]; do
  case "$1" in
    --registry)  REGISTRY="$2"; shift 2 ;;
    --tag)       TAG="$2"; shift 2 ;;
    --release)   RELEASE="$2"; shift 2 ;;
    --namespace) NAMESPACE="$2"; shift 2 ;;
    --values)    VALUES="$2"; shift 2 ;;
    --no-build)  BUILD=0; shift ;;
    --no-push)   PUBLISH=0; shift ;;
    -h|--help)   sed -n '2,22p' "$0"; exit 0 ;;
    *) echo "Unknown argument: $1" >&2; exit 2 ;;
  esac
done

if [[ -z "$TAG" ]]; then
  TAG="$(./mvnw -q help:evaluate -Dexpression=project.version -DforceStdout -pl . 2>/dev/null | tail -1)"
fi
IMAGE="${REGISTRY}/unitrack:${TAG}"

if [[ -z "$VALUES" && -f deploy/helm/unitrack/values-homelab.yaml ]]; then
  VALUES=deploy/helm/unitrack/values-homelab.yaml
fi

if [[ "$BUILD" -eq 1 ]]; then
  echo "==> Building image $IMAGE via buildpacks (publish=$PUBLISH)..."
  ./mvnw -q -pl unitrack-web -am -Pdocker -DskipTests package \
    -Ddocker.image.name="$IMAGE" \
    -Ddocker.publish="$([[ $PUBLISH -eq 1 ]] && echo true || echo false)"
fi

echo "==> helm upgrade --install $RELEASE (ns: $NAMESPACE)..."
helm upgrade --install "$RELEASE" deploy/helm/unitrack \
  -n "$NAMESPACE" --create-namespace \
  --set image.repository="${REGISTRY}/unitrack" \
  --set image.tag="$TAG" \
  ${VALUES:+-f "$VALUES"}

echo "==> Waiting for rollout..."
kubectl -n "$NAMESPACE" rollout status "deploy/${RELEASE}" --timeout=180s

echo "==> Smoke test..."
helm test "$RELEASE" -n "$NAMESPACE"
echo "==> Done. UniTrack is up (release '$RELEASE', namespace '$NAMESPACE')."
