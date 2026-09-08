package br.com.triaige.mcpai.application.service;

import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SessionPipelineStateRegistry {

    private final ConcurrentHashMap<UUID, SessionPipelineState> states = new ConcurrentHashMap<>();

    public void register(SessionPipelineState state) {
        states.put(state.sessionId(), state);
    }

    public Optional<SessionPipelineState> get(UUID sessionId) {
        return Optional.ofNullable(states.get(sessionId));
    }

    public void remove(UUID sessionId) {
        states.remove(sessionId);
    }
}
