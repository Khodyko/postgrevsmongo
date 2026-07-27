package ru.bench.mongo.scenario;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import ru.bench.common.dto.StorageCase;
import ru.bench.common.load.ScenarioOperation;

/**
 * Операции сценариев MongoDB.
 */
@Service
public class MongoScenarioService {

    private static final String COL_PRODUCTS_EMBED = "products_embed";
    private static final String COL_PRODUCTS_REF = "products_ref";
    private static final String COL_TAGS = "tags";

    private final MongoTemplate mongoTemplate;
    private final Map<Long, String> tagNames = new ConcurrentHashMap<>();

    /**
     * Создаёт сервис.
     *
     * @param mongoTemplate Mongo
     */
    public MongoScenarioService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    /**
     * Загружает словарь тегов.
     *
     * @param names id → name
     */
    public void loadTagNames(Map<Long, String> names) {
        tagNames.clear();
        tagNames.putAll(names);
    }

    /**
     * Выполняет операцию.
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
            case UPDATE_TAG -> updateTag(storageCase, productId, tag, context.resolveTagId());
            case DELETE_BY_TAG -> deleteTag(storageCase, productId, tag, context.resolveTagId());
            case AGG_COUNT_BY_TAG -> countByTag(storageCase, tag, context.resolveTagId());
            case AGG_TOP_TAGS -> topTags(storageCase, context.topN());
            default -> throw new IllegalArgumentException("Неизвестная операция");
        }
    }

    /**
     * Резолвит имя тега.
     *
     * @param context контекст
     * @return имя
     */
    private String resolveTag(ScenarioOperation.OperationContext context) {
        if (context.tag() != null && !context.tag().isBlank()) {
            return context.tag();
        }
        long id = context.resolveTagId();
        String name = tagNames.get(id);
        if (name != null) {
            return name;
        }
        Document doc = mongoTemplate.findOne(Query.query(Criteria.where("_id").is(id)), Document.class, COL_TAGS);
        if (doc != null) {
            return doc.getString("name");
        }
        return "tag_" + id;
    }

    /**
     * Поиск по тегу.
     *
     * @param storageCase кейс
     * @param tag имя тега
     * @param pageSize лимит
     */
    private void findByTag(StorageCase storageCase, String tag, int pageSize) {
        if (storageCase == StorageCase.MONGO) {
            Query query = Query.query(Criteria.where("tags").is(tag)).limit(pageSize);
            mongoTemplate.find(query, Document.class, COL_PRODUCTS_EMBED);
            return;
        }
        Document tagDoc = mongoTemplate.findOne(Query.query(Criteria.where("name").is(tag)), Document.class, COL_TAGS);
        if (tagDoc == null) {
            return;
        }
        long tagId = ((Number) tagDoc.get("_id")).longValue();
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("tagIds").is(tagId)),
                Aggregation.limit(pageSize),
                Aggregation.lookup(COL_TAGS, "tagIds", "_id", "tags")
        );
        mongoTemplate.aggregate(aggregation, COL_PRODUCTS_REF, Document.class).getMappedResults();
    }

    /**
     * Точечное чтение.
     *
     * @param storageCase кейс
     * @param productId id
     */
    private void findById(StorageCase storageCase, long productId) {
        String collection = storageCase == StorageCase.MONGO ? COL_PRODUCTS_EMBED : COL_PRODUCTS_REF;
        mongoTemplate.findById(productId, Document.class, collection);
    }

    /**
     * Добавляет тег.
     *
     * @param storageCase кейс
     * @param productId id товара
     * @param tag имя
     * @param tagId id тега
     */
    private void updateTag(StorageCase storageCase, long productId, String tag, long tagId) {
        if (storageCase == StorageCase.MONGO) {
            mongoTemplate.updateFirst(
                    Query.query(Criteria.where("_id").is(productId)),
                    new Update().addToSet("tags", tag),
                    COL_PRODUCTS_EMBED
            );
            return;
        }
        mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is(productId)),
                new Update().addToSet("tagIds", tagId),
                COL_PRODUCTS_REF
        );
    }

    /**
     * Удаляет тег.
     *
     * @param storageCase кейс
     * @param productId id товара
     * @param tag имя
     * @param tagId id тега
     */
    private void deleteTag(StorageCase storageCase, long productId, String tag, long tagId) {
        if (storageCase == StorageCase.MONGO) {
            mongoTemplate.updateFirst(
                    Query.query(Criteria.where("_id").is(productId)),
                    new Update().pull("tags", tag),
                    COL_PRODUCTS_EMBED
            );
            return;
        }
        mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is(productId)),
                new Update().pull("tagIds", tagId),
                COL_PRODUCTS_REF
        );
    }

    /**
     * Count по тегу.
     *
     * @param storageCase кейс
     * @param tag имя
     * @param tagId id
     */
    private void countByTag(StorageCase storageCase, String tag, long tagId) {
        if (storageCase == StorageCase.MONGO) {
            mongoTemplate.count(Query.query(Criteria.where("tags").is(tag)), COL_PRODUCTS_EMBED);
            return;
        }
        Document tagDoc = mongoTemplate.findOne(Query.query(Criteria.where("name").is(tag)), Document.class, COL_TAGS);
        long id = tagDoc == null ? tagId : ((Number) tagDoc.get("_id")).longValue();
        mongoTemplate.count(Query.query(Criteria.where("tagIds").is(id)), COL_PRODUCTS_REF);
    }

    /**
     * Top-N тегов.
     *
     * @param storageCase кейс
     * @param topN N
     */
    private void topTags(StorageCase storageCase, int topN) {
        if (storageCase == StorageCase.MONGO) {
            Aggregation aggregation = Aggregation.newAggregation(
                    Aggregation.unwind("tags"),
                    Aggregation.group("tags").count().as("cnt"),
                    Aggregation.sort(org.springframework.data.domain.Sort.Direction.DESC, "cnt"),
                    Aggregation.limit(topN)
            );
            mongoTemplate.aggregate(aggregation, COL_PRODUCTS_EMBED, Document.class).getMappedResults();
            return;
        }
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.unwind("tagIds"),
                Aggregation.group("tagIds").count().as("cnt"),
                Aggregation.sort(org.springframework.data.domain.Sort.Direction.DESC, "cnt"),
                Aggregation.limit(topN),
                Aggregation.lookup(COL_TAGS, "_id", "_id", "tag"),
                Aggregation.project("cnt").and("tag.name").arrayElementAt(0).as("name")
        );
        mongoTemplate.aggregate(aggregation, COL_PRODUCTS_REF, Document.class).getMappedResults();
    }
}
