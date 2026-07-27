package ru.bench.common.dto;

/**
 * Результат заливки данных.
 *
 * @param storageCase вариант хранения
 * @param productsLoaded число загруженных товаров
 * @param tagsLoaded число загруженных тегов / связей (смысл зависит от кейса)
 * @param loadMillis время чтения файла и вставки, мс
 * @param indexBuildMillis время построения индексов, мс
 * @param dataBytes размер данных на диске (если удалось снять), иначе -1
 * @param indexBytes размер индексов на диске (если удалось снять), иначе -1
 */
public record DataLoadResult(
        StorageCase storageCase,
        long productsLoaded,
        long tagsLoaded,
        long loadMillis,
        long indexBuildMillis,
        long dataBytes,
        long indexBytes
) {
}
