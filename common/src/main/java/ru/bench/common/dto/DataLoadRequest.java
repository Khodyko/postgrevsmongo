package ru.bench.common.dto;

/**
 * Запрос на заливку данных из каталога с JSONL.
 *
 * @param dataDir каталог с tags.jsonl и products.jsonl
 * @param storageCase целевой вариант хранения
 * @param rebuildIndexes пересоздать индексы и замерить время
 * @param clearBeforeLoad очистить таблицы/коллекции перед заливкой
 */
public record DataLoadRequest(
        String dataDir,
        StorageCase storageCase,
        boolean rebuildIndexes,
        boolean clearBeforeLoad
) {
    /**
     * Создаёт запрос с разумными значениями по умолчанию.
     *
     * @param dataDir каталог датасета
     * @param storageCase вариант хранения
     * @return запрос
     */
    public static DataLoadRequest of(String dataDir, StorageCase storageCase) {
        return new DataLoadRequest(dataDir, storageCase, true, true);
    }
}
