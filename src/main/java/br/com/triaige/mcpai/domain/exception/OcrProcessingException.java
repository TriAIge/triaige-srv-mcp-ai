package br.com.triaige.mcpai.domain.exception;

import lombok.Getter;

/**
 * Falha durante T1 (spec seção 5.2). {@code retryable=true} — timeout/5xx/throttling do
 * Textract — vai para a fila triaige-mcp-ocr-retry; {@code retryable=false} — documento
 * corrompido, formato inválido, arquivo vazio — direto para OCR_FAILED.
 */
@Getter
public class OcrProcessingException extends McpException {

    private final boolean retryable;

    public OcrProcessingException(String code, String message, boolean retryable) {
        super(code, message);
        this.retryable = retryable;
    }

    public OcrProcessingException(String code, String message, boolean retryable, Throwable cause) {
        super(code, message, cause);
        this.retryable = retryable;
    }
}
