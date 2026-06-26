#!/usr/bin/env bash
#
# Run the k6 UI load test against a UniTrack instance and (optionally) upload the result back into
# UniTrack's own perf feature. Uses a local `k6` if present, else the grafana/k6 container.
#
# Usage:
#   unitrack-it/perf/run-perf.sh [options]
#     --base URL          target instance            (default: http://localhost:8080)
#     --user U --pass P   form login (closed mode)   (or env UNITRACK_USER / UNITRACK_PASS)
#     --project ID        exercise project pages     (e.g. 24)
#     --run ID            exercise a run page
#     --vus N             concurrent virtual users   (default: 5)
#     --duration T        test length                (default: 30s)
#     --upload            after the run, POST summary.json to <base>/api/v1/ingest as a perf run
#     --upload-project P  project name for the perf run   (default: unitrack-selfperf)
#     --token T           ingest token for --upload  (or env UNITRACK_TOKEN)
#
# The load test hits the render-heavy pages (home/board, project, coverage, performance, clusters,
# run). Pair it with the jvmlens monitor on the target and run `jvmlens trend` afterwards.

set -euo pipefail
DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$DIR/../.." && pwd)"

BASE="http://localhost:8080"
USER_="${UNITRACK_USER:-}"; PASS_="${UNITRACK_PASS:-}"
PROJECT_ID=""; RUN_ID=""; VUS=5; DURATION=30s
UPLOAD=0; UPLOAD_PROJECT=unitrack-selfperf; TOKEN="${UNITRACK_TOKEN:-}"

while [[ $# -gt 0 ]]; do
	case "$1" in
		--base)           BASE="$2"; shift 2 ;;
		--user)           USER_="$2"; shift 2 ;;
		--pass)           PASS_="$2"; shift 2 ;;
		--project)        PROJECT_ID="$2"; shift 2 ;;
		--run)            RUN_ID="$2"; shift 2 ;;
		--vus)            VUS="$2"; shift 2 ;;
		--duration)       DURATION="$2"; shift 2 ;;
		--upload)         UPLOAD=1; shift ;;
		--upload-project) UPLOAD_PROJECT="$2"; shift 2 ;;
		--token)          TOKEN="$2"; shift 2 ;;
		-h|--help)        sed -n '2,21p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
		*) echo "unknown arg: $1" >&2; exit 2 ;;
	esac
done

OUT="$DIR/summary.json"
rm -f "$OUT"
ENVV=(BASE_URL="$BASE" UNITRACK_USER="$USER_" UNITRACK_PASS="$PASS_" \
      PROJECT_ID="$PROJECT_ID" RUN_ID="$RUN_ID" VUS="$VUS" DURATION="$DURATION")

echo "==> load test: $BASE  (vus=$VUS duration=$DURATION project=${PROJECT_ID:-none} run=${RUN_ID:-none})"
# k6 exits non-zero when a threshold is crossed — capture it but DON'T abort, so a slow run still
# gets its summary recorded/uploaded (that's the data we want most). Re-surface the code at the end.
K6_RC=0
if command -v k6 >/dev/null 2>&1; then
	env "${ENVV[@]}" SUMMARY_OUT="$OUT" k6 run "$DIR/ui-load.js" || K6_RC=$?
else
	CLI="$(command -v podman || command -v docker || true)"
	[[ -n "$CLI" ]] || { echo "!! need k6, or podman/docker for the grafana/k6 container" >&2; exit 1; }
	echo "   (no local k6 — using grafana/k6 via $CLI; --network=host so localhost targets work)"
	ENVFLAGS=(); for kv in "${ENVV[@]}"; do ENVFLAGS+=(-e "$kv"); done
	# k6 writes the summary via handleSummary() to SUMMARY_OUT (= the mounted /perf). Run as the host
	# user so it can write back; rootless podman maps --user to a subuid, so also --userns=keep-id.
	RUNFLAGS=(--rm --network=host --user "$(id -u):$(id -g)")
	[[ "$CLI" == *podman ]] && RUNFLAGS+=(--userns=keep-id)
	"$CLI" run "${RUNFLAGS[@]}" "${ENVFLAGS[@]}" -e SUMMARY_OUT=/perf/summary.json -v "$DIR":/perf:Z \
		docker.io/grafana/k6 run /perf/ui-load.js || K6_RC=$?
fi
[[ "$K6_RC" -ne 0 ]] && echo "   (k6 exited $K6_RC — likely a crossed threshold; continuing to record the result)"

echo "==> summary: $OUT"
[[ -f "$OUT" ]] || { echo "!! k6 produced no summary (run failed?)" >&2; exit 1; }

if [[ "$UPLOAD" -eq 1 ]]; then
	echo "==> uploading perf run to $BASE (project=$UPLOAD_PROJECT)"
	bash "$ROOT/scripts/unitrack-upload.sh" \
		--url "$BASE" --project "$UPLOAD_PROJECT" --branch perf \
		${TOKEN:+--token "$TOKEN"} --perf "$OUT"
fi
echo "==> done."
exit "$K6_RC"   # non-zero if k6 crossed a threshold (after the result was recorded/uploaded)
