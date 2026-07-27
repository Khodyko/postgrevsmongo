package ru.bench.common.load;

import java.util.concurrent.ThreadLocalRandom;
import ru.bench.common.dto.BenchOperation;
import ru.bench.common.dto.BenchRunRequest;
import ru.bench.common.dto.StorageCase;

/**
 * Контракт одной операции сценария для генератора нагрузки.
 */
@FunctionalInterface
public interface ScenarioOperation {

    /**
     * Выполняет одну операцию.
     *
     * @param context параметры текущего вызова
     */
    void execute(OperationContext context) throws Exception;

    /**
     * Контекст одного вызова операции.
     *
     * @param storageCase вариант хранения
     * @param operation тип операции
     * @param pageSize размер страницы
     * @param topN top-N
     * @param tag имя тега (может быть null — сгенерировать)
     * @param productId id товара (может быть null — сгенерировать)
     * @param maxProductId верхняя граница id
     * @param tagCount размер словаря тегов
     */
    record OperationContext(
            StorageCase storageCase,
            BenchOperation operation,
            int pageSize,
            int topN,
            String tag,
            Long productId,
            long maxProductId,
            int tagCount
    ) {
        /**
         * Собирает контекст из запроса прогона.
         *
         * @param request запрос
         * @return контекст
         */
        public static OperationContext from(BenchRunRequest request) {
            return new OperationContext(
                    request.storageCase(),
                    request.operation(),
                    request.pageSize(),
                    request.topN(),
                    request.tag(),
                    request.productId(),
                    request.maxProductId(),
                    request.tagCount()
            );
        }

        /**
         * Выбирает id товара для вызова.
         *
         * @return id в диапазоне 1..maxProductId
         */
        public long resolveProductId() {
            if (productId != null && productId > 0) {
                return productId;
            }
            long max = Math.max(1L, maxProductId);
            return 1L + ThreadLocalRandom.current().nextLong(max);
        }

        /**
         * Выбирает номер тега 1..tagCount для построения имени снаружи.
         *
         * @return номер тега
         */
        public long resolveTagId() {
            int max = Math.max(1, tagCount);
            return 1L + ThreadLocalRandom.current().nextInt(max);
        }
    }
}
