#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

echo "== generate 10K =="
./scripts/generate-dataset.sh --volume 10K --tag-count 5000 --generator 42 --out-dir data/v10k

echo "== load postgres PG_NORM =="
curl -sS -X POST http://localhost:8081/api/data/load \
  -H 'Content-Type: application/json' \
  -d "{\"dataDir\":\"$ROOT/data/v10k\",\"storageCase\":\"PG_NORM\",\"rebuildIndexes\":true,\"clearBeforeLoad\":true}"
echo

echo "== bench postgres FIND_BY_TAG c=1 =="
curl -sS -X POST http://localhost:8081/api/bench/run \
  -H 'Content-Type: application/json' \
  -d '{"storageCase":"PG_NORM","operation":"FIND_BY_TAG","concurrency":1,"warmupSeconds":2,"measureSeconds":5,"pageSize":50,"topN":20,"maxProductId":10000,"tagCount":5000}'
echo
