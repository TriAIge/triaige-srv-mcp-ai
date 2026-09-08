package br.com.triaige.mcpai.infrastructure.s3;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

/**
 * Leitura de objetos S3 trusted — contraparte de {@link TrustedDocumentWriter}, usada pela
 * Fase 3 (spec seção 4.2, passo 2) para ler de volta o que este mesmo serviço já escreveu.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TrustedDocumentReader {

    private final S3Client s3Client;
    private final ObjectMapper objectMapper;

    public TrustedDocumentPayload read(String bucket, String objectKey) {
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .build();

        try (ResponseInputStream<GetObjectResponse> object = s3Client.getObject(request)) {
            return objectMapper.readValue(object, TrustedDocumentPayload.class);
        } catch (Exception e) {
            log.error("Failed to read trusted object: bucket={}, key={}", bucket, objectKey, e);
            throw new IllegalStateException(
                    "Falha ao ler objeto trusted s3://%s/%s".formatted(bucket, objectKey), e);
        }
    }
}
