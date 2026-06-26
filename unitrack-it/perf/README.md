# perf — UI load test (k6)

A small [k6](https://k6.io) load test that drives UniTrack's render-heavy pages (home/board,
project, coverage, performance, clusters, run) under concurrency. Pair it with the jvmlens monitor
on the target and run `jvmlens trend` afterwards; the end-of-test summary can also be uploaded back
into UniTrack's own perf feature (dogfood — UniTrack parses k6 summaries via `K6JsonParser`).

## Run

```bash
# Open mode (local dev), exercise project 24's pages:
unitrack-it/perf/run-perf.sh --base http://localhost:8080 --project 24 --vus 5 --duration 30s

# Closed mode (the lab/prod instance) — needs a login, since pages 302 to /login otherwise:
unitrack-it/perf/run-perf.sh --base https://unitrack.example.com \
  --user admin --pass "$UNITRACK_ADMIN_PASSWORD" --project 24 --vus 5 --duration 30s

# ...and upload the result as a perf run (needs an ingest token when require-ingest-token=true):
unitrack-it/perf/run-perf.sh --base https://unitrack.example.com \
  --user admin --pass "$UNITRACK_ADMIN_PASSWORD" --project 24 \
  --upload --upload-project unitrack-selfperf --token "$UNITRACK_TOKEN"
```

No local `k6`? The wrapper falls back to the `grafana/k6` container (podman/docker) automatically.

## What it measures

k6's own thresholds gate the run: `http_req_failed < 5%` and `http_req_duration p(95) < 2000ms`.
The per-page `check`s also assert each page returns 200 and didn't bounce to `/login` (auth lapse).
The `--summary-export` JSON carries the aggregate percentiles UniTrack ingests (`http_req_duration`
p90/p95/p99, `http_reqs` count+rate, `http_req_failed` rate) — no per-label split (k6 default summary).

## Knobs

| Flag / env | Default | Meaning |
|---|---|---|
| `--base` / `BASE_URL` | `http://localhost:8080` | target instance |
| `--user`/`--pass` (`UNITRACK_USER`/`UNITRACK_PASS`) | — | form login for closed mode |
| `--project` / `PROJECT_ID` | — | also hit `/projects/<id>` + coverage/performance/clusters |
| `--run` / `RUN_ID` | — | also hit `/runs/<id>` |
| `--vus` / `VUS` | `5` | concurrent virtual users |
| `--duration` / `DURATION` | `30s` | test length |
| `--upload` + `--token` (`UNITRACK_TOKEN`) | off | POST `summary.json` to `/api/v1/ingest` as a perf run |
