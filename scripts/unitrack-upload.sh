#!/usr/bin/env bash
#
# UniTrack uploader — sends JUnit (Surefire) and JaCoCo reports to a UniTrack server.
#
# Usage:
#   UNITRACK_URL=http://localhost:8080 \
#   scripts/unitrack-upload.sh \
#     --project myapp \
#     --branch  "$GITHUB_REF_NAME" \
#     --commit  "$GITHUB_SHA" \
#     --build   "$GITHUB_SERVER_URL/$GITHUB_REPOSITORY/actions/runs/$GITHUB_RUN_ID" \
#     --junit   'target/surefire-reports/TEST-*.xml' \
#     --jacoco  'target/site/jacoco/jacoco.xml'
#
# --junit / --jacoco accept glob patterns (quote them so the shell does not expand early)
# and may be repeated. Only --project and at least one --junit file are required.

set -euo pipefail

URL="${UNITRACK_URL:-http://localhost:8080}"
PROJECT="" BRANCH="" COMMIT="" BUILD="" REPO="" FLAG="" CI_PROVIDER="${UNITRACK_CI:-}"
JUNIT_GLOBS=()
JACOCO_GLOBS=()

while [[ $# -gt 0 ]]; do
  case "$1" in
    --project)  PROJECT="$2"; shift 2 ;;
    --branch)   BRANCH="$2"; shift 2 ;;
    --flag)     FLAG="$2"; shift 2 ;;
    --commit)   COMMIT="$2"; shift 2 ;;
    --build)    BUILD="$2"; shift 2 ;;
    --repo)     REPO="$2"; shift 2 ;;
    --ci)       CI_PROVIDER="$2"; shift 2 ;;
    --junit)    JUNIT_GLOBS+=("$2"); shift 2 ;;
    --jacoco)   JACOCO_GLOBS+=("$2"); shift 2 ;;
    --url)      URL="$2"; shift 2 ;;
    -h|--help)  sed -n '2,20p' "$0"; exit 0 ;;
    *) echo "Unknown argument: $1" >&2; exit 2 ;;
  esac
done

if [[ -z "$PROJECT" ]]; then
  echo "error: --project is required" >&2; exit 2
fi

# Expand globs into curl -F form fields.
FORM=(-F "project=$PROJECT")
[[ -n "$BRANCH" ]]      && FORM+=(-F "branch=$BRANCH")
[[ -n "$FLAG" ]]        && FORM+=(-F "flag=$FLAG")
[[ -n "$COMMIT" ]]      && FORM+=(-F "commit=$COMMIT")
[[ -n "$BUILD" ]]       && FORM+=(-F "buildUrl=$BUILD")
[[ -n "$REPO" ]]        && FORM+=(-F "repoUrl=$REPO")
[[ -n "$CI_PROVIDER" ]] && FORM+=(-F "ciProvider=$CI_PROVIDER")

junit_count=0
for glob in "${JUNIT_GLOBS[@]}"; do
  for f in $glob; do
    [[ -f "$f" ]] || continue
    FORM+=(-F "junit=@$f;type=text/xml")
    junit_count=$((junit_count + 1))
  done
done

for glob in "${JACOCO_GLOBS[@]}"; do
  for f in $glob; do
    [[ -f "$f" ]] || continue
    FORM+=(-F "jacoco=@$f;type=text/xml")
  done
done

if [[ "$junit_count" -eq 0 ]]; then
  echo "error: no JUnit files matched (--junit globs: ${JUNIT_GLOBS[*]:-none})" >&2
  exit 1
fi

echo "Uploading $junit_count JUnit file(s) to $URL/api/v1/ingest ..."
curl --fail --show-error --silent -X POST "${FORM[@]}" "$URL/api/v1/ingest"
echo
