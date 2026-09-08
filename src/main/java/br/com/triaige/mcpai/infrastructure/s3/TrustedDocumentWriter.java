package br.com.triaige.mcpai.infrastructure.s3;

import br.com.triaige.mcpai.infrastructure.config.McpProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.util.UUID;

/** Escrita em S3 trusted, um objeto por attachment_group_id (spec seção 5.7). */
@Slf4j
@Component
@RequiredArgsConstructor
public class TrustedDocumentWriter {

    private final S3Client s3Client;
    private final ObjectMapper objectMapper;
    private final McpProperties mcpProperties;

    public String write(UUID lawFirmId, UUID sessionId, UUID attachmentGroupId, TrustedDocumentPayload payload) {
        String bucket = mcpProperties.getS3().getTrustedDocumentsBucket();
        String objectKey = "trusted/%s/%s/%s.json".formatted(lawFirmId, sessionId, attachmentGroupId);

        try {
            byte[] body = objectMapper.writeValueAsBytes(payload);
            s3Client.putObject(PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectKey)
                    .contentType("application/json")
                    .build(), RequestBody.fromBytes(body));

            log.info("Trusted document written: bucket={}, objectKey={}", bucket, objectKey);
            return objectKey;
        } catch (S3Exception e) {
            log.error("Failed to write trusted document: bucket={}, objectKey={}", bucket, objectKey, e);
            throw e;
        } catch (Exception e) {
            log.error("Failed to serialize trusted document payload: attachmentGroupId={}", attachmentGroupId, e);
            throw new RuntimeException("Falha ao serializar objeto trusted", e);
        }
    }

    public String bucket() {
        return mcpProperties.getS3().getTrustedDocumentsBucket();
    }
}
