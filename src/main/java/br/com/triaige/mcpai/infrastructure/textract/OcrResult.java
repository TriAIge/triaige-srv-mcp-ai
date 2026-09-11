package br.com.triaige.mcpai.infrastructure.textract;

/** Saída de T1 — texto extraído fica só em memória, nunca persistido aqui. */
public record OcrResult(String extractedText, int paginasProcessadas, int caracteresExtraidos) {
}
