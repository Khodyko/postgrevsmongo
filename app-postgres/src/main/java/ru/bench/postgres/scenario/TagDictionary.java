package ru.bench.postgres.scenario;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Кэш имён тегов для генерации нагрузки (после заливки PG_NORM / из product_doc).
 */
@Component
public class TagDictionary {

    private final JdbcTemplate jdbcTemplate;
    private final Map<Long, String> byId = new ConcurrentHashMap<>();
    private final Map<String, Long> byName = new ConcurrentHashMap<>();

    /**
     * Создаёт словарь.
     *
     * @param jdbcTemplate JDBC
     */
    public TagDictionary(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Перечитывает словарь из нормализованной таблицы (distinct tag с искусственным id порядка).
     * Для нагрузки достаточно стабильного списка имён.
     */
    public void reloadFromProductTag() {
        byId.clear();
        byName.clear();
        var tags = jdbcTemplate.queryForList("SELECT DISTINCT tag FROM product_tag ORDER BY tag", String.class);
        long id = 1L;
        for (String tag : tags) {
            byId.put(id, tag);
            byName.put(tag, id);
            id++;
        }
    }

    /**
     * Перечитывает словарь из JSONB.
     */
    public void reloadFromProductDoc() {
        byId.clear();
        byName.clear();
        var tags = jdbcTemplate.queryForList(
                """
                SELECT DISTINCT tag
                FROM product_doc,
                     LATERAL jsonb_array_elements_text(doc -> 'tags') AS tag
                ORDER BY tag
                """,
                String.class
        );
        long id = 1L;
        for (String tag : tags) {
            byId.put(id, tag);
            byName.put(tag, id);
            id++;
        }
    }

    /**
     * Загружает словарь из внешнего map (после чтения tags.jsonl).
     *
     * @param tagNames id → name
     */
    public void load(Map<Long, String> tagNames) {
        byId.clear();
        byName.clear();
        byId.putAll(tagNames);
        tagNames.forEach((id, name) -> byName.put(name, id));
    }

    /**
     * Имя тега по id; если нет — синтетическое.
     *
     * @param id id
     * @return имя
     */
    public String nameById(long id) {
        String name = byId.get(id);
        if (name != null) {
            return name;
        }
        if (!byId.isEmpty()) {
            long idx = 1 + Math.floorMod(id - 1, byId.size());
            return byId.getOrDefault(idx, byId.values().iterator().next());
        }
        return "tag_" + id;
    }

    /**
     * Размер словаря.
     *
     * @return число тегов
     */
    public int size() {
        return byId.size();
    }
}
