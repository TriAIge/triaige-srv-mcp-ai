package br.com.triaige.mcpai.infrastructure.s3;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class S3DocumentReader {

    private final S3Client s3Client;

    /** Retorna o tamanho do objeto se ele existir no S3 raw, ou empty caso contrário. */
    public Optional<Long> headObject(String bucket, String objectKey) {
        try {
            HeadObjectResponse response = s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectKey)
                    .build());
            return Optional.of(response.contentLength());
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return Optional.empty();
            }
            log.error("Failed to HeadObject raw document: bucket={}, objectKey={}", bucket, objectKey, e);
            throw e;
        }
    }
}
