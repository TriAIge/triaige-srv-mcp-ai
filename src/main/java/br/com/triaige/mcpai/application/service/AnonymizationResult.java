package br.com.triaige.mcpai.application.service;

import java.util.Map;

/** Saída de T2 (spec seção 5.3): texto rotulado + contagem por categoria, nunca os valores originais. */
public record AnonymizationResult(String anonymizedText, Map<String, Integer> countsByCategory) {
}
