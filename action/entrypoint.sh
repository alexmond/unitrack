#!/usr/bin/env sh
# Entry point for the UniTrack GitHub Action. Maps the action's INPUT_* env vars onto the
# unitrack-cli, runs `upload`, and optionally `gate`. URL + token go via env (UNITRACK_URL /
# UNITRACK_TOKEN) so the token never lands on the command line.
set -eu

JAR="${UNITRACK_CLI_JAR:-/app/unitrack-cli.jar}"

[ -n "${INPUT_URL:-}" ] && export UNITRACK_URL="$INPUT_URL"
[ -n "${INPUT_TOKEN:-}" ] && export UNITRACK_TOKEN="$INPUT_TOKEN"
# INPUT_SOFT_FAIL / INPUT_DRY_RUN / INPUT_RUN_KEY are provided under these underscore names by
# the action's runs.env (Docker drops the hyphenated INPUT_SOFT-FAIL form GitHub sets natively).

# Append each non-empty line of $2 as a repeated "$1 <value>" pair to the arg list.
# (Used via the surrounding `set --`; emits NUL-free args one per line.)

set -- upload
[ -n "${INPUT_PROJECT:-}" ] && set -- "$@" --project "$INPUT_PROJECT"
[ -n "${INPUT_BRANCH:-}" ] && set -- "$@" --branch "$INPUT_BRANCH"
[ -n "${INPUT_COMMIT:-}" ] && set -- "$@" --commit "$INPUT_COMMIT"
[ -n "${INPUT_BUILD:-}" ] && set -- "$@" --build "$INPUT_BUILD"
[ -n "${INPUT_BUILD_NAME:-}" ] && set -- "$@" --build-name "$INPUT_BUILD_NAME"
[ -n "${INPUT_REPO:-}" ] && set -- "$@" --repo "$INPUT_REPO"
[ -n "${INPUT_FLAG:-}" ] && set -- "$@" --flag "$INPUT_FLAG"
[ -n "${INPUT_RUN_KEY:-}" ] && set -- "$@" --run-key "$INPUT_RUN_KEY"
[ -n "${INPUT_CI:-}" ] && set -- "$@" --ci "$INPUT_CI"

add_globs() {
	flag="$1"
	vals="$2"
	[ -z "$vals" ] && return 0
	old_ifs="$IFS"
	IFS='
'
	set -f # keep globs literal — the CLI resolves them, not the shell
	for g in $vals; do
		[ -n "$g" ] && GLOB_ARGS="${GLOB_ARGS}${flag}	${g}
"
	done
	set +f
	IFS="$old_ifs"
}

# Collect repeatable globs into a tab/newline-delimited list, then splice in.
GLOB_ARGS=""
add_globs --junit "${INPUT_JUNIT:-}"
add_globs --jacoco "${INPUT_JACOCO:-}"
add_globs --perf "${INPUT_PERF:-}"
if [ -n "$GLOB_ARGS" ]; then
	old_ifs="$IFS"
	IFS='
'
	set -f
	for pair in $GLOB_ARGS; do
		flag="${pair%%	*}"
		value="${pair#*	}"
		set -- "$@" "$flag" "$value"
	done
	set +f
	IFS="$old_ifs"
fi

[ "${INPUT_DRY_RUN:-false}" = "true" ] && set -- "$@" --dry-run
[ "${INPUT_SOFT_FAIL:-false}" = "true" ] && set -- "$@" --soft-fail
[ "${INPUT_SPLIT_BY_MODULE:-false}" = "true" ] && set -- "$@" --split-by-module

# Extra HTTP headers, one "Name: Value" per line — e.g. Cloudflare Access service-token
# headers (CF-Access-Client-Id / CF-Access-Client-Secret) when the server is behind Zero Trust.
if [ -n "${INPUT_HEADERS:-}" ]; then
	old_ifs="$IFS"
	IFS='
'
	set -f
	for h in $INPUT_HEADERS; do
		[ -n "$h" ] && set -- "$@" --header "$h"
	done
	set +f
	IFS="$old_ifs"
fi

echo "unitrack upload ($#-arg invocation)"
java -jar "$JAR" "$@"

if [ "${INPUT_GATE:-false}" = "true" ]; then
	set -- gate
	[ -n "${INPUT_PROJECT:-}" ] && set -- "$@" --project "$INPUT_PROJECT"
	[ -n "${INPUT_COMMIT:-}" ] && set -- "$@" --commit "$INPUT_COMMIT"
	[ -n "${INPUT_BRANCH:-}" ] && set -- "$@" --branch "$INPUT_BRANCH"
	[ -n "${INPUT_FLAG:-}" ] && set -- "$@" --flag "$INPUT_FLAG"
	if [ -n "${INPUT_HEADERS:-}" ]; then
		old_ifs="$IFS"
		IFS='
'
		set -f
		for h in $INPUT_HEADERS; do
			[ -n "$h" ] && set -- "$@" --header "$h"
		done
		set +f
		IFS="$old_ifs"
	fi
	echo "unitrack gate"
	java -jar "$JAR" "$@"
fi
