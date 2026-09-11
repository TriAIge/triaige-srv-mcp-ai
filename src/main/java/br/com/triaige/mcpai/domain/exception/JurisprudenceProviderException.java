package br.com.triaige.mcpai.domain.exception;

/** T5 — falha ao consultar mockapi.io após o retry único. */
public class JurisprudenceProviderException extends McpException {

    public JurisprudenceProviderException(String message, Throwable cause) {
        super("JURISPRUDENCE_PROVIDER_FAILED", message, cause);
    }
}
