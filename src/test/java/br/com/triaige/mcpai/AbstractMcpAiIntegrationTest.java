package br.com.triaige.mcpai;

import org.junit.jupiter.api.BeforeAll;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;

/**
 * Base de testes de integração do mcp-ai: MySQL (schema gerado pelo Hibernate a partir das
 * entidades JPA, {@code ddl-auto=create-drop} — sem Flyway) + LocalStack (SQS + S3) — mesmo
 * padrão de {@code AbstractIntegrationTest} do triaige-srv-orchestrator. Não existia
 * scaffolding de {@code @SpringBootTest} neste módulo antes da Fase 4 (as 5 suítes de teste
 * pré-existentes cobrem só serviços isolados do pipeline T1-T4).
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AbstractMcpAiIntegrationTest {

    protected static final String RAW_BUCKET = "bucket-triaige-raw-certificacoes-test";
    protected static final String TRUSTED_BUCKET = "bucket-triaige-trusted-certificacoes-test";
    protected static final String DOCS_PREPROCESSING_QUEUE = "triaige-docs-preprocessing-test";
    protected static final String OCR_RETRY_QUEUE = "triaige-mcp-ocr-retry-test";
    protected static final String MCP_CALLBACK_DLQ = "triaige-mcp-callback-dlq-test";

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>(DockerImageName.parse("mysql:8.3"))
            .withDatabaseName("triaige_srv_mcp_ai")
            .withUsername("triaige")
            .withPassword("triaige");

    @Container
    static LocalStackContainer localstack = new LocalStackContainer(
            DockerImageName.parse("localstack/localstack:3.4"))
            .withServices(LocalStackContainer.Service.SQS, LocalStackContainer.Service.S3);

    private static String docsPreprocessingQueueUrl;
    private static String ocrRetryQueueUrl;
    private static String mcpCallbackDlqUrl;

    @LocalServerPort
    protected int port;

    protected final TestRestTemplate restTemplate = new TestRestTemplate();

    @BeforeAll
    static void provisionAwsResources() {
        S3Client s3Client = S3Client.builder()
                .endpointOverride(localstack.getEndpointOverride(LocalStackContainer.Service.S3))
                .region(Region.of(localstack.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(localstack.getAccessKey(), localstack.getSecretKey())))
                .forcePathStyle(true)
                .build();
        s3Client.createBucket(b -> b.bucket(RAW_BUCKET));
        s3Client.createBucket(b -> b.bucket(TRUSTED_BUCKET));

        SqsClient sqsClient = SqsClient.builder()
                .endpointOverride(localstack.getEndpointOverride(LocalStackContainer.Service.SQS))
                .region(Region.of(localstack.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(localstack.getAccessKey(), localstack.getSecretKey())))
                .build();
        docsPreprocessingQueueUrl = sqsClient.createQueue(
                CreateQueueRequest.builder().queueName(DOCS_PREPROCESSING_QUEUE).build()).queueUrl();
        ocrRetryQueueUrl = sqsClient.createQueue(
                CreateQueueRequest.builder().queueName(OCR_RETRY_QUEUE).build()).queueUrl();
        mcpCallbackDlqUrl = sqsClient.createQueue(
                CreateQueueRequest.builder().queueName(MCP_CALLBACK_DLQ).build()).queueUrl();
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");

        registry.add("aws.region", localstack::getRegion);
        registry.add("aws.s3.endpoint", () -> localstack.getEndpointOverride(LocalStackContainer.Service.S3).toString());
        registry.add("aws.sqs.endpoint", () -> localstack.getEndpointOverride(LocalStackContainer.Service.SQS).toString());

        registry.add("mcp.s3.raw-documents-bucket", () -> RAW_BUCKET);
        registry.add("mcp.s3.trusted-documents-bucket", () -> TRUSTED_BUCKET);
        registry.add("mcp.queue.docs-preprocessing-queue-url", () -> docsPreprocessingQueueUrl);
        registry.add("mcp.ocr.retry-queue-url", () -> ocrRetryQueueUrl);
        registry.add("mcp.callback.fallback-queue-url", () -> mcpCallbackDlqUrl);
        // Evita interferência dos pollers com as asserções síncronas dos testes.
        registry.add("mcp.queue.consumer.enabled", () -> "false");
        registry.add("mcp.ocr.retry-consumer.enabled", () -> "false");
    }

    protected String baseUrl() {
        return "http://localhost:" + port;
    }
}
