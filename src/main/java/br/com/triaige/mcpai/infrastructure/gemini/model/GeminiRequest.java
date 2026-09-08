package br.com.triaige.mcpai.infrastructure.gemini.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/** Body de POST /v1beta/models/{model}:generateContent (Gemini Generative Language API). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GeminiRequest {
    private List<GeminiContent> contents;
    private GeminiContent systemInstruction;
    private List<GeminiTool> tools;
    private Map<String, Object> generationConfig;
}
