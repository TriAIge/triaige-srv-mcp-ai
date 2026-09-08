package br.com.triaige.mcpai.infrastructure.s3;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Objeto gravado em s3://{trustedBucket}/trusted/{lawFirmId}/{sessionId}/{attachmentGroupId}.json (spec seção 5.7). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrustedDocumentPayload {

    @Builder.Default
    private String schemaVersion = "1.0";

    private UUID attachmentGroupId;
    private UUID sessionId;
    private String tipoDocumento;
    private String textoAnonimizado;
    private boolean resumido;
    private Map<String, Integer> piiRedactedCounts;
    private List<PartOrigem> partesOrigem;
    /** Preenchido apenas quando ao menos uma parte do grupo falhou em T1/T2 mas outras tiveram sucesso (spec seção 5.8). */
    private List<UUID> partesFaltantes;
    private LocalDateTime processedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PartOrigem {
        private UUID documentId;
        private Integer partNumber;
        private String nomeArquivoOriginal;
    }
}
