package br.com.triaige.mcpai.shared.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

/** Corpo literal da spec Fase 3, seção 4.4 — usado para as 4 falhas de análise (422/502/504/409). */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AnalysisErrorResponse {

    private String error;
    private String status;
    private UUID sessionId;
}
