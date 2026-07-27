package ru.bench.postgres;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Приложение бенчмарка PostgreSQL.
 */
@SpringBootApplication(scanBasePackages = {"ru.bench.postgres", "ru.bench.common"})
public class PostgresBenchApplication {

    /**
     * Точка входа.
     *
     * @param args аргументы
     */
    public static void main(String[] args) {
        SpringApplication.run(PostgresBenchApplication.class, args);
    }
}
