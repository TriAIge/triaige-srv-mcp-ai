package br.com.triaige.mcpai.domain.exception;

import org.springframework.http.HttpStatus;

/** Spec Fase 3, seção 4.4: {@code processedGroups} vazio — não chama o Gemini. */
public class NoProcessableContentException extends AnalysisException {

    public NoProcessableContentException() {
        super("NO_PROCESSABLE_CONTENT", HttpStatus.UNPROCESSABLE_ENTITY,
                "processedGroups vazio — nada para analisar");
    }
}
