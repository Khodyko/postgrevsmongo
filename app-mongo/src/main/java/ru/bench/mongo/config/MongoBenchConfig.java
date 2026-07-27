package ru.bench.mongo.config;

import tools.jackson.databind.json.JsonMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.bench.common.dataset.DatasetReader;
import ru.bench.common.load.LoadRunner;

/**
 * Общие бины приложения Mongo.
 */
@Configuration
public class MongoBenchConfig {

    /**
     * Читатель JSONL.
     *
     * @param objectMapper Jackson
     * @return DatasetReader
     */
    @Bean
    public DatasetReader datasetReader(JsonMapper objectMapper) {
        return new DatasetReader(objectMapper);
    }

    /**
     * Генератор нагрузки.
     *
     * @return LoadRunner
     */
    @Bean
    public LoadRunner loadRunner() {
        return new LoadRunner();
    }
}
