package br.com.triaige.mcpai.domain.exception;

import org.springframework.http.HttpStatus;

/** Spec Fase 3, seção 4.4/5.4: timeout total da análise (ANALYSIS_TIMEOUT_MS) excedido. */
public class AnalysisTimeoutException extends AnalysisException {

    public AnalysisTimeoutException() {
        super("ANALYSIS_TIMEOUT", HttpStatus.GATEWAY_TIMEOUT,
                "Timeout total da análise excedido", true);
    }
}
