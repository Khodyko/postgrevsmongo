package ru.bench.mongo.web;

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
import ru.bench.mongo.load.MongoDataLoadService;
import ru.bench.mongo.scenario.MongoScenarioService;

/**
 * HTTP API бенчмарка Mongo.
 */
@RestController
@RequestMapping("/api")
public class MongoBenchController {

    private final MongoDataLoadService dataLoadService;
    private final MongoScenarioService scenarioService;
    private final LoadRunner loadRunner;
    private final DatasetReader datasetReader;

    /**
     * Создаёт контроллер.
     *
     * @param dataLoadService заливка
     * @param scenarioService сценарии
     * @param loadRunner нагрузка
     * @param datasetReader читатель датасета
     */
    public MongoBenchController(
            MongoDataLoadService dataLoadService,
            MongoScenarioService scenarioService,
            LoadRunner loadRunner,
            DatasetReader datasetReader
    ) {
        this.dataLoadService = dataLoadService;
        this.scenarioService = scenarioService;
        this.loadRunner = loadRunner;
        this.datasetReader = datasetReader;
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
            scenarioService.loadTagNames(datasetReader.tagNameById(datasetReader.readAllTags(dir)));
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Не удалось прочитать tags.jsonl", e);
        }
        return dataLoadService.load(request);
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
        if (storageCase != StorageCase.MONGO && storageCase != StorageCase.MONGO_LOOKUP) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Поддерживаются MONGO и MONGO_LOOKUP");
        }
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
                request.tagCount() > 0 ? request.tagCount() : 100_000
        );
        return loadRunner.run(normalized, scenarioService::execute);
    }

    /**
     * Простой health для ручной проверки.
     *
     * @return статус
     */
    @GetMapping("/ping")
    public Map<String, String> ping() {
        return Map.of("app", "mongo", "status", "ok");
    }
}
