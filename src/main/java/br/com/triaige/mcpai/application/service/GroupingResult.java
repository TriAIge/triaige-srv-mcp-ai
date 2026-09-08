package br.com.triaige.mcpai.application.service;

/** Saída de T3 (spec seção 5.4/5.5). */
public record GroupingResult(String concatenatedText, long totalChars, long totalPages, boolean routeToSummarization) {
}
