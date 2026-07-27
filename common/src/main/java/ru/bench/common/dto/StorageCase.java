package ru.bench.common.dto;

/**
 * Вариант хранения / кейс сравнения.
 */
public enum StorageCase {
    /** Postgres: product + product_tag. */
    PG_NORM,
    /** Postgres: весь продукт в JSONB. */
    PG_JSON,
    /** Mongo: теги внутри документа. */
    MONGO,
    /** Mongo: товар в документе, теги через $lookup. */
    MONGO_LOOKUP
}
