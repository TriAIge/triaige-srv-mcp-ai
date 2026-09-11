package br.com.triaige.mcpai.application.service;

import br.com.triaige.mcpai.infrastructure.sqs.message.DocumentReadyMessage;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Estado em memória de uma sessão em processamento, vivo entre a leitura de Q2 e a
 * resolução (possivelmente assíncrona, via fila de retry de OCR) de todos os seus
 * documentos. Necessário porque T3 (agrupamento) só pode rodar quando TODAS as partes de
 * um grupo estiverem resolvidas, e uma parte pode demorar minutos a mais que as outras
 * (retry de OCR com delay). Suposição assumida: uma única instância do serviço processa a
 * sessão do início ao fim (mesma convenção do Q1 do triaige-srv-orchestrator) — em caso de
 * restart do processo com retries pendentes, o estado em memória é perdido e a sessão fica
 * incompleta; mitigação é fora do escopo (spec não define reconciliação).
 */
public class SessionPipelineState {

    private final UUID sessionId;
    private final UUID lawFirmId;
    private final String protocolo;
    private final UUID correlationId;
    private final DocumentReadyMessage.LegalCaseInfo legalCase;
    private final Map<UUID, DocumentReadyMessage.DocumentInfo> documentsById;
    private final Map<UUID, String> anonymizedTextByDocument = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> pagesByDocument = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Integer>> piiCountsByDocument = new ConcurrentHashMap<>();
    private final Map<UUID, String> failedDocuments = new ConcurrentHashMap<>();
    private final AtomicInteger pendingCount;
    private final AtomicBoolean finalized = new AtomicBoolean(false);
    private final long startedAtMillis = System.currentTimeMillis();

    public SessionPipelineState(UUID sessionId, UUID lawFirmId, String protocolo, UUID correlationId,
                                 DocumentReadyMessage.LegalCaseInfo legalCase,
                                 List<DocumentReadyMessage.DocumentInfo> documents) {
        this.sessionId = sessionId;
        this.lawFirmId = lawFirmId;
        this.protocolo = protocolo;
        this.correlationId = correlationId;
        this.legalCase = legalCase;
        this.documentsById = new ConcurrentHashMap<>();
        documents.forEach(d -> documentsById.put(d.getDocumentId(), d));
        this.pendingCount = new AtomicInteger(documents.size());
    }

    public void resolveSuccess(UUID documentId, String anonymizedText, int pages, Map<String, Integer> piiCounts) {
        anonymizedTextByDocument.put(documentId, anonymizedText);
        pagesByDocument.put(documentId, pages);
        piiCountsByDocument.put(documentId, piiCounts);
        pendingCount.decrementAndGet();
    }

    public void resolveFailure(UUID documentId, String errorMessage) {
        failedDocuments.put(documentId, errorMessage);
        pendingCount.decrementAndGet();
    }

    public boolean isComplete() {
        return pendingCount.get() <= 0;
    }

    /** Garante que a finalização (T3/T4/S3/callback) rode exatamente uma vez por sessão. */
    public boolean tryStartFinalization() {
        return finalized.compareAndSet(false, true);
    }

    public UUID sessionId() {
        return sessionId;
    }

    public UUID lawFirmId() {
        return lawFirmId;
    }

    public String protocolo() {
        return protocolo;
    }

    public UUID correlationId() {
        return correlationId;
    }

    public DocumentReadyMessage.LegalCaseInfo legalCase() {
        return legalCase;
    }

    public DocumentReadyMessage.DocumentInfo document(UUID documentId) {
        return documentsById.get(documentId);
    }

    public List<DocumentReadyMessage.DocumentInfo> allDocuments() {
        return List.copyOf(documentsById.values());
    }

    public Map<UUID, String> failedDocuments() {
        return Map.copyOf(failedDocuments);
    }

    public boolean isSuccessful(UUID documentId) {
        return anonymizedTextByDocument.containsKey(documentId);
    }

    public String anonymizedText(UUID documentId) {
        return anonymizedTextByDocument.get(documentId);
    }

    public int pages(UUID documentId) {
        return pagesByDocument.getOrDefault(documentId, 0);
    }

    public Map<String, Integer> piiCounts(UUID documentId) {
        return piiCountsByDocument.getOrDefault(documentId, Map.of());
    }

    public long elapsedMillis() {
        return System.currentTimeMillis() - startedAtMillis;
    }
}
