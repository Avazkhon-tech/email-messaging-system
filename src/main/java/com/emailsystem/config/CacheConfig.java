package com.emailsystem.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.transaction.TransactionAwareCacheManagerProxy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager(AppProperties properties) {
        AppProperties.Cache cfg = properties.getCache();

        CaffeineCacheManager caffeine = new CaffeineCacheManager(CacheNames.ACCOUNTS);
        caffeine.setCaffeine(Caffeine.newBuilder()
                .maximumSize(cfg.getMaximumSize())
                .expireAfterWrite(Duration.ofSeconds(cfg.getExpireAfterWriteSeconds()))
                .recordStats());

        return new TransactionAwareCacheManagerProxy(caffeine);
    }
}
