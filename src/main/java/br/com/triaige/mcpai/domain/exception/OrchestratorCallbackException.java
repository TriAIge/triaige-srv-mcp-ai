package br.com.triaige.mcpai.domain.exception;

/** Todas as tentativas de callback síncrono ao Orchestrator se esgotaram. */
public class OrchestratorCallbackException extends McpException {

    public OrchestratorCallbackException(String message, Throwable cause) {
        super("ORCHESTRATOR_CALLBACK_FAILED", message, cause);
    }
}
