package br.com.triaige.mcpai.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/** Propriedades do pipeline MCP — tudo configurável, nada hardcoded. */
@Data
@ConfigurationProperties(prefix = "mcp")
public class McpProperties {

    private String schemaVersionSupported = "1.0";
    private Queue queue = new Queue();
    private Ocr ocr = new Ocr();
    private S3 s3 = new S3();
    private Summarization summarization = new Summarization();
    private Callback callback = new Callback();
    private Jurisprudence jurisprudence = new Jurisprudence();

    @Data
    public static class Queue {
        private String docsPreprocessingQueueUrl;
        private Consumer consumer = new Consumer();

        @Data
        public static class Consumer {
            private boolean enabled = true;
            private long pollIntervalMs = 5000;
            private int waitTimeSeconds = 20;
            private int maxMessages = 10;
        }
    }

    @Data
    public static class Ocr {
        private List<String> supportedContentTypes =
                List.of("application/pdf", "image/png", "image/jpeg", "image/tiff");
        private long pollInitialDelayMs = 2000;
        private long pollMaxDelayMs = 15000;
        private long pollTimeoutMs = 300000;
        private String retryQueueUrl;
        private RetryConsumer retryConsumer = new RetryConsumer();
        private List<Long> retryDelaysSeconds = List.of(30L, 120L, 600L);
        private int maxReceiveCount = 3;

        @Data
        public static class RetryConsumer {
            private boolean enabled = true;
            private long pollIntervalMs = 10000;
            private int waitTimeSeconds = 10;
            private int maxMessages = 10;
        }
    }

    @Data
    public static class S3 {
        private String rawDocumentsBucket;
        private String trustedDocumentsBucket;
    }

    @Data
    public static class Summarization {
        private long thresholdTokens = 6000;
        private long thresholdPages = 15;
        private long summaryMaxTokens = 1500;
    }

    @Data
    public static class Callback {
        private String orchestratorBaseUrl;
        private String pathTemplate = "/api/orchestrator/v1/sessions/{sessionId}/mcp-result";
        private String internalToken;
        private int connectTimeoutMs = 5000;
        private int readTimeoutMs = 10000;
        private List<Long> retryDelaysMs = List.of(5000L, 30000L, 120000L);
        private String fallbackQueueUrl;
    }

    @Data
    public static class Jurisprudence {
        private String provider = "mockapi_io";
        private String endpoint;
        private int timeoutMs = 5000;
        private int cacheTtlDays = 7;
    }
}
