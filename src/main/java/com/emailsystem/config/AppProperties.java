package com.emailsystem.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.stereotype.Component;

import java.util.List;

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

    @NestedConfigurationProperty
    private Google google = new Google();

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

    @Getter
    @Setter
    public static class Google {

        private String clientId;
        private String clientSecret;

        private String redirectUri = "http://localhost:8080/api/oauth/google/callback";

        private String successRedirect = "/accounts.html";

        private String projectId;
        private String pubsubTopic;
        private String pubsubSubscription;

        private List<String> scopes = List.of(
                "https://www.googleapis.com/auth/gmail.readonly",
                "https://www.googleapis.com/auth/gmail.send");

        private boolean enabled = false;

        public String topicName() {
            return "projects/" + projectId + "/topics/" + pubsubTopic;
        }

        public String subscriptionName() {
            return "projects/" + projectId + "/subscriptions/" + pubsubSubscription;
        }
    }
}
