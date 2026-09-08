package br.com.triaige.mcpai.infrastructure.circuitbreaker;

import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Circuit breaker hand-rolled em memória (sem biblioteca — mesma filosofia de dependências
 * mínimas já usada no repo, ex. retry manual em OrchestratorCallbackClient). Semântica: após
 * {@code failureThreshold} falhas CONSECUTIVAS, o circuito abre por {@code openDuration};
 * chamadas feitas com o circuito aberto devem ser recusadas pelo chamador (ver
 * {@link #allowRequest()}) sem sequer tentar o recurso remoto. Um único sucesso zera o
 * contador de falhas e fecha o circuito. Estado single-instance (não compartilhado entre
 * réplicas) — aceitável nesta fase, mesma convenção já adotada para coordenação de estado
 * em memória em outros pontos do serviço (ex. SessionPipelineStateRegistry).
 */
public class SimpleCircuitBreaker {

    private final int failureThreshold;
    private final long openDurationMs;
    private final Clock clock;

    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private final AtomicReference<Instant> openUntil = new AtomicReference<>();

    public SimpleCircuitBreaker(int failureThreshold, long openDurationMs) {
        this(failureThreshold, openDurationMs, Clock.systemUTC());
    }

    SimpleCircuitBreaker(int failureThreshold, long openDurationMs, Clock clock) {
        this.failureThreshold = failureThreshold;
        this.openDurationMs = openDurationMs;
        this.clock = clock;
    }

    /** {@code true} se o circuito está fechado (ou já expirou a janela de abertura) — chamada pode prosseguir. */
    public boolean allowRequest() {
        Instant until = openUntil.get();
        if (until == null) {
            return true;
        }
        if (clock.instant().isAfter(until)) {
            openUntil.compareAndSet(until, null);
            return true;
        }
        return false;
    }

    public void recordSuccess() {
        consecutiveFailures.set(0);
        openUntil.set(null);
    }

    public void recordFailure() {
        int failures = consecutiveFailures.incrementAndGet();
        if (failures >= failureThreshold) {
            openUntil.set(clock.instant().plusMillis(openDurationMs));
        }
    }
}
