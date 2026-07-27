package ru.bench.mongo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Приложение бенчмарка MongoDB.
 */
@SpringBootApplication(scanBasePackages = {"ru.bench.mongo", "ru.bench.common"})
public class MongoBenchApplication {

    /**
     * Точка входа.
     *
     * @param args аргументы
     */
    public static void main(String[] args) {
        SpringApplication.run(MongoBenchApplication.class, args);
    }
}
