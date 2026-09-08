package br.com.triaige.mcpai.application.service;

import br.com.triaige.mcpai.domain.entity.AiToolCall;
import br.com.triaige.mcpai.domain.enums.AiToolCallStatus;
import br.com.triaige.mcpai.domain.enums.AiToolName;
import br.com.triaige.mcpai.infrastructure.persistence.AiToolCallRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Fonte da verdade para toda chamada de tool (T1-T5), spec seção 2.4: request_payload e
 * response_payload NUNCA contêm texto bruto/anonimizado ou PII — só metadados (contagens,
 * referências S3, categorias). Cabe a cada chamador montar um payload já nesse formato;
 * este serviço apenas serializa e persiste.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiToolCallService {

    private final AiToolCallRepository repository;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AiToolCall start(UUID sessionId, AiToolName toolName, String provider, Object requestPayload) {
        AiToolCall call = AiToolCall.builder()
                .sessionId(sessionId)
                .toolName(toolName)
                .provider(provider)
                .requestPayload(serialize(requestPayload))
                .status(AiToolCallStatus.IN_PROGRESS)
                .startedAt(LocalDateTime.now())
                .build();
        return repository.save(call);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void completeSuccess(UUID callId, Object responsePayload) {
        repository.findById(callId).ifPresent(call -> {
            call.setStatus(AiToolCallStatus.SUCCESS);
            call.setResponsePayload(serialize(responsePayload));
            call.setFinishedAt(LocalDateTime.now());
            repository.save(call);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void completeFailure(UUID callId, String errorMessage) {
        repository.findById(callId).ifPresent(call -> {
            call.setStatus(AiToolCallStatus.FAILED);
            call.setErrorMessage(truncate(errorMessage));
            call.setFinishedAt(LocalDateTime.now());
            repository.save(call);
        });
    }

    private String serialize(Object payload) {
        if (payload == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            log.warn("Failed to serialize ai_tool_calls payload (metadata only, not PII): {}", e.getMessage());
            return null;
        }
    }

    private String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() > 1000 ? message.substring(0, 1000) : message;
    }
}
