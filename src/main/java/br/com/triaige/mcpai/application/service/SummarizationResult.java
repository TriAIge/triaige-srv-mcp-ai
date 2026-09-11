package br.com.triaige.mcpai.application.service;

/** Saída de T4. */
public record SummarizationResult(String summaryText, int originalCharCount, int summaryCharCount) {

    public double compressionRate() {
        return originalCharCount == 0 ? 0.0 : 1.0 - ((double) summaryCharCount / originalCharCount);
    }
}
