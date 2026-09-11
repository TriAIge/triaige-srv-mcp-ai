package br.com.triaige.mcpai.domain.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.util.UUID;

/**
 * Base das exceções do endpoint {@code POST /api/ai/v1/analyze}. Distinta de
 * {@link McpException} (que não carrega HTTP status, pois o pipeline T1-T5 não expunha API
 * REST de negócio) — introduz o primeiro endpoint REST de negócio deste serviço,
 * com corpo de erro literal definido na spec: {@code error}, e para
 * LLM_UNAVAILABLE/ANALYSIS_TIMEOUT também {@code status="FAILED"} e {@code sessionId}.
 */
@Getter
public abstract class AnalysisException extends RuntimeException {

    private final String code;
    private final HttpStatus status;
    private final boolean failedStatus;
    private UUID sessionId;

    protected AnalysisException(String code, HttpStatus status, String message) {
        this(code, status, message, false);
    }

    protected AnalysisException(String code, HttpStatus status, String message, boolean failedStatus) {
        super(message);
        this.code = code;
        this.status = status;
        this.failedStatus = failedStatus;
    }

    public AnalysisException withSessionId(UUID sessionId) {
        this.sessionId = sessionId;
        return this;
    }
}
