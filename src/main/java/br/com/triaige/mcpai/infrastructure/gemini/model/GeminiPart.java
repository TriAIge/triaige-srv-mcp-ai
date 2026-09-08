package br.com.triaige.mcpai.infrastructure.gemini.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/** Uma "parte" de conteúdo Gemini: texto, chamada de função ou resultado de função (mutuamente exclusivos). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GeminiPart {
    private String text;
    private FunctionCall functionCall;
    private FunctionResponse functionResponse;
    /**
     * Exigido pela API atual em partes de functionCall de modelos com "thinking" (validado
     * nesta sessão contra a API real: "Function call is missing a thought_signature...").
     * Só precisa ser capturado na desserialização da resposta do Gemini e reenviado tal
     * qual quando o turno do modelo é ecoado de volta no histórico da conversa — nunca
     * gerado por este serviço.
     */
    private String thoughtSignature;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FunctionCall {
        private String name;
        private Map<String, Object> args;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FunctionResponse {
        private String name;
        private Map<String, Object> response;
    }
}
