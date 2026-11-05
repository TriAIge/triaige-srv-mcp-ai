package br.com.triaige.mcpai.infrastructure.callback;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Payload do callback síncrono ao Orchestrator. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class McpResultCallbackPayload {

    @Builder.Default
    private String schemaVersion = "1.0";

    private UUID sessionId;
    private UUID correlationId;
    /** COMPLETED | PARTIALLY_COMPLETED | FAILED */
    private String status;
    private List<ProcessedGroup> processedGroups;
    private List<FailedDocument> failedDocuments;
    private LocalDateTime completedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProcessedGroup {
        private UUID attachmentGroupId;
        private String trustedBucket;
        private String trustedObjectKey;
        private String tipoDocumento;
        private boolean resumido;
        private List<String> piiRedactedCategories;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FailedDocument {
        private UUID documentId;
        private String errorMessage;
    }
}
