package br.com.triaige.mcpai.infrastructure.textract;

import br.com.triaige.mcpai.domain.exception.OcrProcessingException;
import br.com.triaige.mcpai.infrastructure.config.McpProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.services.textract.TextractClient;
import software.amazon.awssdk.services.textract.model.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * T1 — Amazon Textract em modo assíncrono (spec seção 5.2): StartDocumentTextDetection +
 * polling de GetDocumentTextDetection, obrigatório para suportar documentos multi-página.
 * Polling com backoff exponencial (2s -> 15s), timeout total de 5 minutos por documento.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TextractOcrClient {

    private final TextractClient textractClient;
    private final McpProperties mcpProperties;

    public OcrResult extractText(String bucket, String objectKey) {
        McpProperties.Ocr config = mcpProperties.getOcr();
        String jobId = startJob(bucket, objectKey);
        pollUntilDone(jobId, config);
        return collectResult(jobId);
    }

    private String startJob(String bucket, String objectKey) {
        try {
            StartDocumentTextDetectionRequest request = StartDocumentTextDetectionRequest.builder()
                    .documentLocation(DocumentLocation.builder()
                            .s3Object(S3Object.builder().bucket(bucket).name(objectKey).build())
                            .build())
                    .build();
            return textractClient.startDocumentTextDetection(request).jobId();
        } catch (ThrottlingException | ProvisionedThroughputExceededException e) {
            throw new OcrProcessingException("OCR_THROTTLED", "Textract throttling ao iniciar job", true, e);
        } catch (InvalidS3ObjectException | UnsupportedDocumentException | DocumentTooLargeException
                 | BadDocumentException e) {
            throw new OcrProcessingException("OCR_INVALID_DOCUMENT", "Documento inválido/corrompido para OCR: "
                    + e.getMessage(), false, e);
        } catch (AwsServiceException e) {
            boolean retryable = e.statusCode() >= 500;
            throw new OcrProcessingException("OCR_START_FAILED", "Falha ao iniciar job Textract: " + e.getMessage(),
                    retryable, e);
        }
    }

    private void pollUntilDone(String jobId, McpProperties.Ocr config) {
        long deadline = System.currentTimeMillis() + config.getPollTimeoutMs();
        long delay = config.getPollInitialDelayMs();

        while (true) {
            JobStatus status = fetchStatus(jobId);
            if (status == JobStatus.SUCCEEDED || status == JobStatus.PARTIAL_SUCCESS) {
                return;
            }
            if (status == JobStatus.FAILED) {
                throw new OcrProcessingException("OCR_JOB_FAILED", "Textract job failed: jobId=" + jobId, false);
            }
            if (System.currentTimeMillis() >= deadline) {
                throw new OcrProcessingException("OCR_TIMEOUT", "Timeout aguardando Textract: jobId=" + jobId, true);
            }
            sleep(delay);
            delay = Math.min((long) (delay * 1.5), config.getPollMaxDelayMs());
        }
    }

    private JobStatus fetchStatus(String jobId) {
        try {
            return textractClient.getDocumentTextDetection(
                    GetDocumentTextDetectionRequest.builder().jobId(jobId).maxResults(1).build()).jobStatus();
        } catch (ThrottlingException | ProvisionedThroughputExceededException e) {
            throw new OcrProcessingException("OCR_THROTTLED", "Textract throttling durante polling", true, e);
        } catch (AwsServiceException e) {
            boolean retryable = e.statusCode() >= 500;
            throw new OcrProcessingException("OCR_POLL_FAILED", "Falha ao consultar status do job Textract: "
                    + e.getMessage(), retryable, e);
        }
    }

    private OcrResult collectResult(String jobId) {
        List<Block> blocks = new ArrayList<>();
        String nextToken = null;
        int pages = 0;

        do {
            GetDocumentTextDetectionRequest request = GetDocumentTextDetectionRequest.builder()
                    .jobId(jobId)
                    .nextToken(nextToken)
                    .build();
            GetDocumentTextDetectionResponse response = textractClient.getDocumentTextDetection(request);
            blocks.addAll(response.blocks());
            if (response.documentMetadata() != null && response.documentMetadata().pages() != null) {
                pages = Math.max(pages, response.documentMetadata().pages());
            }
            nextToken = response.nextToken();
        } while (nextToken != null && !nextToken.isBlank());

        String text = blocks.stream()
                .filter(b -> b.blockType() == BlockType.LINE)
                .map(Block::text)
                .collect(Collectors.joining("\n"));

        return new OcrResult(text, pages, text.length());
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OcrProcessingException("OCR_INTERRUPTED", "Polling de OCR interrompido", true, e);
        }
    }
}
