package br.com.triaige.mcpai.infrastructure.security;

import br.com.triaige.mcpai.infrastructure.config.AiProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InternalTokenAuthFilterTest {

    private static final String VALID_TOKEN = "dev-local-analyze-token";

    private AiProperties aiProperties;
    private InternalTokenAuthFilter filter;

    @BeforeEach
    void setUp() {
        aiProperties = new AiProperties();
        aiProperties.getInternal().setAnalyzeToken(VALID_TOKEN);
        filter = new InternalTokenAuthFilter(aiProperties, new ObjectMapper());
    }

    @Test
    void shouldNotFilter_returnsTrueForPathsOtherThanAnalyze() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/orchestrator/v1/sessions/x/mcp-result");

        assertThat(filter.shouldNotFilter(request)).isTrue();
    }

    @Test
    void shouldNotFilter_returnsFalseForAnalyzeEndpoint() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/ai/v1/analyze");

        assertThat(filter.shouldNotFilter(request)).isFalse();
    }

    @Test
    void missingToken_rejectsWith401() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        StringWriter body = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(body));
        when(request.getHeader("X-Internal-Token")).thenReturn(null);

        filter.doFilterInternal(request, response, chain);

        verify(response).setStatus(401);
        verify(chain, never()).doFilter(request, response);
        assertThat(body.toString()).contains("UNAUTHORIZED");
    }

    @Test
    void wrongToken_rejectsWith401() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        StringWriter body = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(body));
        when(request.getHeader("X-Internal-Token")).thenReturn("wrong-token");

        filter.doFilterInternal(request, response, chain);

        verify(response).setStatus(401);
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void correctToken_allowsRequestThrough() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        when(request.getHeader("X-Internal-Token")).thenReturn(VALID_TOKEN);

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
    }
}
