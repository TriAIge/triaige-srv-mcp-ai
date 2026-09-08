package br.com.triaige.mcpai.infrastructure.gemini.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeminiContent {
    /** "user" | "model" | "function" */
    private String role;
    private List<GeminiPart> parts;
}
