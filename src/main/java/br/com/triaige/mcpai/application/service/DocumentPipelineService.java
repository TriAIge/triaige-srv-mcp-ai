package br.com.triaige.mcpai.application.service;

import br.com.triaige.mcpai.domain.entity.AiToolCall;
import br.com.triaige.mcpai.domain.entity.LegalDocument;
import br.com.triaige.mcpai.domain.entity.ProcessingStep;
import br.com.triaige.mcpai.domain.enums.AiToolName;
import br.com.triaige.mcpai.domain.enums.AiToolProvider;
import br.com.triaige.mcpai.domain.enums.DocumentStatus;
import br.com.triaige.mcpai.domain.enums.ProcessingStepName;
import br.com.triaige.mcpai.domain.exception.McpException;
import br.com.triaige.mcpai.domain.exception.OcrProcessingException;
import br.com.triaige.mcpai.domain.exception.UnsupportedSchemaVersionException;
import br.com.triaige.mcpai.infrastructure.callback.McpResultCallbackPayload;
import br.com.triaige.mcpai.infrastructure.callback.OrchestratorCallbackClient;
import br.com.triaige.mcpai.infrastructure.config.McpProperties;
import br.com.triaige.mcpai.infrastructure.persistence.LegalDocumentRepository;
import br.com.triaige.mcpai.infrastructure.s3.TrustedDocumentPayload;
import br.com.triaige.mcpai.infrastructure.s3.TrustedDocumentWriter;
import br.com.triaige.mcpai.infrastructure.sqs.QueuePublisher;
import br.com.triaige.mcpai.infrastructure.sqs.message.DocumentReadyMessage;
import br.com.triaige.mcpai.infrastructure.sqs.message.OcrRetryMessage;
import br.com.triaige.mcpai.infrastructure.textract.OcrResult;
import br.com.triaige.mcpai.shared.metrics.McpMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Orquestra o pipeline automático T1 -> T2 -> T3 -> (T4 condicional) -> S3 trusted -> callback
 * (spec seção 5). Ponto de entrada tanto do consumidor de Q2 (novo documento) quanto do
 * consumidor da fila de retry de OCR (reprocessamento de uma parte específica).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentPipelineService {

    private final McpProperties mcpProperties;
    private final LegalDocumentRepository legalDocumentRepository;
    private final SessionPipelineStateRegistry stateRegistry;
    private final OcrService ocrService;
    private final AnonymizationService anonymizationService;
    private final ProcessingStepService processingStepService;
    private final AiToolCallService aiToolCallService;
    private final AttachmentGroupingService attachmentGroupingService;
    private final SummarizationService summarizationService;
    private final TrustedDocumentWriter trustedDocumentWriter;
    private final OrchestratorCallbackClient callbackClient;
    private final QueuePublisher queuePublisher;
    private final McpMetrics metrics;

    public void handleNewSession(DocumentReadyMessage message) {
        validateSchemaVersion(message.getSchemaVersion());
        if (message.getDocuments() == null || message.getDocuments().isEmpty()) {
            throw new McpException("EMPTY_DOCUMENTS", "Mensagem Q2 sem documentos (violação de contrato upstream)") {
            };
        }

        UUID sessionId = message.getSessionId();
        MDC.put("sessionId", sessionId.toString());
        try {
            if (isDuplicate(sessionId)) {
                log.info("Duplicate Q2 message discarded (idempotência de fila): sessionId={}", sessionId);
                return;
            }

            SessionPipelineState state = new SessionPipelineState(sessionId, message.getLawFirmId(),
                    message.getProtocolo(), message.getCorrelationId(), message.getLegalCase(), message.getDocuments());
            stateRegistry.register(state);

            for (DocumentReadyMessage.DocumentInfo docInfo : message.getDocuments()) {
                attemptDocument(state, docInfo, 0);
            }

            finalizeIfComplete(sessionId);
        } finally {
            MDC.remove("sessionId");
        }
    }

    public void handleOcrRetry(OcrRetryMessage retry) {
        UUID sessionId = retry.getSessionId();
        MDC.put("sessionId", sessionId.toString());
        try {
            Optional<SessionPipelineState> stateOpt = stateRegistry.get(sessionId);
            if (stateOpt.isEmpty()) {
                log.warn("OCR retry received for a session with no in-memory pipeline state (instance restart?): "
                        + "sessionId={}, documentId={}. Marking document as failed.", sessionId, retry.getDocumentId());
                markTerminalFailure(retry.getDocumentId(), "ORPHANED_RETRY: estado de sessão perdido");
                return;
            }

            SessionPipelineState state = stateOpt.get();
            DocumentReadyMessage.DocumentInfo docInfo = state.document(retry.getDocumentId());
            if (docInfo == null) {
                log.warn("OCR retry for unknown documentId in session state: sessionId={}, documentId={}",
                        sessionId, retry.getDocumentId());
                return;
            }

            attemptDocument(state, docInfo, retry.getAttemptNumber());
            finalizeIfComplete(sessionId);
        } finally {
            MDC.remove("sessionId");
        }
    }

    private void attemptDocument(SessionPipelineState state, DocumentReadyMessage.DocumentInfo docInfo, int attemptNumber) {
        LegalDocument document = legalDocumentRepository.findById(docInfo.getDocumentId()).orElse(null);
        if (document == null) {
            log.error("legal_documents row not found for documentId in Q2 message (bug upstream): documentId={}",
                    docInfo.getDocumentId());
            state.resolveFailure(docInfo.getDocumentId(), "DOCUMENT_NOT_FOUND");
            return;
        }

        try {
            OcrResult ocrResult = ocrService.process(state.sessionId(), document);
            applyAnonymization(state, document, ocrResult);
        } catch (OcrProcessingException e) {
            int nextAttempt = attemptNumber + 1;
            List<Long> delays = mcpProperties.getOcr().getRetryDelaysSeconds();
            if (e.isRetryable() && nextAttempt <= delays.size()) {
                enqueueOcrRetry(state.sessionId(), docInfo, nextAttempt, delays.get(nextAttempt - 1));
            } else {
                if (e.isRetryable()) {
                    log.warn("OCR retries exhausted, giving up on document: sessionId={}, documentId={}, "
                                    + "attempts={}, reason={}",
                            state.sessionId(), document.getId(), attemptNumber, e.getMessage());
                    markTerminalFailure(document.getId(), "RETRY_EXHAUSTED: " + e.getMessage());
                    metrics.incrementOcrFailure("RETRY_EXHAUSTED");
                }
                state.resolveFailure(docInfo.getDocumentId(), e.getMessage());
            }
        } catch (McpException e) {
            log.warn("Document pipeline resolving failure: sessionId={}, documentId={}, reason={}",
                    state.sessionId(), docInfo.getDocumentId(), e.getMessage());
            state.resolveFailure(docInfo.getDocumentId(), e.getMessage());
        }
    }

    private void applyAnonymization(SessionPipelineState state, LegalDocument document, OcrResult ocrResult) {
        UUID sessionId = state.sessionId();
        MDC.put("documentId", document.getId().toString());
        MDC.put("tool", "anonymization");

        ProcessingStep step = processingStepService.startDocumentStep(sessionId, document.getId(), ProcessingStepName.ANONYMIZATION);
        AiToolCall call = aiToolCallService.start(sessionId, AiToolName.ANONYMIZATION, AiToolProvider.INTERNAL_REGEX,
                Map.of("documentId", document.getId(), "caracteresEntrada", ocrResult.caracteresExtraidos()));

        try {
            AnonymizationResult result = anonymizationService.anonymize(ocrResult.extractedText());

            document.setStatus(DocumentStatus.ANONYMIZED);
            legalDocumentRepository.save(document);

            processingStepService.complete(step.getId());
            aiToolCallService.completeSuccess(call.getId(), Map.of("contagemPorCategoria", result.countsByCategory()));
            result.countsByCategory().forEach(metrics::incrementAnonymizationPii);

            state.resolveSuccess(document.getId(), result.anonymizedText(), ocrResult.paginasProcessadas(),
                    result.countsByCategory());
        } catch (RuntimeException e) {
            log.error("Anonymization failed unexpectedly: documentId={}", document.getId(), e);
            processingStepService.fail(step.getId(), e.getMessage());
            aiToolCallService.completeFailure(call.getId(), e.getMessage());
            document.setStatus(DocumentStatus.PROCESSING_FAILED);
            document.setErrorMessage("ANONYMIZATION_FAILED");
            legalDocumentRepository.save(document);
            state.resolveFailure(document.getId(), "ANONYMIZATION_FAILED");
        } finally {
            MDC.remove("documentId");
            MDC.remove("tool");
        }
    }

    private void enqueueOcrRetry(UUID sessionId, DocumentReadyMessage.DocumentInfo docInfo, int attemptNumber, long delaySeconds) {
        OcrRetryMessage message = OcrRetryMessage.builder()
                .documentId(docInfo.getDocumentId())
                .sessionId(sessionId)
                .attachmentGroupId(docInfo.getAttachmentGroupId())
                .rawBucket(docInfo.getRawBucket())
                .rawObjectKey(docInfo.getRawObjectKey())
                .attemptNumber(attemptNumber)
                .build();
        queuePublisher.publish(mcpProperties.getOcr().getRetryQueueUrl(), message, delaySeconds);
        log.info("OCR retry enqueued: sessionId={}, documentId={}, attemptNumber={}, delaySeconds={}",
                sessionId, docInfo.getDocumentId(), attemptNumber, delaySeconds);
    }

    private void markTerminalFailure(UUID documentId, String errorMessage) {
        legalDocumentRepository.findById(documentId).ifPresent(d -> {
            d.setStatus(DocumentStatus.OCR_FAILED);
            d.setErrorMessage(errorMessage.length() > 1000 ? errorMessage.substring(0, 1000) : errorMessage);
            legalDocumentRepository.save(d);
        });
    }

    private void finalizeIfComplete(UUID sessionId) {
        Optional<SessionPipelineState> stateOpt = stateRegistry.get(sessionId);
        if (stateOpt.isEmpty()) {
            return;
        }
        SessionPipelineState state = stateOpt.get();
        if (!state.isComplete() || !state.tryStartFinalization()) {
            return;
        }

        try {
            List<McpResultCallbackPayload.ProcessedGroup> processedGroups = new ArrayList<>();
            Map<UUID, List<DocumentReadyMessage.DocumentInfo>> byGroup = state.allDocuments().stream()
                    .collect(Collectors.groupingBy(DocumentReadyMessage.DocumentInfo::getAttachmentGroupId));

            for (Map.Entry<UUID, List<DocumentReadyMessage.DocumentInfo>> entry : byGroup.entrySet()) {
                McpResultCallbackPayload.ProcessedGroup processedGroup = finalizeGroup(state, entry.getKey(), entry.getValue());
                if (processedGroup != null) {
                    processedGroups.add(processedGroup);
                }
            }

            List<McpResultCallbackPayload.FailedDocument> failedDocuments = state.failedDocuments().entrySet().stream()
                    .map(e -> McpResultCallbackPayload.FailedDocument.builder()
                            .documentId(e.getKey())
                            .errorMessage(e.getValue())
                            .build())
                    .toList();

            String status;
            if (processedGroups.isEmpty()) {
                status = "FAILED";
            } else if (failedDocuments.isEmpty()) {
                status = "COMPLETED";
            } else {
                status = "PARTIALLY_COMPLETED";
            }

            McpResultCallbackPayload payload = McpResultCallbackPayload.builder()
                    .sessionId(sessionId)
                    .correlationId(state.correlationId())
                    .status(status)
                    .processedGroups(processedGroups)
                    .failedDocuments(failedDocuments)
                    .completedAt(LocalDateTime.now())
                    .build();

            log.info("Session pipeline finalized: sessionId={}, status={}, processedGroups={}, failedDocuments={}",
                    sessionId, status, processedGroups.size(), failedDocuments.size());

            callbackClient.send(payload);
            metrics.recordPipelineLatency(state.elapsedMillis());
        } finally {
            stateRegistry.remove(sessionId);
        }
    }

    private McpResultCallbackPayload.ProcessedGroup finalizeGroup(SessionPipelineState state, UUID attachmentGroupId,
                                                                    List<DocumentReadyMessage.DocumentInfo> groupDocs) {
        List<GroupPart> successParts = new ArrayList<>();
        List<UUID> missingParts = new ArrayList<>();
        Map<String, Integer> aggregatedPiiCounts = new LinkedHashMap<>();

        for (DocumentReadyMessage.DocumentInfo doc : groupDocs) {
            if (state.isSuccessful(doc.getDocumentId())) {
                successParts.add(new GroupPart(doc.getDocumentId(), doc.getPartNumber(),
                        state.anonymizedText(doc.getDocumentId()), state.pages(doc.getDocumentId()),
                        doc.getNomeArquivoOriginal(), doc.getTipoDocumento()));
                state.piiCounts(doc.getDocumentId()).forEach((k, v) -> aggregatedPiiCounts.merge(k, v, Integer::sum));
            } else {
                missingParts.add(doc.getDocumentId());
            }
        }

        if (successParts.isEmpty()) {
            log.warn("Attachment group fully failed, no trusted object written: attachmentGroupId={}", attachmentGroupId);
            return null;
        }

        MDC.put("attachmentGroupId", attachmentGroupId.toString());
        try {
            GroupingResult groupingResult = attachmentGroupingService.group(state.sessionId(), attachmentGroupId, successParts);

            String finalText = groupingResult.concatenatedText();
            boolean resumido = groupingResult.routeToSummarization();
            if (resumido) {
                metrics.incrementSummarizationTriggered();
                SummarizationResult summary = summarizationService.summarize(state.sessionId(), attachmentGroupId, finalText);
                finalText = summary.summaryText();
            }

            List<GroupPart> orderedParts = successParts.stream()
                    .sorted(Comparator.comparingInt(GroupPart::partNumber))
                    .toList();

            TrustedDocumentPayload payload = TrustedDocumentPayload.builder()
                    .attachmentGroupId(attachmentGroupId)
                    .sessionId(state.sessionId())
                    .tipoDocumento(orderedParts.get(0).tipoDocumento())
                    .textoAnonimizado(finalText)
                    .resumido(resumido)
                    .piiRedactedCounts(aggregatedPiiCounts)
                    .partesOrigem(orderedParts.stream()
                            .map(p -> TrustedDocumentPayload.PartOrigem.builder()
                                    .documentId(p.documentId())
                                    .partNumber(p.partNumber())
                                    .nomeArquivoOriginal(p.nomeArquivoOriginal())
                                    .build())
                            .toList())
                    .partesFaltantes(missingParts.isEmpty() ? null : missingParts)
                    .processedAt(LocalDateTime.now())
                    .build();

            String objectKey = trustedDocumentWriter.write(state.lawFirmId(), state.sessionId(), attachmentGroupId, payload);

            markGroupTrusted(orderedParts, trustedDocumentWriter.bucket(), objectKey);

            return McpResultCallbackPayload.ProcessedGroup.builder()
                    .attachmentGroupId(attachmentGroupId)
                    .trustedBucket(trustedDocumentWriter.bucket())
                    .trustedObjectKey(objectKey)
                    .tipoDocumento(orderedParts.get(0).tipoDocumento())
                    .resumido(resumido)
                    .piiRedactedCategories(new ArrayList<>(aggregatedPiiCounts.keySet()))
                    .build();
        } finally {
            MDC.remove("attachmentGroupId");
        }
    }

    private void markGroupTrusted(List<GroupPart> parts, String trustedBucket, String objectKey) {
        for (GroupPart part : parts) {
            legalDocumentRepository.findById(part.documentId()).ifPresent(d -> {
                d.setStatus(DocumentStatus.TRUSTED);
                d.setProcessedBucket(trustedBucket);
                d.setProcessedObjectKey(objectKey);
                legalDocumentRepository.save(d);
            });
        }
    }

    private boolean isDuplicate(UUID sessionId) {
        return legalDocumentRepository.existsBySessionIdAndStatusNot(sessionId, DocumentStatus.QUEUED);
    }

    private void validateSchemaVersion(String schemaVersion) {
        if (!mcpProperties.getSchemaVersionSupported().equals(schemaVersion)) {
            throw new UnsupportedSchemaVersionException(schemaVersion);
        }
    }
}
