package br.com.triaige.mcpai.infrastructure.gemini;

import br.com.triaige.mcpai.domain.exception.LlmUnavailableException;
import br.com.triaige.mcpai.infrastructure.circuitbreaker.SimpleCircuitBreaker;
import br.com.triaige.mcpai.infrastructure.config.AiProperties;
import br.com.triaige.mcpai.infrastructure.gemini.model.GeminiRequest;
import br.com.triaige.mcpai.infrastructure.gemini.model.GeminiResponse;
import br.com.triaige.mcpai.shared.metrics.McpMetrics;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.List;

/**
 * Client HTTP direto à Gemini Generative Language API — sem SDK/spring-ai (spec Fase 3,
 * seção 5.2): timeout 60s, retry em 429/5xx com backoff 2s/8s/20s (3 tentativas), circuit
 * breaker após 5 falhas consecutivas (todas as tentativas esgotadas) abrindo por 2 min.
 * Modelo configurável via {@code GEMINI_MODEL}, nunca hardcoded. Sem {@code GEMINI_API_KEY}
 * configurada, toda chamada falha explicitamente. Na primeira falha retryable (429/5xx) o
 * client alterna definitivamente para {@code GEMINI_FALLBACK_MODEL} nas tentativas restantes,
 * já que picos de "high demand" (503) do Google afetam a capacidade de um modelo específico,
 * não a cota do projeto — outro modelo tem pool de capacidade próprio.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GeminiClient {

    private final AiProperties aiProperties;
    private final McpMetrics metrics;

    private SimpleCircuitBreaker circuitBreaker;

    @PostConstruct
    void init() {
        AiProperties.Gemini config = aiProperties.getGemini();
        this.circuitBreaker = new SimpleCircuitBreaker(
                config.getCircuitBreakerFailureThreshold(), config.getCircuitBreakerOpenMs());
    }

    public GeminiResponse generateContent(GeminiRequest request) {
        AiProperties.Gemini config = aiProperties.getGemini();

        if (config.getApiKey() == null || config.getApiKey().isBlank()) {
            throw new LlmUnavailableException("GEMINI_API_KEY não configurada");
        }

        if (!circuitBreaker.allowRequest()) {
            metrics.incrementCircuitBreakerOpen();
            throw new LlmUnavailableException("Circuito aberto para o Gemini (muitas falhas consecutivas recentes)");
        }

        RestClient restClient = buildClient(config);
        List<Long> delays = config.getRetryDelaysMs();
        int totalAttempts = delays.size() + 1;
        Exception lastError = null;
        String model = config.getModel();
        boolean usingFallback = false;

        for (int attempt = 1; attempt <= totalAttempts; attempt++) {
            try {
                GeminiResponse response = restClient.post()
                        .uri("/models/{model}:generateContent?key={key}", model, config.getApiKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(request)
                        .retrieve()
                        .body(GeminiResponse.class);
                circuitBreaker.recordSuccess();
                return response;
            } catch (RestClientResponseException e) {
                lastError = e;
                if (!isRetryable(e.getStatusCode())) {
                    circuitBreaker.recordFailure();
                    throw new LlmUnavailableException("Gemini respondeu " + e.getStatusCode().value()
                            + " (não recuperável): " + safeMessage(e));
                }
                log.warn("Gemini call failed (attempt {}/{}), model={}, status={}: {}",
                        attempt, totalAttempts, model, e.getStatusCode(), safeMessage(e));
            } catch (Exception e) {
                lastError = e;
                log.warn("Gemini call failed (attempt {}/{}), model={}: {}", attempt, totalAttempts, model, e.getMessage());
            }

            if (attempt < totalAttempts) {
                metrics.incrementGeminiRetry();
                if (!usingFallback && config.getFallbackModel() != null && !config.getFallbackModel().isBlank()
                        && !config.getFallbackModel().equals(model)) {
                    usingFallback = true;
                    model = config.getFallbackModel();
                    log.warn("Alternando para modelo de fallback do Gemini: {}", model);
                }
                sleep(delays.get(attempt - 1));
            }
        }

        circuitBreaker.recordFailure();
        throw new LlmUnavailableException("Gemini indisponível após esgotar retries (modelos tentados: "
                + config.getModel() + (usingFallback ? ", " + config.getFallbackModel() : "") + "): "
                + (lastError != null ? lastError.getMessage() : "erro desconhecido"));
    }

    private boolean isRetryable(HttpStatusCode status) {
        return status.value() == 429 || status.is5xxServerError();
    }

    private String safeMessage(RestClientResponseException e) {
        String body = e.getResponseBodyAsString();
        return body == null || body.isBlank() ? e.getMessage() : body;
    }

    private RestClient buildClient(AiProperties.Gemini config) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(config.getTimeoutMs()));
        factory.setReadTimeout(Duration.ofMillis(config.getTimeoutMs()));

        return RestClient.builder()
                .baseUrl(config.getEndpoint())
                .requestFactory(factory)
                .build();
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
