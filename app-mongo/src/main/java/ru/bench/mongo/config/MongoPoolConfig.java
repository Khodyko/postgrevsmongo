package ru.bench.mongo.config;

import com.mongodb.MongoClientSettings;
import org.springframework.boot.mongodb.autoconfigure.MongoClientSettingsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Фиксация размера пула Mongo = 32.
 */
@Configuration
public class MongoPoolConfig {

    /**
     * Кастомизатор пула соединений.
     *
     * @return customizer
     */
    @Bean
    public MongoClientSettingsBuilderCustomizer mongoPoolSizeCustomizer() {
        return (MongoClientSettings.Builder builder) ->
                builder.applyToConnectionPoolSettings(pool -> pool.maxSize(32).minSize(4));
    }
}
