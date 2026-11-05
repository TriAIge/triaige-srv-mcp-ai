package br.com.triaige.mcpai.infrastructure.gemini;

import br.com.triaige.mcpai.api.dto.request.AnalysisRequest;
import br.com.triaige.mcpai.infrastructure.config.AiProperties;
import br.com.triaige.mcpai.infrastructure.gemini.model.GeminiContent;
import br.com.triaige.mcpai.infrastructure.gemini.model.GeminiPart;
import br.com.triaige.mcpai.infrastructure.gemini.model.GeminiRequest;
import br.com.triaige.mcpai.infrastructure.gemini.model.GeminiResponse;
import br.com.triaige.mcpai.infrastructure.gemini.model.GeminiTool;
import br.com.triaige.mcpai.infrastructure.jurisprudence.JurisprudenceQueryTool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Laço de function calling do Gemini com a tool {@code jurisprudence_query}. Ao atingir {@code ai.analysis.max-tool-calls} chamadas, o próximo
 * request ao Gemini é enviado SEM a declaração da tool — o modelo fica impossibilitado de
 * chamá-la de novo (mais robusto que confiar só na instrução do prompt de sistema) e é
 * forçado a concluir com o que já tem.
 *
 * <p>A tool é chamada EM PROCESSO via {@link JurisprudenceQueryTool#query} — não por HTTP/MCP
 * — já que este é o mesmo serviço que a expõe via protocolo MCP (T5). Isso também
 * resolve de forma direta o id de auditoria: {@code JurisprudenceQueryTool.query} já devolve
 * o {@code ai_tool_calls.id} do registro que ele mesmo grava, sem precisar de um registro
 * paralelo.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GeminiReasoningService {

    private static final Logger DEBUG_LOG = LoggerFactory.getLogger("br.com.triaige.mcpai.debug");

    private final GeminiClient geminiClient;
    private final JurisprudenceQueryTool jurisprudenceQueryTool;
    private final AiProperties aiProperties;
    private final PromptProvider promptProvider;

    public ReasoningResult reason(UUID sessionId, AnalysisRequest.LegalCaseDto legalCase, String contextoDocumentos) {
        String systemPrompt = promptProvider.load();
        GeminiContent systemInstruction = GeminiContent.builder()
                .parts(List.of(GeminiPart.builder().text(systemPrompt).build()))
                .build();

        List<GeminiContent> contents = new ArrayList<>();
        contents.add(GeminiContent.builder()
                .role("user")
                .parts(List.of(GeminiPart.builder().text(buildUserMessage(legalCase, contextoDocumentos)).build()))
                .build());

        GeminiTool jurisprudenceTool = buildJurisprudenceTool();
        int maxToolCalls = aiProperties.getAnalysis().getMaxToolCalls();
        int toolCallsUsed = 0;
        UUID lastJurisprudenceCallId = null;
        int tokensIn = 0;
        int tokensOut = 0;
        String finishReason = null;
        // Fallback de resiliência: se o provedor de jurisprudência falhar, a tool é retirada
        // das próximas rodadas em vez de deixar o modelo insistir em chamá-la — evita
        // desperdiçar o orçamento de ai.analysis.timeout-ms em rodadas Gemini extras contra um
        // provedor indisponível, e garante que a análise sempre conclua com o que já tem.
        boolean jurisprudenceAvailable = true;

        while (true) {
            boolean toolsAvailable = toolCallsUsed < maxToolCalls && jurisprudenceAvailable;
            GeminiRequest request = GeminiRequest.builder()
                    .systemInstruction(systemInstruction)
                    .contents(contents)
                    .tools(toolsAvailable ? List.of(jurisprudenceTool) : null)
                    // A API do Gemini rejeita combinar function calling com
                    // responseMimeType=application/json na mesma chamada ("Function calling
                    // with a response mime type: 'application/json' is unsupported") — o JSON
                    // mode só pode ser ligado na rodada final, sem tools anexado.
                    .generationConfig(toolsAvailable ? null : Map.of("responseMimeType", "application/json"))
                    .build();

            DEBUG_LOG.debug("Gemini request (sessionId={}): {}", sessionId, request);
            GeminiResponse response = geminiClient.generateContent(request);
            DEBUG_LOG.debug("Gemini response (sessionId={}): {}", sessionId, response);

            if (response.getUsageMetadata() != null) {
                tokensIn += orZero(response.getUsageMetadata().getPromptTokenCount());
                tokensOut += orZero(response.getUsageMetadata().getCandidatesTokenCount());
            }

            GeminiResponse.Candidate candidate = response.getCandidates().get(0);
            finishReason = candidate.getFinishReason();
            GeminiContent modelContent = candidate.getContent();
            contents.add(modelContent);

            Optional<GeminiPart> functionCallPart = modelContent.getParts().stream()
                    .filter(part -> part.getFunctionCall() != null)
                    .findFirst();

            if (functionCallPart.isEmpty()) {
                String finalText = extractText(modelContent);
                return new ReasoningResult(finalText, toolCallsUsed, lastJurisprudenceCallId, tokensIn, tokensOut, finishReason);
            }

            toolCallsUsed++;
            GeminiPart.FunctionCall functionCall = functionCallPart.get().getFunctionCall();
            String teseJuridica = String.valueOf(functionCall.getArgs().get("teseJuridica"));
            String areaJuridica = String.valueOf(functionCall.getArgs().get("areaJuridica"));

            Map<String, Object> functionResponsePayload;
            try {
                JurisprudenceQueryTool.JurisprudenceQueryOutcome outcome =
                        jurisprudenceQueryTool.query(teseJuridica, areaJuridica, sessionId);
                lastJurisprudenceCallId = outcome.aiToolCallId();
                functionResponsePayload = Map.of("resultados", outcome.result().getResultados());
            } catch (RuntimeException e) {
                log.warn("jurisprudence_query falhou, tool desabilitada para o restante desta análise "
                        + "(sessionId={}): {}", sessionId, e.getMessage());
                jurisprudenceAvailable = false;
                functionResponsePayload = Map.of("erro", "provedor de jurisprudência indisponível no momento, "
                        + "prossiga sem esta consulta");
            }

            contents.add(GeminiContent.builder()
                    // A API rejeita role="function" ("Role 'function' is not supported"),
                    // validado nesta sessão contra a API real — a Generative Language API
                    // atual espera role="user" para o turno de functionResponse.
                    .role("user")
                    .parts(List.of(GeminiPart.builder()
                            .functionResponse(GeminiPart.FunctionResponse.builder()
                                    .name("jurisprudence_query")
                                    .response(functionResponsePayload)
                                    .build())
                            .build()))
                    .build());
        }
    }

    private String buildUserMessage(AnalysisRequest.LegalCaseDto legalCase, String contextoDocumentos) {
        return """
                Caso jurídico:
                - Título: %s
                - Área jurídica: %s
                - Tipo de caso: %s

                Evidências (já anonimizadas e agrupadas por anexo):
                %s
                """.formatted(legalCase.getTitulo(), legalCase.getAreaJuridica(), legalCase.getTipoCaso(), contextoDocumentos);
    }

    private GeminiTool buildJurisprudenceTool() {
        Map<String, Object> parameters = Map.of(
                "type", "OBJECT",
                "properties", Map.of(
                        "teseJuridica", Map.of("type", "STRING", "description", "Resumo da tese jurídica ou palavras-chave"),
                        "areaJuridica", Map.of("type", "STRING", "description", "Área jurídica do caso")),
                "required", List.of("teseJuridica", "areaJuridica"));

        return GeminiTool.builder()
                .functionDeclarations(List.of(GeminiTool.FunctionDeclaration.builder()
                        .name("jurisprudence_query")
                        .description("Consulta jurisprudência relacionada a uma tese jurídica e área do direito.")
                        .parameters(parameters)
                        .build()))
                .build();
    }

    private String extractText(GeminiContent content) {
        return content.getParts().stream()
                .map(GeminiPart::getText)
                .filter(text -> text != null && !text.isBlank())
                .collect(Collectors.joining("\n"));
    }

    private int orZero(Integer value) {
        return value == null ? 0 : value;
    }
}
