package com.emailsystem.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app")
@Getter
@Setter
public class AppProperties {

    @NestedConfigurationProperty
    private Jwt jwt = new Jwt();

    @NestedConfigurationProperty
    private Crypto crypto = new Crypto();

    @NestedConfigurationProperty
    private Sync sync = new Sync();

    @NestedConfigurationProperty
    private Mail mail = new Mail();

    @NestedConfigurationProperty
    private Cache cache = new Cache();

    @Getter
    @Setter
    public static class Jwt {

        private String secret;

        private long expirationMs = 86_400_000L;
    }

    @Getter
    @Setter
    public static class Crypto {

        private String secret;
    }

    @Getter
    @Setter
    public static class Sync {
        private long intervalMs = 60_000L;
        private long initialDelayMs = 15_000L;
        private int fetchLimit = 50;
    }

    @Getter
    @Setter
    public static class Mail {

        private int timeoutMs = 30_000;
    }

    @Getter
    @Setter
    public static class Cache {

        private long maximumSize = 10_000L;

        private long expireAfterWriteSeconds = 300L;
    }
}
