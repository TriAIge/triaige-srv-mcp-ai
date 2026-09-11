package br.com.triaige.mcpai.domain.exception;

/** T1 — formato não suportado pelo Textract. Falha imediata, sem retry. */
public class UnsupportedDocumentFormatException extends McpException {

    public UnsupportedDocumentFormatException(String contentType) {
        super("UNSUPPORTED_FORMAT", "UNSUPPORTED_FORMAT: " + contentType);
    }
}
