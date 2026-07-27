#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
# Передаём все аргументы в Java CLI модуля common
ARGS=""
for a in "$@"; do
  ARGS+="${ARGS:+ }${a}"
done
exec ./gradlew -q :common:run --args="$ARGS"
