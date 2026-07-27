package ru.bench.common.dataset;

/**
 * Строка tags.jsonl — словарь уникальных текстовых тегов.
 *
 * @param id стабильный номер в словаре (для воспроизводимости и lookup-заливки)
 * @param name текстовое имя тега
 */
public record TagRecord(long id, String name) {
}
