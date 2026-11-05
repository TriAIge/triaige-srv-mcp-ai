package br.com.triaige.mcpai.infrastructure.gemini.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeminiTool {
    private List<FunctionDeclaration> functionDeclarations;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FunctionDeclaration {
        private String name;
        private String description;
        /** JSON Schema (tipo OBJECT) dos parâmetros. */
        private Map<String, Object> parameters;
    }
}
