package br.com.triaige.mcpai.domain.exception;

import org.springframework.http.HttpStatus;

/** Timeout total da análise (ANALYSIS_TIMEOUT_MS) excedido. */
public class AnalysisTimeoutException extends AnalysisException {

    public AnalysisTimeoutException() {
        super("ANALYSIS_TIMEOUT", HttpStatus.GATEWAY_TIMEOUT,
                "Timeout total da análise excedido", true);
    }
}
