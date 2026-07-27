package ru.bench.common.dto;

/**
 * Запрос на прогон нагрузки.
 *
 * @param storageCase вариант хранения
 * @param operation операция
 * @param concurrency число потоков
 * @param warmupSeconds длительность прогрева
 * @param measureSeconds длительность измерения
 * @param pageSize размер страницы для поиска
 * @param topN top-N для агрегации тегов
 * @param tag для операций по тегу; если null — берётся из словаря по ходу
 * @param productId для FIND_BY_ID / UPDATE / DELETE; если null — случайный из диапазона
 * @param maxProductId верхняя граница id товара в датасете
 * @param tagCount размер словаря тегов
 */
public record BenchRunRequest(
        StorageCase storageCase,
        BenchOperation operation,
        int concurrency,
        int warmupSeconds,
        int measureSeconds,
        int pageSize,
        int topN,
        String tag,
        Long productId,
        long maxProductId,
        int tagCount
) {
}
