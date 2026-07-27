package ru.bench.common.dataset;

import java.util.List;

/**
 * Строка products.jsonl.
 * В файле теги всегда текстовые; id появляются только при заливке в norm/lookup.
 *
 * @param id идентификатор товара
 * @param name имя
 * @param price цена
 * @param createdAt дата создания (ISO-8601)
 * @param tags текстовые теги
 */
public record ProductRecord(
        long id,
        String name,
        double price,
        String createdAt,
        List<String> tags
) {
}
