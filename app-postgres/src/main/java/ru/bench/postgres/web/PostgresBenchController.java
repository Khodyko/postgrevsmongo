package ru.bench.postgres.web;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import ru.bench.common.dataset.DatasetReader;
import ru.bench.common.dto.BenchRunRequest;
import ru.bench.common.dto.BenchRunResult;
import ru.bench.common.dto.DataLoadRequest;
import ru.bench.common.dto.DataLoadResult;
import ru.bench.common.dto.StorageCase;
import ru.bench.common.load.LoadRunner;
import ru.bench.common.metrics.BenchMetricsExporter;
import ru.bench.postgres.load.PostgresDataLoadService;
import ru.bench.postgres.scenario.PostgresScenarioService;
import ru.bench.postgres.scenario.TagDictionary;

/**
 * HTTP API бенчмарка Postgres.
 */
@RestController
@RequestMapping("/api")
public class PostgresBenchController {

    private final PostgresDataLoadService dataLoadService;
    private final PostgresScenarioService scenarioService;
    private final LoadRunner loadRunner;
    private final DatasetReader datasetReader;
    private final TagDictionary tagDictionary;
    private final BenchMetricsExporter metricsExporter;

    /**
     * Создаёт контроллер.
     *
     * @param dataLoadService заливка
     * @param scenarioService сценарии
     * @param loadRunner нагрузка
     * @param datasetReader читатель датасета
     * @param tagDictionary словарь тегов
     * @param metricsExporter экспорт в Prometheus
     */
    public PostgresBenchController(
            PostgresDataLoadService dataLoadService,
            PostgresScenarioService scenarioService,
            LoadRunner loadRunner,
            DatasetReader datasetReader,
            TagDictionary tagDictionary,
            BenchMetricsExporter metricsExporter
    ) {
        this.dataLoadService = dataLoadService;
        this.scenarioService = scenarioService;
        this.loadRunner = loadRunner;
        this.datasetReader = datasetReader;
        this.tagDictionary = tagDictionary;
        this.metricsExporter = metricsExporter;
    }

    /**
     * Заливает данные из каталога JSONL.
     *
     * @param request запрос
     * @return результат
     */
    @PostMapping("/data/load")
    public DataLoadResult load(@RequestBody DataLoadRequest request) {
        Path dir = Path.of(request.dataDir()).toAbsolutePath().normalize();
        if (!Files.isDirectory(dir)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Нет каталога: " + dir);
        }
        try {
            Map<Long, String> names = datasetReader.tagNameById(datasetReader.readAllTags(dir));
            tagDictionary.load(names);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Не удалось прочитать tags.jsonl", e);
        }
        DataLoadResult result = dataLoadService.load(request);
        metricsExporter.recordLoad(result);
        return result;
    }

    /**
     * Запускает прогон нагрузки.
     *
     * @param request запрос
     * @return результат
     */
    @PostMapping("/bench/run")
    public BenchRunResult run(@RequestBody BenchRunRequest request) {
        StorageCase storageCase = request.storageCase();
        if (storageCase != StorageCase.PG_NORM && storageCase != StorageCase.PG_JSON) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Поддерживаются PG_NORM и PG_JSON");
        }
        int tagCount = request.tagCount() > 0 ? request.tagCount() : Math.max(1, tagDictionary.size());
        BenchRunRequest normalized = new BenchRunRequest(
                request.storageCase(),
                request.operation(),
                request.concurrency(),
                request.warmupSeconds(),
                request.measureSeconds(),
                request.pageSize() > 0 ? request.pageSize() : 50,
                request.topN() > 0 ? request.topN() : 20,
                request.tag(),
                request.productId(),
                request.maxProductId(),
                tagCount
        );
        BenchRunResult result = loadRunner.run(normalized, scenarioService::execute);
        metricsExporter.recordRun(normalized, result);
        return result;
    }

    /**
     * Простой health для ручной проверки.
     *
     * @return статус
     */
    @GetMapping("/ping")
    public Map<String, String> ping() {
        return Map.of("app", "postgres", "status", "ok");
    }
}
