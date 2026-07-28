package ru.bench.postgres.config;

import io.micrometer.core.instrument.MeterRegistry;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.bench.common.dataset.DatasetReader;
import ru.bench.common.load.LoadRunner;
import ru.bench.common.metrics.BenchMetricsExporter;

/**
 * Общие бины приложения Postgres.
 */
@Configuration
public class PostgresBenchConfig {

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

    /**
     * Экспорт итогов прогона/заливки в Prometheus.
     *
     * @param registry Micrometer
     * @return экспортёр
     */
    @Bean
    public BenchMetricsExporter benchMetricsExporter(MeterRegistry registry) {
        return new BenchMetricsExporter(registry);
    }
}
