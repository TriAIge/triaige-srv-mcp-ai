package br.com.triaige.mcpai.infrastructure.callback;

import br.com.triaige.mcpai.domain.exception.OrchestratorCallbackException;
import br.com.triaige.mcpai.infrastructure.config.McpProperties;
import br.com.triaige.mcpai.infrastructure.sqs.QueuePublisher;
import br.com.triaige.mcpai.shared.metrics.McpMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;

/**
 * Callback síncrono ao Orchestrator (spec seção 6): {@code POST .../sessions/{sessionId}/mcp-result}
 * com {@code X-Internal-Token}. Retry com backoff; se todas as tentativas falharem, publica o
 * mesmo payload na fila de fallback {@code triaige-mcp-callback-dlq} — nunca perde o resultado
 * silenciosamente.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrchestratorCallbackClient {

    private final McpProperties mcpProperties;
    private final QueuePublisher queuePublisher;
    private final McpMetrics metrics;

    public void send(McpResultCallbackPayload payload) {
        McpProperties.Callback config = mcpProperties.getCallback();
        RestClient restClient = buildClient(config);
        String path = config.getPathTemplate().replace("{sessionId}", payload.getSessionId().toString());

        List<Long> delays = config.getRetryDelaysMs();
        int totalAttempts = delays.size() + 1;

        for (int attempt = 1; attempt <= totalAttempts; attempt++) {
            try {
                restClient.post()
                        .uri(path)
                        .header("X-Internal-Token", config.getInternalToken())
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .body(payload)
                        .retrieve()
                        .toBodilessEntity();

                log.info("Orchestrator callback delivered: sessionId={}, attempt={}", payload.getSessionId(), attempt);
                return;
            } catch (Exception e) {
                log.warn("Orchestrator callback failed: sessionId={}, attempt={}/{}, error={}",
                        payload.getSessionId(), attempt, totalAttempts, e.getMessage());

                if (attempt == totalAttempts) {
                    handleExhausted(payload, config, e);
                    return;
                }
                sleep(delays.get(attempt - 1));
            }
        }
    }

    private void handleExhausted(McpResultCallbackPayload payload, McpProperties.Callback config, Exception cause) {
        log.error("Orchestrator callback exhausted retries, publishing to fallback queue: sessionId={}",
                payload.getSessionId(), cause);
        metrics.incrementMcpCallbackFailure();
        try {
            queuePublisher.publish(config.getFallbackQueueUrl(), payload);
        } catch (Exception publishFailure) {
            log.error("CRITICAL: failed to publish callback fallback to DLQ, result may be lost: sessionId={}",
                    payload.getSessionId(), publishFailure);
            throw new OrchestratorCallbackException(
                    "Callback e publicação de fallback falharam para sessionId=" + payload.getSessionId(),
                    publishFailure);
        }
    }

    private RestClient buildClient(McpProperties.Callback config) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(config.getConnectTimeoutMs()));
        factory.setReadTimeout(Duration.ofMillis(config.getReadTimeoutMs()));

        return RestClient.builder()
                .baseUrl(config.getOrchestratorBaseUrl())
                .requestFactory(factory)
                .defaultStatusHandler(HttpStatusCode::isError, (request, response) -> {
                    throw new IllegalStateException("Orchestrator respondeu " + response.getStatusCode());
                })
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
