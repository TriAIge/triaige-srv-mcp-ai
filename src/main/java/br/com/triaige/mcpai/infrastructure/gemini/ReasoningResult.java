package br.com.triaige.mcpai.infrastructure.gemini;

import java.util.UUID;

/** Resultado bruto do raciocínio Gemini, antes da validação/parse do relatório estruturado. */
public record ReasoningResult(
        String reportJsonText,
        int toolCallsUsed,
        UUID jurisprudenceCallId,
        Integer tokensIn,
        Integer tokensOut,
        String finishReason) {
}
