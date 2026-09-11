package br.com.triaige.mcpai.api.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/** Resposta 200 de sucesso do {@code POST /api/ai/v1/analyze}. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisResponse {

    @Builder.Default
    private String schemaVersion = "1.0";

    private UUID sessionId;
    private UUID correlationId;
    /** COMPLETED (única possibilidade de sucesso — falhas viram resposta de erro). */
    private String status;
    private RelatorioEstruturado relatorioEstruturado;
    private UUID geminiToolCallId;
    private UUID jurisprudenceCallId;
    private LocalDateTime geradoEm;
}
