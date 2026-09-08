package br.com.triaige.mcpai.domain.exception;

import lombok.Getter;

/** Base das exceções de domínio do pipeline. Não carrega HTTP status: o MCP não expõe API REST de negócio. */
@Getter
public abstract class McpException extends RuntimeException {

    private final String code;

    protected McpException(String code, String message) {
        super(message);
        this.code = code;
    }

    protected McpException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }
}
