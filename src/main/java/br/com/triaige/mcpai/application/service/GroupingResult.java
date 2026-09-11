package br.com.triaige.mcpai.application.service;

/** Saída de T3. */
public record GroupingResult(String concatenatedText, long totalChars, long totalPages, boolean routeToSummarization) {
}
