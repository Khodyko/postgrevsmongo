package ru.bench.postgres.load;

import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.bench.common.dataset.DatasetReader;
import ru.bench.common.dataset.ProductRecord;
import ru.bench.common.dto.DataLoadRequest;
import ru.bench.common.dto.DataLoadResult;
import ru.bench.common.dto.StorageCase;
import tools.jackson.databind.json.JsonMapper;

/**
 * Заливка датасета в PostgreSQL для кейсов A и B.
 * В файле теги текстовые; id в PG не нужны (текст в product_tag / массив строк в JSONB).
 */
@Service
public class PostgresDataLoadService {

    private static final Logger log = LoggerFactory.getLogger(PostgresDataLoadService.class);
    private static final int BATCH_SIZE = 2000;

    private final JdbcTemplate jdbcTemplate;
    private final DatasetReader datasetReader;
    private final JsonMapper objectMapper;

    /**
     * Создаёт сервис заливки.
     *
     * @param jdbcTemplate JDBC
     * @param datasetReader читатель JSONL
     * @param objectMapper Jackson
     */
    public PostgresDataLoadService(
            JdbcTemplate jdbcTemplate,
            DatasetReader datasetReader,
            JsonMapper objectMapper
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.datasetReader = datasetReader;
        this.objectMapper = objectMapper;
    }

    /**
     * Выполняет заливку.
     *
     * @param request параметры
     * @return результат с таймингами
     */
    public DataLoadResult load(DataLoadRequest request) {
        StorageCase storageCase = request.storageCase();
        if (storageCase != StorageCase.PG_NORM && storageCase != StorageCase.PG_JSON) {
            throw new IllegalArgumentException("app-postgres поддерживает только PG_NORM и PG_JSON");
        }
        Path dataDir = Path.of(request.dataDir()).toAbsolutePath().normalize();
        try {
            if (request.clearBeforeLoad()) {
                clear(storageCase);
            }
            long loadStarted = System.nanoTime();
            long[] counters = storageCase == StorageCase.PG_NORM
                    ? loadNormalized(dataDir)
                    : loadJson(dataDir);
            long loadMillis = (System.nanoTime() - loadStarted) / 1_000_000L;

            long indexMillis = 0L;
            if (request.rebuildIndexes()) {
                long indexStarted = System.nanoTime();
                rebuildIndexes(storageCase);
                indexMillis = (System.nanoTime() - indexStarted) / 1_000_000L;
            }
            jdbcTemplate.execute("ANALYZE");

            long[] sizes = diskSizes(storageCase);
            log.info("Заливка {} завершена: products={}, tagsOrLinks={}, loadMs={}, indexMs={}",
                    storageCase, counters[0], counters[1], loadMillis, indexMillis);
            return new DataLoadResult(
                    storageCase,
                    counters[0],
                    counters[1],
                    loadMillis,
                    indexMillis,
                    sizes[0],
                    sizes[1]
            );
        } catch (Exception e) {
            throw new IllegalStateException("Ошибка заливки из " + dataDir, e);
        }
    }

    /**
     * Очищает таблицы выбранного кейса.
     *
     * @param storageCase кейс
     */
    private void clear(StorageCase storageCase) {
        if (storageCase == StorageCase.PG_NORM) {
            jdbcTemplate.execute("TRUNCATE TABLE product_tag, product");
        } else {
            jdbcTemplate.execute("TRUNCATE TABLE product_doc");
        }
    }

    /**
     * Заливает нормализованную схему (теги — текст).
     *
     * @param dataDir каталог
     * @return [products, tagLinks]
     */
    private long[] loadNormalized(Path dataDir) throws Exception {
        List<ProductRecord> buffer = new ArrayList<>(BATCH_SIZE);
        long[] products = {0L};
        long[] links = {0L};
        datasetReader.readProducts(dataDir, product -> {
            buffer.add(product);
            if (buffer.size() >= BATCH_SIZE) {
                links[0] += flushNormalizedBatch(buffer);
                products[0] += buffer.size();
                buffer.clear();
            }
        });
        if (!buffer.isEmpty()) {
            links[0] += flushNormalizedBatch(buffer);
            products[0] += buffer.size();
            buffer.clear();
        }
        return new long[]{products[0], links[0]};
    }

    /**
     * Сбрасывает пакет в product + product_tag.
     *
     * @param batch пакет товаров
     * @return число связей
     */
    @Transactional
    protected long flushNormalizedBatch(List<ProductRecord> batch) {
        jdbcTemplate.batchUpdate(
                "INSERT INTO product(id, name, price, created_at) VALUES (?,?,?,?)",
                batch,
                batch.size(),
                (PreparedStatement ps, ProductRecord p) -> {
                    ps.setLong(1, p.id());
                    ps.setString(2, p.name());
                    ps.setBigDecimal(3, java.math.BigDecimal.valueOf(p.price()));
                    ps.setTimestamp(4, Timestamp.from(Instant.parse(p.createdAt())));
                }
        );
        List<Object[]> tagRows = new ArrayList<>();
        for (ProductRecord product : batch) {
            for (String tag : product.tags()) {
                tagRows.add(new Object[]{product.id(), tag});
            }
        }
        jdbcTemplate.batchUpdate(
                "INSERT INTO product_tag(product_id, tag) VALUES (?,?) ON CONFLICT DO NOTHING",
                tagRows
        );
        return tagRows.size();
    }

    /**
     * Заливает JSONB-схему (теги — текстовый массив в doc).
     *
     * @param dataDir каталог
     * @return [products, tagRefs]
     */
    private long[] loadJson(Path dataDir) throws Exception {
        List<ProductRecord> buffer = new ArrayList<>(BATCH_SIZE);
        long[] products = {0L};
        long[] refs = {0L};
        datasetReader.readProducts(dataDir, product -> {
            buffer.add(product);
            refs[0] += product.tags().size();
            if (buffer.size() >= BATCH_SIZE) {
                flushJsonBatch(buffer);
                products[0] += buffer.size();
                buffer.clear();
            }
        });
        if (!buffer.isEmpty()) {
            flushJsonBatch(buffer);
            products[0] += buffer.size();
            buffer.clear();
        }
        return new long[]{products[0], refs[0]};
    }

    /**
     * Сбрасывает пакет в product_doc.
     *
     * @param batch пакет
     */
    @Transactional
    protected void flushJsonBatch(List<ProductRecord> batch) {
        jdbcTemplate.batchUpdate(
                "INSERT INTO product_doc(id, doc) VALUES (?, ?::jsonb)",
                batch,
                batch.size(),
                (PreparedStatement ps, ProductRecord p) -> {
                    try {
                        Map<String, Object> doc = Map.of(
                                "id", p.id(),
                                "name", p.name(),
                                "price", p.price(),
                                "createdAt", p.createdAt(),
                                "tags", p.tags()
                        );
                        ps.setLong(1, p.id());
                        ps.setString(2, objectMapper.writeValueAsString(doc));
                    } catch (Exception e) {
                        throw new IllegalStateException("Не удалось сериализовать product id=" + p.id(), e);
                    }
                }
        );
    }

    /**
     * Пересоздаёт индексы.
     *
     * @param storageCase кейс
     */
    private void rebuildIndexes(StorageCase storageCase) {
        if (storageCase == StorageCase.PG_NORM) {
            jdbcTemplate.execute("DROP INDEX IF EXISTS idx_product_tag_tag");
            jdbcTemplate.execute("CREATE INDEX idx_product_tag_tag ON product_tag(tag)");
        } else {
            jdbcTemplate.execute("DROP INDEX IF EXISTS idx_product_doc_tags_gin");
            jdbcTemplate.execute(
                    "CREATE INDEX idx_product_doc_tags_gin ON product_doc USING GIN (doc jsonb_path_ops)"
            );
        }
    }

    /**
     * Размер данных и индексов.
     *
     * @param storageCase кейс
     * @return [dataBytes, indexBytes]
     */
    private long[] diskSizes(StorageCase storageCase) {
        if (storageCase == StorageCase.PG_NORM) {
            Long data = jdbcTemplate.queryForObject(
                    """
                    SELECT pg_total_relation_size('product')
                         + pg_relation_size('product_tag')
                    """,
                    Long.class
            );
            Long indexes = jdbcTemplate.queryForObject(
                    """
                    SELECT pg_indexes_size('product')
                         + pg_indexes_size('product_tag')
                    """,
                    Long.class
            );
            return new long[]{nullToZero(data), nullToZero(indexes)};
        }
        Long data = jdbcTemplate.queryForObject("SELECT pg_relation_size('product_doc')", Long.class);
        Long indexes = jdbcTemplate.queryForObject("SELECT pg_indexes_size('product_doc')", Long.class);
        return new long[]{nullToZero(data), nullToZero(indexes)};
    }

    /**
     * Null-safe long.
     *
     * @param value значение
     * @return 0 если null
     */
    private long nullToZero(Long value) {
        return value == null ? 0L : value;
    }
}
