package br.com.triaige.mcpai.infrastructure.jurisprudence;

import br.com.triaige.mcpai.application.service.AiToolCallService;
import br.com.triaige.mcpai.domain.entity.AiToolCall;
import br.com.triaige.mcpai.domain.enums.AiToolName;
import br.com.triaige.mcpai.domain.enums.AiToolProvider;
import br.com.triaige.mcpai.shared.metrics.McpMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * T5 — tool MCP {@code jurisprudence_query} (spec seção 7), exposta via protocolo MCP
 * (porta do servidor configurada em {@code server.port}) para consumo sob demanda por um
 * serviço de raciocínio de IA durante a triagem (fora do escopo desta fase).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JurisprudenceQueryTool {

    private final JurisprudenceCacheService cacheService;
    private final MockApiJurisprudenceClient providerClient;
    private final AiToolCallService aiToolCallService;
    private final McpMetrics metrics;

    @Tool(name = "jurisprudence_query",
            description = "Consulta jurisprudência relacionada a uma tese jurídica e área do direito, com cache "
                    + "de 7 dias. Use durante o raciocínio para embasar a triagem de um caso jurídico com "
                    + "precedentes. ATENÇÃO: a fonte atual é uma API mock — resultados não são jurisprudência "
                    + "real e não devem ser usados para decisão jurídica de fato.")
    public JurisprudenceQueryResult jurisprudenceQuery(
            @ToolParam(description = "Resumo da tese jurídica ou palavras-chave, obrigatório") String teseJuridica,
            @ToolParam(description = "Área jurídica do caso, obrigatório") String areaJuridica,
            @ToolParam(description = "UUID da sessão de triagem, usado apenas para auditoria em ai_tool_calls") UUID sessionId) {
        return query(teseJuridica, areaJuridica, sessionId).result();
    }

    /**
     * Mesma lógica de {@link #jurisprudenceQuery}, mas também devolve o id do registro
     * {@code ai_tool_calls} gravado para esta chamada — usado pelo raciocínio da Fase 3
     * ({@code GeminiReasoningService}) para preencher {@code jurisprudenceCallId} na
     * resposta de {@code POST /api/ai/v1/analyze} (spec Fase 3, seção 4.3). O método
     * exposto ao protocolo MCP ({@link #jurisprudenceQuery}) não muda de contrato — continua
     * devolvendo só {@link JurisprudenceQueryResult}, para não afetar consumidores externos
     * da tool.
     */
    public JurisprudenceQueryOutcome query(String teseJuridica, String areaJuridica, UUID sessionId) {
        MDC.put("sessionId", sessionId.toString());
        MDC.put("tool", "jurisprudence_query");

        AiToolCall call = aiToolCallService.start(sessionId, AiToolName.JURISPRUDENCE_QUERY, AiToolProvider.MOCKAPI_IO,
                Map.of("teseJuridica", teseJuridica, "areaJuridica", areaJuridica));

        try {
            String queryHash = cacheService.hash(teseJuridica, areaJuridica);

            var cached = cacheService.get(queryHash);
            if (cached.isPresent()) {
                aiToolCallService.completeSuccess(call.getId(), Map.of(
                        "resultadosCount", cached.get().size(), "cacheHit", true));
                metrics.recordJurisprudenceCacheHit(true);
                return new JurisprudenceQueryOutcome(
                        JurisprudenceQueryResult.builder().resultados(cached.get()).cacheHit(true).build(),
                        call.getId());
            }

            List<JurisprudenceItem> items = providerClient.query(teseJuridica, areaJuridica);
            cacheService.save(queryHash, teseJuridica, areaJuridica, items);

            aiToolCallService.completeSuccess(call.getId(), Map.of(
                    "resultadosCount", items.size(), "cacheHit", false));
            metrics.recordJurisprudenceCacheHit(false);
            return new JurisprudenceQueryOutcome(
                    JurisprudenceQueryResult.builder().resultados(items).cacheHit(false).build(),
                    call.getId());
        } catch (RuntimeException e) {
            aiToolCallService.completeFailure(call.getId(), e.getMessage());
            throw e;
        } finally {
            MDC.remove("sessionId");
            MDC.remove("tool");
        }
    }

    public record JurisprudenceQueryOutcome(JurisprudenceQueryResult result, UUID aiToolCallId) {
    }
}
