package ru.bench.common.dataset;

import tools.jackson.databind.json.JsonMapper;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Потоковое чтение JSONL датасета.
 */
public final class DatasetReader {

    private final JsonMapper objectMapper;

    /**
     * Создаёт читатель.
     *
     * @param objectMapper Jackson
     */
    public DatasetReader(JsonMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Читает все теги в память (словарь относительно небольшой).
     *
     * @param dataDir каталог датасета
     * @return список тегов
     */
    public List<TagRecord> readAllTags(Path dataDir) throws IOException {
        Path file = dataDir.resolve("tags.jsonl");
        List<TagRecord> tags = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                tags.add(objectMapper.readValue(line, TagRecord.class));
            }
        }
        return tags;
    }

    /**
     * Строит map id → name.
     *
     * @param tags список тегов
     * @return карта имён
     */
    public Map<Long, String> tagNameById(List<TagRecord> tags) {
        Map<Long, String> map = new HashMap<>(tags.size() * 2);
        for (TagRecord tag : tags) {
            map.put(tag.id(), tag.name());
        }
        return map;
    }

    /**
     * Строит map name → id (для заливки lookup/norm при необходимости).
     *
     * @param tags список тегов
     * @return карта id по имени
     */
    public Map<String, Long> tagIdByName(List<TagRecord> tags) {
        Map<String, Long> map = new HashMap<>(tags.size() * 2);
        for (TagRecord tag : tags) {
            map.put(tag.name(), tag.id());
        }
        return map;
    }

    /**
     * Читает товары потоково.
     *
     * @param dataDir каталог датасета
     * @param consumer обработчик каждой записи
     * @return число прочитанных товаров
     */
    public long readProducts(Path dataDir, Consumer<ProductRecord> consumer) throws IOException {
        Path file = dataDir.resolve("products.jsonl");
        long count = 0;
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                consumer.accept(objectMapper.readValue(line, ProductRecord.class));
                count++;
            }
        }
        return count;
    }
}
