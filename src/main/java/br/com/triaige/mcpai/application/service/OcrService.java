package br.com.triaige.mcpai.application.service;

import br.com.triaige.mcpai.domain.entity.AiToolCall;
import br.com.triaige.mcpai.domain.entity.LegalDocument;
import br.com.triaige.mcpai.domain.entity.ProcessingStep;
import br.com.triaige.mcpai.domain.enums.AiToolName;
import br.com.triaige.mcpai.domain.enums.AiToolProvider;
import br.com.triaige.mcpai.domain.enums.DocumentStatus;
import br.com.triaige.mcpai.domain.enums.ProcessingStepName;
import br.com.triaige.mcpai.domain.exception.OcrProcessingException;
import br.com.triaige.mcpai.domain.exception.RawObjectNotFoundException;
import br.com.triaige.mcpai.domain.exception.UnsupportedDocumentFormatException;
import br.com.triaige.mcpai.infrastructure.config.McpProperties;
import br.com.triaige.mcpai.infrastructure.persistence.LegalDocumentRepository;
import br.com.triaige.mcpai.infrastructure.s3.S3DocumentReader;
import br.com.triaige.mcpai.infrastructure.textract.OcrResult;
import br.com.triaige.mcpai.infrastructure.textract.TextractOcrClient;
import br.com.triaige.mcpai.shared.metrics.McpMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

/** T1 — orquestra validação de formato, checagem de existência no S3 raw, OCR e registro (spec seção 5.2). */
@Slf4j
@Service
@RequiredArgsConstructor
public class OcrService {

    private final S3DocumentReader s3DocumentReader;
    private final TextractOcrClient textractOcrClient;
    private final McpProperties mcpProperties;
    private final LegalDocumentRepository legalDocumentRepository;
    private final ProcessingStepService processingStepService;
    private final AiToolCallService aiToolCallService;
    private final McpMetrics metrics;

    /**
     * Executa T1 para um documento. Lança {@link UnsupportedDocumentFormatException} ou
     * {@link RawObjectNotFoundException} (falhas imediatas, sem retry) ou
     * {@link OcrProcessingException} (pode ser retryable) — cabe ao chamador decidir o
     * roteamento pós-falha (spec seção 5.2/5.8). Em sucesso, atualiza o documento para
     * OCR_DONE e devolve o texto extraído (mantido em memória).
     */
    public OcrResult process(UUID sessionId, LegalDocument document) {
        MDC.put("documentId", document.getId().toString());
        MDC.put("tool", "ocr");
        long startedAt = System.currentTimeMillis();

        ProcessingStep step = processingStepService.startDocumentStep(sessionId, document.getId(), ProcessingStepName.OCR);
        AiToolCall call = aiToolCallService.start(sessionId, AiToolName.OCR, AiToolProvider.AWS_TEXTRACT,
                Map.of("documentId", document.getId(), "rawObjectKey", document.getRawObjectKey()));

        try {
            validateFormat(document.getContentType());
            validateExists(document);
            markInProgress(document);

            OcrResult result = textractOcrClient.extractText(document.getRawBucket(), document.getRawObjectKey());

            markStatus(document, DocumentStatus.OCR_DONE, null);
            processingStepService.complete(step.getId());
            aiToolCallService.completeSuccess(call.getId(), Map.of(
                    "paginasProcessadas", result.paginasProcessadas(),
                    "caracteresExtraidos", result.caracteresExtraidos()));
            metrics.recordOcrLatency(System.currentTimeMillis() - startedAt);

            log.info("OCR completed: documentId={}, pages={}, chars={}, latencyMs={}",
                    document.getId(), result.paginasProcessadas(), result.caracteresExtraidos(),
                    System.currentTimeMillis() - startedAt);
            return result;
        } catch (UnsupportedDocumentFormatException | RawObjectNotFoundException e) {
            log.warn("OCR rejected document: documentId={}, code={}, reason={}",
                    document.getId(), e.getCode(), e.getMessage());
            markStatus(document, DocumentStatus.OCR_FAILED, e.getMessage());
            processingStepService.fail(step.getId(), e.getMessage());
            aiToolCallService.completeFailure(call.getId(), e.getMessage());
            metrics.incrementOcrFailure(e.getCode());
            throw e;
        } catch (OcrProcessingException e) {
            log.warn("OCR processing failed: documentId={}, code={}, retryable={}, reason={}",
                    document.getId(), e.getCode(), e.isRetryable(), e.getMessage());
            if (!e.isRetryable()) {
                markStatus(document, DocumentStatus.OCR_FAILED, e.getMessage());
            }
            processingStepService.fail(step.getId(), e.getMessage());
            aiToolCallService.completeFailure(call.getId(), e.getMessage());
            metrics.incrementOcrFailure(e.getCode());
            throw e;
        } finally {
            MDC.remove("documentId");
            MDC.remove("tool");
        }
    }

    private void validateFormat(String contentType) {
        if (!mcpProperties.getOcr().getSupportedContentTypes().contains(contentType)) {
            throw new UnsupportedDocumentFormatException(contentType);
        }
    }

    private void validateExists(LegalDocument document) {
        var size = s3DocumentReader.headObject(document.getRawBucket(), document.getRawObjectKey());
        if (size.isEmpty()) {
            throw new RawObjectNotFoundException(document.getRawBucket(), document.getRawObjectKey());
        }
        if (size.get() == 0) {
            throw new OcrProcessingException("EMPTY_FILE", "Arquivo vazio: " + document.getRawObjectKey(), false);
        }
    }

    private void markInProgress(LegalDocument document) {
        legalDocumentRepository.findById(document.getId()).ifPresent(d -> {
            d.setStatus(DocumentStatus.OCR_IN_PROGRESS);
            legalDocumentRepository.save(d);
        });
    }

    private void markStatus(LegalDocument document, DocumentStatus status, String errorMessage) {
        legalDocumentRepository.findById(document.getId()).ifPresent(d -> {
            d.setStatus(status);
            if (errorMessage != null) {
                d.setErrorMessage(errorMessage.length() > 1000 ? errorMessage.substring(0, 1000) : errorMessage);
            }
            legalDocumentRepository.save(d);
        });
    }
}
