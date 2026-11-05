package br.com.triaige.mcpai.infrastructure.config;

import io.micrometer.cloudwatch2.CloudWatchMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import software.amazon.awssdk.services.cloudwatch.CloudWatchAsyncClient;

import static org.assertj.core.api.Assertions.assertThat;

class CloudWatchMetricsConfigTest {

    @Test
    void criaRegistryComNamespaceEStepDasPropriedades() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("aws.region", "us-east-1")
                .withProperty("management.cloudwatch.metrics.export.namespace", "Triaige/Teste")
                .withProperty("management.cloudwatch.metrics.export.step", "1m");
        CloudWatchMetricsConfig config = new CloudWatchMetricsConfig();

        try (CloudWatchAsyncClient client = config.cloudWatchAsyncClient(env)) {
            CloudWatchMeterRegistry registry = config.cloudWatchMeterRegistry(client, env);

            assertThat(registry.config().namingConvention()).isNotNull();
            assertThat(client.serviceClientConfiguration().region().id()).isEqualTo("us-east-1");
            registry.close();
        }
    }

}
