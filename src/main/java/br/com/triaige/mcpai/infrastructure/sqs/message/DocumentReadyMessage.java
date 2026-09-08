package br.com.triaige.mcpai.infrastructure.sqs.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Contrato Q2 (triaige-docs-preprocessing) — spec da Fase 1, seção 7.2. Deve espelhar
 * exatamente {@code DocumentReadyMessage} do triaige-srv-orchestrator (produtor desta
 * mensagem) campo a campo para que a (de)serialização Jackson seja compatível.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentReadyMessage {

    @Builder.Default
    private String schemaVersion = "1.0";

    private UUID correlationId;
    private UUID sessionId;
    private String protocolo;
    private UUID lawFirmId;
    private LegalCaseInfo legalCase;
    private List<DocumentInfo> documents;
    private LocalDateTime publishedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LegalCaseInfo {
        private UUID id;
        private String titulo;
        private String areaJuridica;
        private String tipoCaso;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DocumentInfo {
        private UUID documentId;
        private UUID attachmentGroupId;
        private Integer partNumber;
        private String nomeArquivoOriginal;
        private String tipoDocumento;
        private String contentType;
        private Long tamanhoBytes;
        private String rawBucket;
        private String rawObjectKey;
    }
}
