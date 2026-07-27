package ru.bench.common.dataset;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Locale;
import tools.jackson.databind.json.JsonMapper;

/**
 * CLI-генератор tags.jsonl и products.jsonl.
 */
public final class GenerateDatasetMain {

    private GenerateDatasetMain() {
    }

    /**
     * Точка входа.
     *
     * @param args аргументы CLI
     */
    public static void main(String[] args) throws IOException {
        Args parsed = Args.parse(args);
        Path outDir = Path.of(parsed.outDir).toAbsolutePath().normalize();
        Files.createDirectories(outDir);

        JsonMapper mapper = JsonMapper.builder().build();

        if (parsed.fromId <= 1L) {
            writeTags(outDir, parsed.tagCount, parsed.generator, mapper);
            writeProducts(outDir, parsed.volume, parsed.tagCount, parsed.generator, 1L, false, mapper);
        } else {
            writeProducts(outDir, parsed.volume, parsed.tagCount, parsed.generator, parsed.fromId, true, mapper);
        }

        String meta = String.format(
                Locale.ROOT,
                """
                {
                  "volume": %d,
                  "tagCount": %d,
                  "generator": %d,
                  "fromId": %d
                }
                """,
                parsed.volume,
                parsed.tagCount,
                parsed.generator,
                parsed.fromId
        );
        Files.writeString(outDir.resolve("meta.json"), meta, StandardCharsets.UTF_8);
        System.out.printf(
                Locale.ROOT,
                "OK: volume=%d, tags=%d, out=%s%n",
                parsed.volume,
                parsed.tagCount,
                outDir
        );
    }

    /**
     * Пишет tags.jsonl.
     *
     * @param outDir каталог
     * @param tagCount число тегов
     * @param generator параметр
     * @param mapper Jackson
     */
    private static void writeTags(Path outDir, int tagCount, long generator, JsonMapper mapper) throws IOException {
        Path path = outDir.resolve("tags.jsonl");
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            for (long tagId = 1; tagId <= tagCount; tagId++) {
                TagRecord tag = new TagRecord(tagId, DatasetGeneratorLogic.tagName(tagId, generator));
                writer.write(mapper.writeValueAsString(tag));
                writer.newLine();
            }
        }
    }

    /**
     * Пишет или дописывает products.jsonl.
     *
     * @param outDir каталог
     * @param volume конечный объём
     * @param tagCount словарь тегов
     * @param generator параметр
     * @param fromId начальный id
     * @param append дозапись
     * @param mapper Jackson
     */
    private static void writeProducts(
            Path outDir,
            long volume,
            int tagCount,
            long generator,
            long fromId,
            boolean append,
            JsonMapper mapper
    ) throws IOException {
        Path path = outDir.resolve("products.jsonl");
        StandardOpenOption[] options = append
                ? new StandardOpenOption[]{StandardOpenOption.CREATE, StandardOpenOption.APPEND}
                : new StandardOpenOption[]{StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE};
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8, options)) {
            for (long productId = fromId; productId <= volume; productId++) {
                List<String> tags = DatasetGeneratorLogic.productTags(productId, generator, tagCount);
                ProductRecord product = new ProductRecord(
                        productId,
                        "product-" + productId,
                        DatasetGeneratorLogic.productPrice(productId, generator),
                        DatasetGeneratorLogic.productCreatedAt(productId),
                        tags
                );
                writer.write(mapper.writeValueAsString(product));
                writer.newLine();
            }
        }
    }

    /**
     * Аргументы CLI.
     *
     * @param volume объём
     * @param tagCount число тегов
     * @param generator параметр
     * @param outDir каталог
     * @param fromId начальный id
     */
    private record Args(long volume, int tagCount, long generator, String outDir, long fromId) {

        /**
         * Разбирает argv.
         *
         * @param args argv
         * @return аргументы
         */
        static Args parse(String[] args) {
            String volume = null;
            int tagCount = 100_000;
            long generator = 42L;
            String outDir = null;
            long fromId = 1L;
            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
                switch (arg) {
                    case "--volume" -> volume = requireValue(args, ++i, arg);
                    case "--tag-count" -> tagCount = Integer.parseInt(requireValue(args, ++i, arg));
                    case "--generator" -> generator = Long.parseLong(requireValue(args, ++i, arg));
                    case "--out-dir" -> outDir = requireValue(args, ++i, arg);
                    case "--from-id" -> fromId = Long.parseLong(requireValue(args, ++i, arg));
                    default -> throw new IllegalArgumentException("Неизвестный аргумент: " + arg);
                }
            }
            if (volume == null || outDir == null) {
                throw new IllegalArgumentException(
                        "Нужны --volume и --out-dir. Пример: --volume 10K --tag-count 5000 --generator 42 --out-dir data/v10k"
                );
            }
            return new Args(DatasetGeneratorLogic.parseVolume(volume), tagCount, generator, outDir, fromId);
        }

        /**
         * Берёт значение после флага.
         *
         * @param args argv
         * @param index индекс
         * @param flag флаг
         * @return значение
         */
        private static String requireValue(String[] args, int index, String flag) {
            if (index >= args.length) {
                throw new IllegalArgumentException("Нет значения для " + flag);
            }
            return args[index];
        }
    }
}
