package ru.bench.postgres.scenario;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import ru.bench.common.dto.StorageCase;
import ru.bench.common.load.ScenarioOperation;

/**
 * Операции сценариев Postgres (PG_NORM и PG_JSON).
 */
@Service
public class PostgresScenarioService {

    private final JdbcTemplate jdbcTemplate;
    private final TagDictionary tagDictionary;

    /**
     * Создаёт сервис сценариев.
     *
     * @param jdbcTemplate JDBC
     * @param tagDictionary словарь имён тегов
     */
    public PostgresScenarioService(JdbcTemplate jdbcTemplate, TagDictionary tagDictionary) {
        this.jdbcTemplate = jdbcTemplate;
        this.tagDictionary = tagDictionary;
    }

    /**
     * Выполняет операцию по контексту нагрузки.
     *
     * @param context контекст
     */
    public void execute(ScenarioOperation.OperationContext context) {
        StorageCase storageCase = context.storageCase();
        String tag = resolveTag(context);
        long productId = context.resolveProductId();
        switch (context.operation()) {
            case FIND_BY_TAG -> findByTag(storageCase, tag, context.pageSize());
            case FIND_BY_ID -> findById(storageCase, productId);
            case UPDATE_TAG -> updateTag(storageCase, productId, tag);
            case DELETE_BY_TAG -> deleteTag(storageCase, productId, tag);
            case AGG_COUNT_BY_TAG -> countByTag(storageCase, tag);
            case AGG_TOP_TAGS -> topTags(storageCase, context.topN());
            default -> throw new IllegalArgumentException("Неизвестная операция " + context.operation());
        }
    }

    /**
     * Резолвит имя тега.
     *
     * @param context контекст
     * @return имя тега
     */
    private String resolveTag(ScenarioOperation.OperationContext context) {
        if (context.tag() != null && !context.tag().isBlank()) {
            return context.tag();
        }
        return tagDictionary.nameById(context.resolveTagId());
    }

    /**
     * Поиск по тегу.
     *
     * @param storageCase кейс
     * @param tag тег
     * @param pageSize лимит
     */
    private void findByTag(StorageCase storageCase, String tag, int pageSize) {
        if (storageCase == StorageCase.PG_NORM) {
            jdbcTemplate.query(
                    """
                    SELECT p.id, p.name, p.price
                    FROM product p
                    JOIN product_tag t ON t.product_id = p.id
                    WHERE t.tag = ?
                    LIMIT ?
                    """,
                    rs -> {
                    },
                    tag,
                    pageSize
            );
            return;
        }
        jdbcTemplate.query(
                """
                SELECT id, doc
                FROM product_doc
                WHERE doc @> ?::jsonb
                LIMIT ?
                """,
                rs -> {
                },
                "{\"tags\":[\"" + escapeJson(tag) + "\"]}",
                pageSize
        );
    }

    /**
     * Точечное чтение.
     *
     * @param storageCase кейс
     * @param productId id
     */
    private void findById(StorageCase storageCase, long productId) {
        if (storageCase == StorageCase.PG_NORM) {
            jdbcTemplate.query("SELECT id, name, price FROM product WHERE id = ?", rs -> {
            }, productId);
            return;
        }
        jdbcTemplate.query("SELECT id, doc FROM product_doc WHERE id = ?", rs -> {
        }, productId);
    }

    /**
     * Добавляет тег товару.
     *
     * @param storageCase кейс
     * @param productId id
     * @param tag тег
     */
    private void updateTag(StorageCase storageCase, long productId, String tag) {
        if (storageCase == StorageCase.PG_NORM) {
            jdbcTemplate.update(
                    "INSERT INTO product_tag(product_id, tag) VALUES (?, ?) ON CONFLICT DO NOTHING",
                    productId,
                    tag
            );
            return;
        }
        jdbcTemplate.update(
                """
                UPDATE product_doc
                SET doc = jsonb_set(
                    doc,
                    '{tags}',
                    CASE
                      WHEN doc -> 'tags' ? ?
                      THEN doc -> 'tags'
                      ELSE (doc -> 'tags') || to_jsonb(?::text)
                    END,
                    true
                )
                WHERE id = ?
                """,
                tag,
                tag,
                productId
        );
    }

    /**
     * Удаляет тег у товара.
     *
     * @param storageCase кейс
     * @param productId id
     * @param tag тег
     */
    private void deleteTag(StorageCase storageCase, long productId, String tag) {
        if (storageCase == StorageCase.PG_NORM) {
            jdbcTemplate.update("DELETE FROM product_tag WHERE product_id = ? AND tag = ?", productId, tag);
            return;
        }
        jdbcTemplate.update(
                """
                UPDATE product_doc
                SET doc = jsonb_set(doc, '{tags}', COALESCE((
                    SELECT jsonb_agg(value)
                    FROM jsonb_array_elements_text(doc -> 'tags') AS value
                    WHERE value <> ?
                ), '[]'::jsonb), true)
                WHERE id = ?
                """,
                tag,
                productId
        );
    }

    /**
     * Count по тегу.
     *
     * @param storageCase кейс
     * @param tag тег
     */
    private void countByTag(StorageCase storageCase, String tag) {
        if (storageCase == StorageCase.PG_NORM) {
            jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM product_tag WHERE tag = ?",
                    Long.class,
                    tag
            );
            return;
        }
        jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM product_doc WHERE doc @> ?::jsonb",
                Long.class,
                "{\"tags\":[\"" + escapeJson(tag) + "\"]}"
        );
    }

    /**
     * Top-N тегов.
     *
     * @param storageCase кейс
     * @param topN N
     */
    private void topTags(StorageCase storageCase, int topN) {
        if (storageCase == StorageCase.PG_NORM) {
            jdbcTemplate.query(
                    """
                    SELECT tag, COUNT(*) AS cnt
                    FROM product_tag
                    GROUP BY tag
                    ORDER BY cnt DESC
                    LIMIT ?
                    """,
                    rs -> {
                    },
                    topN
            );
            return;
        }
        jdbcTemplate.query(
                """
                SELECT tag, COUNT(*) AS cnt
                FROM product_doc,
                     LATERAL jsonb_array_elements_text(doc -> 'tags') AS tag
                GROUP BY tag
                ORDER BY cnt DESC
                LIMIT ?
                """,
                rs -> {
                },
                topN
        );
    }

    /**
     * Экранирует строку для вставки в JSON-литерал.
     *
     * @param value исходная строка
     * @return экранированная
     */
    private String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
