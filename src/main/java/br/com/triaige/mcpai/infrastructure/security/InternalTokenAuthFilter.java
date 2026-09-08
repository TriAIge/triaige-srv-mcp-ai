package br.com.triaige.mcpai.infrastructure.security;

import br.com.triaige.mcpai.domain.exception.InternalTokenUnauthorizedException;
import br.com.triaige.mcpai.infrastructure.config.AiProperties;
import br.com.triaige.mcpai.shared.error.AnalysisErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Fecha o gap de segurança da Fase 3 (spec Fase 4, seção 11): {@code POST /api/ai/v1/analyze}
 * não validava nenhuma credencial. Mesmo desenho do gêmeo no Orchestrator
 * ({@code InternalTokenAuthFilter}, direção mcp-ai→Orchestrator) — não há Spring Security
 * neste serviço, então o bean é auto-registrado pelo Boot como filtro de servlet; o
 * path-scoping é feito manualmente via {@link #shouldNotFilter}, já que este é o único
 * endpoint REST de negócio do serviço.
 */
@Component
@RequiredArgsConstructor
public class InternalTokenAuthFilter extends OncePerRequestFilter {

    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().endsWith("/analyze");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        String token = request.getHeader("X-Internal-Token");
        String expected = aiProperties.getInternal().getAnalyzeToken();

        if (token == null || expected == null || !constantTimeEquals(token, expected)) {
            writeError(response);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    private void writeError(HttpServletResponse response) throws IOException {
        InternalTokenUnauthorizedException ex = new InternalTokenUnauthorizedException();
        AnalysisErrorResponse body = AnalysisErrorResponse.builder()
                .error(ex.getCode())
                .build();

        response.setStatus(ex.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
