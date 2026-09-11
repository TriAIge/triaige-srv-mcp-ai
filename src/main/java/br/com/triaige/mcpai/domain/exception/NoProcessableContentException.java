package br.com.triaige.mcpai.domain.exception;

import org.springframework.http.HttpStatus;

/** {@code processedGroups} vazio — não chama o Gemini. */
public class NoProcessableContentException extends AnalysisException {

    public NoProcessableContentException() {
        super("NO_PROCESSABLE_CONTENT", HttpStatus.UNPROCESSABLE_ENTITY,
                "processedGroups vazio — nada para analisar");
    }
}
