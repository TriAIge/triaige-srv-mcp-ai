package br.com.triaige.mcpai.domain.exception;

import org.springframework.http.HttpStatus;

/**
 * Fase 4, spec seção 11: {@code X-Internal-Token} ausente ou inválido em
 * {@code POST /api/ai/v1/analyze}. Nunca chega ao {@code GlobalExceptionHandler} — é lançada
 * e tratada dentro de {@code InternalTokenAuthFilter}, que roda antes do
 * {@code DispatcherServlet}; existe só para manter o mesmo vocabulário de exceção do
 * restante do serviço (code/status).
 */
public class InternalTokenUnauthorizedException extends AnalysisException {

    public InternalTokenUnauthorizedException() {
        super("UNAUTHORIZED", HttpStatus.UNAUTHORIZED, "X-Internal-Token inválido ou ausente");
    }
}
