package br.com.triaige.mcpai.shared.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** Métricas — namespace CloudWatch Triaige/MCP (via management.cloudwatch.metrics.export). */
@Component
@RequiredArgsConstructor
public class McpMetrics {

    private final MeterRegistry registry;

    public void recordPipelineLatency(long millis) {
        registry.timer("PipelineLatencyMs").record(Duration.ofMillis(millis));
    }

    public void recordOcrLatency(long millis) {
        registry.timer("OcrLatencyMs").record(Duration.ofMillis(millis));
    }

    public void incrementOcrFailure(String reason) {
        registry.counter("OcrFailureCount", "reason", reason).increment();
    }

    public void incrementAnonymizationPii(String category, int count) {
        registry.counter("AnonymizationPiiCount", "category", category).increment(count);
    }

    public void incrementSummarizationTriggered() {
        registry.counter("SummarizationTriggeredCount").increment();
    }

    public void recordJurisprudenceCacheHit(boolean hit) {
        registry.counter("JurisprudenceCacheHitRatio", "result", hit ? "hit" : "miss").increment();
    }

    public void incrementMcpCallbackFailure() {
        registry.counter("McpCallbackFailureCount").increment();
    }

    // --- Análise com Gemini ---

    public void recordAnalysisLatency(long millis) {
        registry.timer("AnalysisLatencyMs").record(Duration.ofMillis(millis));
    }

    public void incrementAnalysisSuccess() {
        registry.counter("AnalysisSuccessCount").increment();
    }

    public void incrementAnalysisFailure(String errorCode) {
        registry.counter("AnalysisFailureCount", "error", errorCode).increment();
    }

    public void incrementGeminiRetry() {
        registry.counter("GeminiRetryCount").increment();
    }

    public void incrementCircuitBreakerOpen() {
        registry.counter("CircuitBreakerOpenCount").increment();
    }

    public void recordJurisprudenceToolCallsPerAnalysis(int count) {
        registry.summary("JurisprudenceToolCallsPerAnalysis").record(count);
    }

    public void incrementInvalidReportFormat() {
        registry.counter("InvalidReportFormatCount").increment();
    }
}
