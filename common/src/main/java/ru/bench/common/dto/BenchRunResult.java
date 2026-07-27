package ru.bench.common.dto;

/**
 * Результат прогона нагрузки.
 *
 * @param storageCase вариант хранения
 * @param operation операция
 * @param concurrency число потоков
 * @param operations число успешных операций за окно измерения
 * @param errors число ошибок
 * @param opsPerSecond пропускная способность
 * @param p50Ms перцентиль 50, мс
 * @param p95Ms перцентиль 95, мс
 * @param p99Ms перцентиль 99, мс
 * @param maxMs максимум, мс
 * @param measureSeconds фактическое окно измерения
 */
public record BenchRunResult(
        StorageCase storageCase,
        BenchOperation operation,
        int concurrency,
        long operations,
        long errors,
        double opsPerSecond,
        double p50Ms,
        double p95Ms,
        double p99Ms,
        double maxMs,
        int measureSeconds
) {
}
