package ru.bench.common.dataset;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Детерминированная генерация текстовых тегов и привязок товар→теги.
 */
public final class DatasetGeneratorLogic {

    private static final String[] STEMS = {
            "electronics", "fashion", "home", "garden", "sports", "outdoor", "kids", "toys",
            "beauty", "health", "auto", "tools", "office", "pets", "books", "music",
            "grocery", "furniture", "kitchen", "bathroom", "winter", "summer", "sale", "premium",
            "eco", "organic", "wireless", "portable", "durable", "compact", "luxury", "budget",
            "cotton", "leather", "metal", "plastic", "wood", "glass", "smart", "classic"
    };

    private static final String[] MODIFIERS = {
            "pro", "plus", "max", "lite", "neo", "ultra", "basic", "deluxe",
            "red", "blue", "green", "black", "white", "large", "small", "mid"
    };

    private DatasetGeneratorLogic() {
    }

    /**
     * Детерминированное неотрицательное long из seed и частей.
     *
     * @param seed параметр генератора
     * @param parts дополнительные части
     * @return значение
     */
    public static long stableLong(long seed, long... parts) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(longToBytes(seed));
            for (long part : parts) {
                digest.update(longToBytes(part));
            }
            byte[] hash = digest.digest();
            return ByteBuffer.wrap(hash, 0, 8).getLong() & Long.MAX_VALUE;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 недоступен", e);
        }
    }

    /**
     * Текстовое имя тега по id словаря.
     *
     * @param tagId id тега
     * @param generator параметр генератора
     * @return имя вида electronics-pro-0042
     */
    public static String tagName(long tagId, long generator) {
        String stem = STEMS[(int) (stableLong(generator, 1L, tagId) % STEMS.length)];
        String modifier = MODIFIERS[(int) (stableLong(generator, 2L, tagId) % MODIFIERS.length)];
        long suffix = stableLong(generator, 3L, tagId) % 10_000L;
        return String.format(Locale.ROOT, "%s-%s-%04d", stem, modifier, suffix);
    }

    /**
     * Список текстовых тегов для товара: 3–15 штук с перекосом к «горячим».
     *
     * @param productId id товара
     * @param generator параметр генератора
     * @param tagCount размер словаря
     * @return список имён тегов
     */
    public static List<String> productTags(long productId, long generator, int tagCount) {
        int n = 3 + (int) (stableLong(generator, 4L, productId) % 13L);
        int hotBorder = Math.max(1, tagCount / 20);
        Set<String> seen = new LinkedHashSet<>();
        List<String> result = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            long roll = stableLong(generator, 5L, productId, i);
            long tid;
            if (roll % 100L < 40L) {
                tid = 1L + (roll % hotBorder);
            } else {
                tid = 1L + (roll % tagCount);
            }
            String name = tagName(tid, generator);
            if (seen.add(name)) {
                result.add(name);
            }
        }
        return result;
    }

    /**
     * Разбирает объём вида 10K / 1M / 100000.
     *
     * @param raw строка
     * @return число товаров
     */
    public static long parseVolume(String raw) {
        String s = raw.trim().toUpperCase(Locale.ROOT).replace("_", "");
        if (s.endsWith("K")) {
            return (long) (Double.parseDouble(s.substring(0, s.length() - 1)) * 1_000L);
        }
        if (s.endsWith("M")) {
            return (long) (Double.parseDouble(s.substring(0, s.length() - 1)) * 1_000_000L);
        }
        return Long.parseLong(s);
    }

    /**
     * Цена товара.
     *
     * @param productId id
     * @param generator параметр
     * @return цена
     */
    public static double productPrice(long productId, long generator) {
        long cents = 100L + (stableLong(generator, 6L, productId) % 99_900L);
        return cents / 100.0;
    }

    /**
     * Дата создания в ISO-8601.
     *
     * @param productId id
     * @return строка даты
     */
    public static String productCreatedAt(long productId) {
        int day = 1 + (int) (productId % 28L);
        return String.format(Locale.ROOT, "2024-01-%02dT10:00:00Z", day);
    }

    /**
     * long → 8 байт big-endian.
     *
     * @param value значение
     * @return байты
     */
    private static byte[] longToBytes(long value) {
        return ByteBuffer.allocate(8).putLong(value).array();
    }
}
