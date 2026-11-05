package br.com.triaige.mcpai.infrastructure.config;

import io.micrometer.cloudwatch2.CloudWatchConfig;
import io.micrometer.cloudwatch2.CloudWatchMeterRegistry;
import io.micrometer.core.instrument.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudwatch.CloudWatchAsyncClient;

import java.time.Duration;

/**
 * Exporta as métricas do Micrometer para o CloudWatch (namespace em
 * management.cloudwatch.metrics.export.namespace). O Spring Boot 3 não autoconfigura o
 * registry do CloudWatch, então sem este bean as métricas ficavam só em memória.
 * Ligado por CLOUDWATCH_METRICS_ENABLED=true (desligado em dev).
 */
@Configuration
@ConditionalOnProperty(name = "management.cloudwatch.metrics.export.enabled", havingValue = "true")
public class CloudWatchMetricsConfig {

    private static final String PREFIX = "management.cloudwatch.metrics.export.";

    @Bean(destroyMethod = "close")
    public CloudWatchAsyncClient cloudWatchAsyncClient(Environment env) {
        return CloudWatchAsyncClient.builder()
                .region(Region.of(env.getProperty("aws.region", "us-east-1")))
                .build();
    }

    @Bean
    public CloudWatchMeterRegistry cloudWatchMeterRegistry(CloudWatchAsyncClient client, Environment env) {
        String namespace = env.getRequiredProperty(PREFIX + "namespace");
        String stepText = env.getProperty(PREFIX + "step");
        Duration step = stepText == null ? Duration.ofMinutes(1) : DurationStyle.detectAndParse(stepText);
        CloudWatchConfig config = new CloudWatchConfig() {
            @Override
            public String get(String key) {
                return null;
            }

            @Override
            public String namespace() {
                return namespace;
            }

            @Override
            public Duration step() {
                return step;
            }
        };
        return new CloudWatchMeterRegistry(config, Clock.SYSTEM, client);
    }
}
