package br.com.triaige.mcpai.api.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/** Spec Fase 3, seção 4.1 — processedGroups/failedDocuments espelham o callback do MCP (Fase 2, seção 6), repassados sem transformação pelo Orchestrator. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisRequest {

    @Builder.Default
    private String schemaVersion = "1.0";

    @NotNull(message = "sessionId é obrigatório")
    private UUID sessionId;

    @NotNull(message = "correlationId é obrigatório")
    private UUID correlationId;

    @NotBlank(message = "protocolo é obrigatório")
    private String protocolo;

    @NotNull(message = "legalCase é obrigatório")
    @Valid
    private LegalCaseDto legalCase;

    private List<ProcessedGroupDto> processedGroups;

    private List<FailedDocumentDto> failedDocuments;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LegalCaseDto {
        @NotNull(message = "legalCase.id é obrigatório")
        private UUID id;
        @NotBlank(message = "legalCase.titulo é obrigatório")
        private String titulo;
        @NotBlank(message = "legalCase.areaJuridica é obrigatório")
        private String areaJuridica;
        @NotBlank(message = "legalCase.tipoCaso é obrigatório")
        private String tipoCaso;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProcessedGroupDto {
        private UUID attachmentGroupId;
        private String trustedBucket;
        private String trustedObjectKey;
        private String tipoDocumento;
        private boolean resumido;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FailedDocumentDto {
        private UUID documentId;
        private String errorMessage;
    }
}
