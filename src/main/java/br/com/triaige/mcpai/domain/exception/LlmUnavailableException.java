package br.com.triaige.mcpai.domain.exception;

import org.springframework.http.HttpStatus;

/** Gemini indisponível após esgotar retries, ou circuito aberto. */
public class LlmUnavailableException extends AnalysisException {

    public LlmUnavailableException(String message) {
        super("LLM_UNAVAILABLE", HttpStatus.BAD_GATEWAY, message, true);
    }
}
