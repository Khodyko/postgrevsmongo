package ru.bench.common.load;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import org.HdrHistogram.ConcurrentHistogram;
import org.HdrHistogram.Histogram;
import ru.bench.common.dto.BenchRunRequest;
import ru.bench.common.dto.BenchRunResult;

/**
 * Многопоточный генератор нагрузки с прогревом и перцентилями.
 */
public class LoadRunner {

    /**
     * Запускает прогрев и измерение.
     *
     * @param request параметры прогона
     * @param operation операция сценария
     * @return результат с p50/p95/p99
     */
    public BenchRunResult run(BenchRunRequest request, ScenarioOperation operation) {
        int concurrency = Math.max(1, request.concurrency());
        validatePoolBound(concurrency);

        runPhase(request, operation, request.warmupSeconds(), null, null);

        ConcurrentHistogram histogram = new ConcurrentHistogram(TimeUnit.MINUTES.toNanos(1), 3);
        histogram.setAutoResize(true);
        LongAdder errors = new LongAdder();
        AtomicLong ops = new AtomicLong();

        long started = System.nanoTime();
        runPhase(request, operation, request.measureSeconds(), histogram, errors);
        long elapsedNanos = System.nanoTime() - started;
        ops.set(histogram.getTotalCount());

        double elapsedSec = Math.max(0.001, elapsedNanos / 1_000_000_000.0);
        return new BenchRunResult(
                request.storageCase(),
                request.operation(),
                concurrency,
                ops.get(),
                errors.sum(),
                ops.get() / elapsedSec,
                nanosToMs(histogram, 50),
                nanosToMs(histogram, 95),
                nanosToMs(histogram, 99),
                histogram.getMaxValue() / 1_000_000.0,
                request.measureSeconds()
        );
    }

    /**
     * Перцентиль гистограммы в миллисекундах.
     *
     * @param histogram гистограмма
     * @param percentile перцентиль
     * @return значение в мс
     */
    private double nanosToMs(Histogram histogram, double percentile) {
        return histogram.getValueAtPercentile(percentile) / 1_000_000.0;
    }

    /**
     * Проверяет, что concurrency не превышает пул 32.
     *
     * @param concurrency число потоков
     */
    private void validatePoolBound(int concurrency) {
        if (concurrency > 32) {
            throw new IllegalArgumentException("concurrency=" + concurrency + " превышает пул соединений 32");
        }
    }

    /**
     * Выполняет фазу прогрева или измерения.
     *
     * @param request запрос
     * @param operation операция
     * @param seconds длительность
     * @param histogram гистограмма (null на прогреве)
     * @param errors счётчик ошибок (null на прогреве)
     */
    private void runPhase(
            BenchRunRequest request,
            ScenarioOperation operation,
            int seconds,
            ConcurrentHistogram histogram,
            LongAdder errors
    ) {
        if (seconds <= 0) {
            return;
        }
        int concurrency = Math.max(1, request.concurrency());
        AtomicBoolean running = new AtomicBoolean(true);
        CountDownLatch ready = new CountDownLatch(concurrency);
        CountDownLatch done = new CountDownLatch(concurrency);
        ScenarioOperation.OperationContext base = ScenarioOperation.OperationContext.from(request);

        try (ExecutorService pool = Executors.newFixedThreadPool(concurrency)) {
            for (int i = 0; i < concurrency; i++) {
                pool.submit(() -> {
                    ready.countDown();
                    try {
                        ready.await();
                        while (running.get()) {
                            long t0 = System.nanoTime();
                            try {
                                operation.execute(base);
                                long nanos = System.nanoTime() - t0;
                                if (histogram != null) {
                                    histogram.recordValue(Math.max(1L, nanos));
                                }
                            } catch (Exception ex) {
                                if (errors != null) {
                                    errors.increment();
                                }
                            }
                        }
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }
            sleepSeconds(seconds);
            running.set(false);
            if (!done.await(seconds + 30L, TimeUnit.SECONDS)) {
                pool.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Прогон прерван", e);
        }
    }

    /**
     * Пауза на заданное число секунд.
     *
     * @param seconds секунды
     */
    private void sleepSeconds(int seconds) {
        try {
            TimeUnit.SECONDS.sleep(seconds);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Пауза прервана", e);
        }
    }
}
