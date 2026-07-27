package ru.bench.common.dto;

/**
 * Операция сценария бенчмарка.
 */
public enum BenchOperation {
    FIND_BY_TAG,
    FIND_BY_ID,
    UPDATE_TAG,
    DELETE_BY_TAG,
    AGG_COUNT_BY_TAG,
    AGG_TOP_TAGS
}
