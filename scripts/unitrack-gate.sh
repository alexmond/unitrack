#!/usr/bin/env bash
#
# UniTrack quality-gate check — fails the build (exit 1) when the gate fails.
#
# Works on ANY CI (GitLab CI, Jenkins, Buildkite, local pre-push hooks), not just
# GitHub — it looks up the latest run for a project + commit (or branch) and exits
# non-zero if the quality gate did not pass.
#
# Usage:
#   UNITRACK_URL=http://localhost:8080 \
#   scripts/unitrack-gate.sh --project myapp --commit "$GIT_SHA"
#
#   # or gate on the latest run of a branch:
#   scripts/unitrack-gate.sh --project myapp --branch main
#
# Options:
#   --project <name>   Project name (required).
#   --commit  <sha>    Gate the latest run for this commit (preferred in CI).
#   --branch  <name>   Gate the latest run on this branch (if --commit is absent).
#   --flag    <flag>   Restrict to a coverage flag / component.
#   --token   <token>  API token (or UNITRACK_TOKEN); sent as 'Authorization: Bearer'.
#   --url     <url>    Server URL (or UNITRACK_URL; default http://localhost:8080).
#
# Exit codes: 0 = gate passed · 1 = gate failed · 2 = usage/lookup error.

set -euo pipefail

URL="${UNITRACK_URL:-http://localhost:8080}"
TOKEN="${UNITRACK_TOKEN:-}"
PROJECT="" COMMIT="" BRANCH="" FLAG=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --project) PROJECT="$2"; shift 2 ;;
    --commit)  COMMIT="$2"; shift 2 ;;
    --branch)  BRANCH="$2"; shift 2 ;;
    --flag)    FLAG="$2"; shift 2 ;;
    --token)   TOKEN="$2"; shift 2 ;;
    --url)     URL="$2"; shift 2 ;;
    -h|--help) sed -n '2,24p' "$0"; exit 0 ;;
    *) echo "Unknown argument: $1" >&2; exit 2 ;;
  esac
done

if [[ -z "$PROJECT" ]]; then
  echo "error: --project is required" >&2; exit 2
fi
if [[ -z "$COMMIT" && -z "$BRANCH" ]]; then
  echo "error: one of --commit or --branch is required" >&2; exit 2
fi

AUTH=()
[[ -n "$TOKEN" ]] && AUTH=(-H "Authorization: Bearer $TOKEN")

# Capture body + HTTP status in one request.
response="$(curl --show-error --silent -w $'\n%{http_code}' "${AUTH[@]}" -G \
  --data-urlencode "project=$PROJECT" \
  ${COMMIT:+--data-urlencode "commit=$COMMIT"} \
  ${BRANCH:+--data-urlencode "branch=$BRANCH"} \
  ${FLAG:+--data-urlencode "flag=$FLAG"} \
  "$URL/api/v1/gate")"

code="${response##*$'\n'}"
body="${response%$'\n'*}"

if [[ "$code" == "404" ]]; then
  echo "✗ No run found for project=$PROJECT ${COMMIT:+commit=$COMMIT}${BRANCH:+branch=$BRANCH}${FLAG:+ flag=$FLAG}" >&2
  exit 2
fi
if [[ "$code" != "200" ]]; then
  echo "✗ Gate lookup failed (HTTP $code): $body" >&2
  exit 2
fi

if grep -q '"status":"PASSED"' <<<"$body"; then
  echo "✓ Quality gate PASSED for $PROJECT ${COMMIT:+@ $COMMIT}${BRANCH:+@ $BRANCH}"
  exit 0
fi

echo "✗ Quality gate FAILED for $PROJECT ${COMMIT:+@ $COMMIT}${BRANCH:+@ $BRANCH}" >&2
echo "$body" >&2
exit 1
