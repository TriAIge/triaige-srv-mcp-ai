package br.com.triaige.mcpai.domain.exception;

import org.springframework.http.HttpStatus;

/**
 * Relatório do modelo fora do schema esperado mesmo após 1 retry de
 * geração — falha de formatação, não repassada como está. A spec não lista um HTTP code
 * próprio para esse caso fora dos 4; tratado como 502, mesma família de
 * LLM_UNAVAILABLE (falha do lado do provedor de IA em produzir uma resposta utilizável).
 */
public class InvalidReportFormatException extends AnalysisException {

    public InvalidReportFormatException(String message) {
        super("INVALID_REPORT_FORMAT", HttpStatus.BAD_GATEWAY, message);
    }
}
