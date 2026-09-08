package br.com.triaige.mcpai.infrastructure.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.SqsClientBuilder;
import software.amazon.awssdk.services.textract.TextractClient;
import software.amazon.awssdk.services.textract.TextractClientBuilder;

import java.net.URI;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class AwsConfig {

    private final AwsProperties awsProperties;

    @Bean
    public SqsClient sqsClient() {
        SqsClientBuilder builder = SqsClient.builder()
                .region(Region.of(awsProperties.getRegion()));

        String endpoint = awsProperties.getSqs().getEndpoint();
        if (endpoint != null && !endpoint.isBlank()) {
            log.info("Using custom SQS endpoint: {}", endpoint);
            builder.endpointOverride(URI.create(endpoint))
                   .credentialsProvider(StaticCredentialsProvider.create(
                           AwsBasicCredentials.create("test", "test")));
        } else {
            builder.credentialsProvider(DefaultCredentialsProvider.create());
        }

        return builder.build();
    }

    @Bean
    public S3Client s3Client() {
        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(awsProperties.getRegion()));

        String endpoint = awsProperties.getS3().getEndpoint();
        if (endpoint != null && !endpoint.isBlank()) {
            log.info("Using custom S3 endpoint: {}", endpoint);
            builder.endpointOverride(URI.create(endpoint))
                   .forcePathStyle(true)
                   .credentialsProvider(StaticCredentialsProvider.create(
                           AwsBasicCredentials.create("test", "test")));
        } else {
            builder.credentialsProvider(DefaultCredentialsProvider.create());
        }

        return builder.build();
    }

    /**
     * Amazon Textract (T1). Sem suporte na LocalStack Community — em dev/ho local sem
     * credenciais AWS reais, as chamadas de OCR falham e o documento é marcado
     * OCR_FAILED; isso é esperado fora de um ambiente com acesso real à Textract.
     */
    @Bean
    public TextractClient textractClient() {
        TextractClientBuilder builder = TextractClient.builder()
                .region(Region.of(awsProperties.getRegion()));

        String endpoint = awsProperties.getTextract().getEndpoint();
        if (endpoint != null && !endpoint.isBlank()) {
            log.info("Using custom Textract endpoint: {}", endpoint);
            builder.endpointOverride(URI.create(endpoint))
                   .credentialsProvider(StaticCredentialsProvider.create(
                           AwsBasicCredentials.create("test", "test")));
        } else {
            builder.credentialsProvider(DefaultCredentialsProvider.create());
        }

        return builder.build();
    }
}
