package ru.bench.common.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.MultiGauge;
import io.micrometer.core.instrument.Tags;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.bench.common.dto.BenchRunRequest;
import ru.bench.common.dto.BenchRunResult;
import ru.bench.common.dto.DataLoadResult;

/**
 * Публикует итоги заливки и прогона в Micrometer → Prometheus.
 * HdrHistogram в {@code LoadRunner} остаётся источником p50/p95/p99;
 * сюда кладём уже посчитанные значения как Gauge с фиксированными метками.
 */
public class BenchMetricsExporter {

    private static final Logger log = LoggerFactory.getLogger(BenchMetricsExporter.class);

    private final Map<Tags, Double> p50ByTags = new ConcurrentHashMap<>();
    private final Map<Tags, Double> p95ByTags = new ConcurrentHashMap<>();
    private final Map<Tags, Double> p99ByTags = new ConcurrentHashMap<>();
    private final Map<Tags, Double> opsByTags = new ConcurrentHashMap<>();
    private final Map<Tags, Double> errorsByTags = new ConcurrentHashMap<>();

    private final Map<Tags, Double> loadMsByTags = new ConcurrentHashMap<>();
    private final Map<Tags, Double> indexMsByTags = new ConcurrentHashMap<>();
    private final Map<Tags, Double> dataBytesByTags = new ConcurrentHashMap<>();
    private final Map<Tags, Double> indexBytesByTags = new ConcurrentHashMap<>();

    private final MultiGauge p50;
    private final MultiGauge p95;
    private final MultiGauge p99;
    private final MultiGauge opsPerSecond;
    private final MultiGauge errors;
    private final MultiGauge loadMillis;
    private final MultiGauge indexBuildMillis;
    private final MultiGauge dataBytes;
    private final MultiGauge indexBytes;

    /**
     * Создаёт экспортёр и регистрирует MultiGauge в реестре.
     *
     * @param registry Micrometer registry (Actuator)
     */
    public BenchMetricsExporter(MeterRegistry registry) {
        this.p50 = MultiGauge.builder("bench.p50.ms")
                .description("p50 latency последнего прогона (мс), из LoadRunner/HdrHistogram")
                .baseUnit("milliseconds")
                .register(registry);
        this.p95 = MultiGauge.builder("bench.p95.ms")
                .description("p95 latency последнего прогона (мс), из LoadRunner/HdrHistogram")
                .baseUnit("milliseconds")
                .register(registry);
        this.p99 = MultiGauge.builder("bench.p99.ms")
                .description("p99 latency последнего прогона (мс), из LoadRunner/HdrHistogram")
                .baseUnit("milliseconds")
                .register(registry);
        this.opsPerSecond = MultiGauge.builder("bench.ops.per.second")
                .description("Пропускная способность последнего прогона")
                .baseUnit("operations")
                .register(registry);
        this.errors = MultiGauge.builder("bench.errors")
                .description("Число ошибок за окно измерения последнего прогона")
                .register(registry);
        this.loadMillis = MultiGauge.builder("bench.load.ms")
                .description("Время заливки данных (мс)")
                .baseUnit("milliseconds")
                .register(registry);
        this.indexBuildMillis = MultiGauge.builder("bench.index.build.ms")
                .description("Время построения индексов (мс)")
                .baseUnit("milliseconds")
                .register(registry);
        this.dataBytes = MultiGauge.builder("bench.data.bytes")
                .description("Размер данных на диске после заливки")
                .baseUnit("bytes")
                .register(registry);
        this.indexBytes = MultiGauge.builder("bench.index.bytes")
                .description("Размер индексов на диске после заливки")
                .baseUnit("bytes")
                .register(registry);
    }

    /**
     * Публикует итог прогона нагрузки.
     *
     * @param request параметры прогона (для меток)
     * @param result результат LoadRunner
     */
    public void recordRun(BenchRunRequest request, BenchRunResult result) {
        Tags tags = Tags.of(
                "storage_case", request.storageCase().name(),
                "operation", request.operation().name(),
                "concurrency", String.valueOf(result.concurrency()),
                "volume", formatVolume(request.maxProductId())
        );
        p50ByTags.put(tags, result.p50Ms());
        p95ByTags.put(tags, result.p95Ms());
        p99ByTags.put(tags, result.p99Ms());
        opsByTags.put(tags, result.opsPerSecond());
        errorsByTags.put(tags, (double) result.errors());

        publish(p50, p50ByTags);
        publish(p95, p95ByTags);
        publish(p99, p99ByTags);
        publish(opsPerSecond, opsByTags);
        publish(errors, errorsByTags);

        log.info(
                "Prometheus: bench p95={} ms ops={} case={} op={} c={} volume={}",
                result.p95Ms(),
                result.opsPerSecond(),
                request.storageCase(),
                request.operation(),
                result.concurrency(),
                formatVolume(request.maxProductId())
        );
    }

    /**
     * Публикует итог заливки.
     *
     * @param result результат DataLoadService
     */
    public void recordLoad(DataLoadResult result) {
        Tags tags = Tags.of(
                "storage_case", result.storageCase().name(),
                "volume", formatVolume(result.productsLoaded())
        );
        loadMsByTags.put(tags, (double) result.loadMillis());
        indexMsByTags.put(tags, (double) result.indexBuildMillis());
        if (result.dataBytes() >= 0) {
            dataBytesByTags.put(tags, (double) result.dataBytes());
        }
        if (result.indexBytes() >= 0) {
            indexBytesByTags.put(tags, (double) result.indexBytes());
        }

        publish(loadMillis, loadMsByTags);
        publish(indexBuildMillis, indexMsByTags);
        publish(dataBytes, dataBytesByTags);
        publish(indexBytes, indexBytesByTags);

        log.info(
                "Prometheus: load loadMs={} indexMs={} case={} volume={}",
                result.loadMillis(),
                result.indexBuildMillis(),
                result.storageCase(),
                formatVolume(result.productsLoaded())
        );
    }

    /**
     * Перерегистрирует все накопленные ряды MultiGauge (overwrite).
     *
     * @param gauge MultiGauge
     * @param values карта тегов → значение
     */
    private void publish(MultiGauge gauge, Map<Tags, Double> values) {
        gauge.register(
                values.entrySet().stream()
                        .map(e -> MultiGauge.Row.of(e.getKey(), e.getValue()))
                        .collect(Collectors.toList()),
                true
        );
    }

    /**
     * Человекочитаемый объём для метки (10K / 1M / число).
     *
     * @param count число товаров
     * @return метка volume
     */
    static String formatVolume(long count) {
        if (count <= 0) {
            return "0";
        }
        if (count % 1_000_000L == 0) {
            return (count / 1_000_000L) + "M";
        }
        if (count % 1_000L == 0) {
            return (count / 1_000L) + "K";
        }
        return String.format(Locale.ROOT, "%d", count);
    }
}
