package br.com.triaige.mcpai.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/** Propriedades da Fase 3 (spec seções 2, 4, 5) — tudo configurável, nada hardcoded. */
@Data
@ConfigurationProperties(prefix = "ai")
public class AiProperties {

    private Idempotency idempotency = new Idempotency();
    private Analysis analysis = new Analysis();
    private Gemini gemini = new Gemini();
    private Internal internal = new Internal();

    @Data
    public static class Idempotency {
        private int ttlHours = 24;
    }

    /**
     * Fase 4, spec seção 2.5: token para autenticar {@code POST /api/ai/v1/analyze} na
     * direção Orchestrator→mcp-ai. Nome de property deliberadamente distinto de
     * {@code mcp.callback.internal-token} (direção oposta, mcp-ai→Orchestrator) para
     * permitir rotação independente.
     */
    @Data
    public static class Internal {
        private String analyzeToken = "dev-local-analyze-token";
    }

    @Data
    public static class Analysis {
        private long timeoutMs = 120000;
        private int maxToolCalls = 3;
    }

    @Data
    public static class Gemini {
        private String apiKey;
        private String model = "gemini-2.5-flash";
        private String fallbackModel = "gemini-2.5-flash-lite";
        private String endpoint = "https://generativelanguage.googleapis.com/v1beta";
        private int timeoutMs = 60000;
        private List<Long> retryDelaysMs = List.of(2000L, 8000L, 20000L);
        private int circuitBreakerFailureThreshold = 5;
        private long circuitBreakerOpenMs = 120000;
    }

}
