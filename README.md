# postgrvsmongo

Сравнение **PostgreSQL 17** и **MongoDB 7** для доклада (Java 25, Spring Boot 4).

План: [docs/BENCHMARK_PLAN.md](docs/BENCHMARK_PLAN.md)

## Модули

| Модуль | Порт | Назначение |
|--------|------|------------|
| `common` | — | DTO, DatasetReader, LoadRunner, генератор датасета |
| `app-postgres` | 8081 | кейсы `PG_NORM`, `PG_JSON` + UI |
| `app-mongo` | 8082 | кейсы `MONGO`, `MONGO_LOOKUP` + UI |
| `ui/` | — | общий Bench UI (чистый HTML/JS), копируется в `static/` при сборке |

## Быстрый старт

```bash
# 1. Инфра (Postgres, Mongo, Prometheus)
docker compose up -d

# 2. Датасет — теги в products.jsonl должны быть текстовыми ("tags": [...]), не tagIds
./scripts/generate-dataset.sh --volume 10K --tag-count 5000 --generator 42 --out-dir data/v10k

# 3. Сборка
./gradlew :app-postgres:bootJar :app-mongo:bootJar

# 4. Запуск приложений (нагружать по очереди: сначала Postgres, потом Mongo)
java -jar app-postgres/build/libs/app-postgres.jar
java -jar app-mongo/build/libs/app-mongo.jar
```

## UI

После запуска нужного приложения открой его `/` — UI ходит только в **свой** API (same-origin).

| URL | Кейсы |
|-----|--------|
| http://localhost:8081/ | `PG_NORM`, `PG_JSON` |
| http://localhost:8082/ | `MONGO`, `MONGO_LOOKUP` |

Кнопки: **Ping**, **Load**, **Bench run**. В `dataDir` — абсолютный путь к датасету.

## Заливка и прогон (curl)

Эквивалент кнопок UI:

```bash
# заливка Postgres
curl -X POST http://localhost:8081/api/data/load -H 'Content-Type: application/json' \
  -d '{"dataDir":"'"$PWD"'/data/v10k","storageCase":"PG_NORM","rebuildIndexes":true,"clearBeforeLoad":true}'

# заливка Mongo
curl -X POST http://localhost:8082/api/data/load -H 'Content-Type: application/json' \
  -d '{"dataDir":"'"$PWD"'/data/v10k","storageCase":"MONGO","rebuildIndexes":true,"clearBeforeLoad":true}'

# прогон (p50/p95/p99 — в JSON-ответе)
curl -X POST http://localhost:8081/api/bench/run -H 'Content-Type: application/json' \
  -d '{"storageCase":"PG_NORM","operation":"FIND_BY_TAG","concurrency":8,"warmupSeconds":10,"measureSeconds":60,"pageSize":50,"topN":20,"maxProductId":10000,"tagCount":5000}'
```

Smoke только Postgres (нужен запущенный `:8081`):

```bash
./scripts/smoke-postgres.sh
```

## Метрики

| Что | Откуда |
|-----|--------|
| p50 / p95 / p99 / opsPerSecond | JSON `/api/bench/run` **и** Prometheus `bench_*` (после прогона) |
| время заливки / индексов / диск | JSON `/api/data/load` **и** Prometheus `bench_load_*` / `bench_index_*` / `bench_data_*` |
| CPU / heap JVM, Hikari | Prometheus (live во время прогона) |
| CPU / RAM контейнера БД | `docker stats` |

Итоги считает `LoadRunner` (HdrHistogram); `BenchMetricsExporter` кладёт их в Micrometer Gauge с метками  
`storage_case`, `operation`, `concurrency`, `volume` (например `10K`).

- Actuator Postgres: http://localhost:8081/actuator/prometheus  
- Actuator Mongo: http://localhost:8082/actuator/prometheus  
- Prometheus UI: http://localhost:9090  

После серии прогонов (подожди ~5 с на scrape):

```promql
# p95 по вариантам (instant)
bench_p95_ms{operation="FIND_BY_TAG", volume="10K", concurrency="8"}

bench_ops_per_second{operation="FIND_BY_TAG", volume="10K"}

bench_load_ms
bench_index_build_ms
bench_data_bytes

# ресурсы во время прогона
process_cpu_usage{job="app-postgres"}
jvm_memory_used_bytes{job="app-postgres", area="heap"}
hikaricp_connections_active{job="app-postgres"}
hikaricp_connections_pending{job="app-postgres"}
```

Метрики `bench_*` живут в JVM до перезапуска; повтор с теми же labels перезаписывает значение.
## API

Одинаковый контракт у обоих приложений; отличается набор `storageCase`.

| Метод | Путь | Назначение |
|-------|------|------------|
| `GET` | `/api/ping` | живость приложения |
| `POST` | `/api/data/load` | заливка из `dataDir` |
| `POST` | `/api/bench/run` | прогрев + измерение |

`storageCase`: `PG_NORM` \| `PG_JSON` (порт 8081), `MONGO` \| `MONGO_LOOKUP` (порт 8082).  
`operation`: `FIND_BY_TAG`, `FIND_BY_ID`, `UPDATE_TAG`, `DELETE_BY_TAG`, `AGG_COUNT_BY_TAG`, `AGG_TOP_TAGS`.  
`concurrency` ≤ 32 (размер пула соединений).
