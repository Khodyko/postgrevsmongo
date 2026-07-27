package ru.bench.mongo.load;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.stereotype.Service;
import ru.bench.common.dataset.DatasetReader;
import ru.bench.common.dataset.ProductRecord;
import ru.bench.common.dataset.TagRecord;
import ru.bench.common.dto.DataLoadRequest;
import ru.bench.common.dto.DataLoadResult;
import ru.bench.common.dto.StorageCase;

/**
 * Заливка датасета в MongoDB для кейсов MONGO и MONGO_LOOKUP.
 */
@Service
public class MongoDataLoadService {

    private static final Logger log = LoggerFactory.getLogger(MongoDataLoadService.class);
    private static final int BATCH_SIZE = 2000;
    private static final String COL_PRODUCTS_EMBED = "products_embed";
    private static final String COL_PRODUCTS_REF = "products_ref";
    private static final String COL_TAGS = "tags";

    private final MongoTemplate mongoTemplate;
    private final DatasetReader datasetReader;

    /**
     * Создаёт сервис заливки.
     *
     * @param mongoTemplate Mongo
     * @param datasetReader читатель JSONL
     */
    public MongoDataLoadService(MongoTemplate mongoTemplate, DatasetReader datasetReader) {
        this.mongoTemplate = mongoTemplate;
        this.datasetReader = datasetReader;
    }

    /**
     * Выполняет заливку.
     *
     * @param request параметры
     * @return результат
     */
    public DataLoadResult load(DataLoadRequest request) {
        StorageCase storageCase = request.storageCase();
        if (storageCase != StorageCase.MONGO && storageCase != StorageCase.MONGO_LOOKUP) {
            throw new IllegalArgumentException("app-mongo поддерживает только MONGO и MONGO_LOOKUP");
        }
        Path dataDir = Path.of(request.dataDir()).toAbsolutePath().normalize();
        try {
            if (request.clearBeforeLoad()) {
                clear(storageCase);
            }
            List<TagRecord> tags = datasetReader.readAllTags(dataDir);
            Map<String, Long> tagIdByName = datasetReader.tagIdByName(tags);

            long loadStarted = System.nanoTime();
            long[] counters;
            if (storageCase == StorageCase.MONGO) {
                counters = loadEmbed(dataDir);
            } else {
                loadTagDictionary(tags);
                counters = loadLookup(dataDir, tagIdByName);
                counters[1] = tags.size();
            }
            long loadMillis = (System.nanoTime() - loadStarted) / 1_000_000L;

            long indexMillis = 0L;
            if (request.rebuildIndexes()) {
                long indexStarted = System.nanoTime();
                rebuildIndexes(storageCase);
                indexMillis = (System.nanoTime() - indexStarted) / 1_000_000L;
            }

            long[] sizes = diskSizes(storageCase);
            log.info("Заливка {} завершена: products={}, tags={}, loadMs={}, indexMs={}",
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
     * Очищает коллекции кейса.
     *
     * @param storageCase кейс
     */
    private void clear(StorageCase storageCase) {
        if (storageCase == StorageCase.MONGO) {
            mongoTemplate.dropCollection(COL_PRODUCTS_EMBED);
        } else {
            mongoTemplate.dropCollection(COL_PRODUCTS_REF);
            mongoTemplate.dropCollection(COL_TAGS);
        }
    }

    /**
     * Заливает embed-коллекцию (теги — текст без id).
     *
     * @param dataDir каталог
     * @return [products, tagRefs]
     */
    private long[] loadEmbed(Path dataDir) throws Exception {
        List<Document> buffer = new ArrayList<>(BATCH_SIZE);
        long[] products = {0L};
        long[] refs = {0L};
        datasetReader.readProducts(dataDir, product -> {
            buffer.add(toEmbedDoc(product));
            refs[0] += product.tags().size();
            if (buffer.size() >= BATCH_SIZE) {
                mongoTemplate.insert(buffer, COL_PRODUCTS_EMBED);
                products[0] += buffer.size();
                buffer.clear();
            }
        });
        if (!buffer.isEmpty()) {
            mongoTemplate.insert(buffer, COL_PRODUCTS_EMBED);
            products[0] += buffer.size();
            buffer.clear();
        }
        return new long[]{products[0], refs[0]};
    }

    /**
     * Заливает справочник тегов (с id).
     *
     * @param tags теги
     */
    private void loadTagDictionary(List<TagRecord> tags) {
        List<Document> buffer = new ArrayList<>(BATCH_SIZE);
        for (TagRecord tag : tags) {
            buffer.add(new Document("_id", tag.id()).append("name", tag.name()));
            if (buffer.size() >= BATCH_SIZE) {
                mongoTemplate.insert(buffer, COL_TAGS);
                buffer.clear();
            }
        }
        if (!buffer.isEmpty()) {
            mongoTemplate.insert(buffer, COL_TAGS);
        }
    }

    /**
     * Заливает products_ref: текстовые теги из файла → tagIds по словарю.
     *
     * @param dataDir каталог
     * @param tagIdByName name → id
     * @return [products, 0]
     */
    private long[] loadLookup(Path dataDir, Map<String, Long> tagIdByName) throws Exception {
        List<Document> buffer = new ArrayList<>(BATCH_SIZE);
        long[] products = {0L};
        datasetReader.readProducts(dataDir, product -> {
            buffer.add(toRefDoc(product, tagIdByName));
            if (buffer.size() >= BATCH_SIZE) {
                mongoTemplate.insert(buffer, COL_PRODUCTS_REF);
                products[0] += buffer.size();
                buffer.clear();
            }
        });
        if (!buffer.isEmpty()) {
            mongoTemplate.insert(buffer, COL_PRODUCTS_REF);
            products[0] += buffer.size();
            buffer.clear();
        }
        return new long[]{products[0], 0L};
    }

    /**
     * Документ embed с текстовыми тегами.
     *
     * @param product товар
     * @return Document
     */
    private Document toEmbedDoc(ProductRecord product) {
        return new Document("_id", product.id())
                .append("name", product.name())
                .append("price", product.price())
                .append("createdAt", Date.from(Instant.parse(product.createdAt())))
                .append("tags", product.tags());
    }

    /**
     * Документ с tagIds (id появляются только в lookup-хранении).
     *
     * @param product товар
     * @param tagIdByName словарь
     * @return Document
     */
    private Document toRefDoc(ProductRecord product, Map<String, Long> tagIdByName) {
        List<Long> tagIds = new ArrayList<>(product.tags().size());
        for (String tag : product.tags()) {
            Long id = tagIdByName.get(tag);
            if (id != null) {
                tagIds.add(id);
            }
        }
        return new Document("_id", product.id())
                .append("name", product.name())
                .append("price", product.price())
                .append("createdAt", Date.from(Instant.parse(product.createdAt())))
                .append("tagIds", tagIds);
    }

    /**
     * Пересоздаёт индексы.
     *
     * @param storageCase кейс
     */
    private void rebuildIndexes(StorageCase storageCase) {
        if (storageCase == StorageCase.MONGO) {
            mongoTemplate.indexOps(COL_PRODUCTS_EMBED).dropAllIndexes();
            mongoTemplate.indexOps(COL_PRODUCTS_EMBED)
                    .createIndex(new Index().on("tags", Sort.Direction.ASC).named("idx_tags"));
        } else {
            mongoTemplate.indexOps(COL_TAGS).dropAllIndexes();
            mongoTemplate.indexOps(COL_TAGS)
                    .createIndex(new Index().on("name", Sort.Direction.ASC).named("idx_tag_name"));
            mongoTemplate.indexOps(COL_PRODUCTS_REF).dropAllIndexes();
            mongoTemplate.indexOps(COL_PRODUCTS_REF)
                    .createIndex(new Index().on("tagIds", Sort.Direction.ASC).named("idx_tag_ids"));
        }
    }

    /**
     * Оценка размера через collStats.
     *
     * @param storageCase кейс
     * @return [dataBytes, indexBytes]
     */
    private long[] diskSizes(StorageCase storageCase) {
        if (storageCase == StorageCase.MONGO) {
            return collStats(COL_PRODUCTS_EMBED);
        }
        long[] tags = collStats(COL_TAGS);
        long[] products = collStats(COL_PRODUCTS_REF);
        return new long[]{tags[0] + products[0], tags[1] + products[1]};
    }

    /**
     * collStats одной коллекции.
     *
     * @param collection имя
     * @return [size, totalIndexSize]
     */
    private long[] collStats(String collection) {
        Document stats = mongoTemplate.executeCommand(new Document("collStats", collection));
        long size = stats.get("size", Number.class) == null ? 0L : stats.get("size", Number.class).longValue();
        long indexes = stats.get("totalIndexSize", Number.class) == null
                ? 0L
                : stats.get("totalIndexSize", Number.class).longValue();
        return new long[]{size, indexes};
    }
}
