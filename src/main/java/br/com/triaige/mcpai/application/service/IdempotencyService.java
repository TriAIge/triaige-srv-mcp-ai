package br.com.triaige.mcpai.application.service;

import br.com.triaige.mcpai.domain.entity.IdempotencyRecord;
import br.com.triaige.mcpai.domain.entity.IdempotencyRecordId;
import br.com.triaige.mcpai.domain.exception.IdempotencyKeyConflictException;
import br.com.triaige.mcpai.domain.exception.MissingIdempotencyKeyException;
import br.com.triaige.mcpai.infrastructure.config.AiProperties;
import br.com.triaige.mcpai.infrastructure.persistence.IdempotencyRecordRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Suporte a idempotência para o endpoint {@code POST /api/ai/v1/analyze}: registra (idempotencyKey, endpoint, requestHash,
 * responseBody, statusCode) e, em reenvio com a mesma chave e mesmo payload, retorna a
 * resposta original sem reexecutar efeitos colaterais (não rechama o Gemini). Mesma chave
 * com payload diferente resulta em 409 IDEMPOTENCY_KEY_CONFLICT. Cópia adaptada de
 * triaige-srv-orchestrator/.../application/service/IdempotencyService.java.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final IdempotencyRecordRepository repository;
    private final ObjectMapper objectMapper;
    private final AiProperties aiProperties;

    @Transactional
    public <T> ResponseEntity<T> execute(String idempotencyKeyHeader, String endpoint, Object requestPayload,
                                          Class<T> responseType, Supplier<ResponseEntity<T>> action) {
        String idempotencyKey = parseKey(idempotencyKeyHeader).toString();
        String requestHash = hash(serialize(requestPayload));

        IdempotencyRecordId id = new IdempotencyRecordId(idempotencyKey, endpoint);
        Optional<IdempotencyRecord> existing = repository.findById(id);

        if (existing.isPresent()) {
            IdempotencyRecord record = existing.get();
            if (!record.getRequestHash().equals(requestHash)) {
                throw new IdempotencyKeyConflictException(idempotencyKey);
            }
            log.info("Idempotent replay: key={}, endpoint={}", idempotencyKey, endpoint);
            T body = deserialize(record.getResponseBody(), responseType);
            return ResponseEntity.status(record.getStatusCode()).body(body);
        }

        ResponseEntity<T> response = action.get();

        IdempotencyRecord record = IdempotencyRecord.builder()
                .id(id)
                .requestHash(requestHash)
                .responseBody(serialize(response.getBody()))
                .statusCode(response.getStatusCode().value())
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusHours(aiProperties.getIdempotency().getTtlHours()))
                .build();
        repository.save(record);

        return response;
    }

    private UUID parseKey(String idempotencyKeyHeader) {
        if (idempotencyKeyHeader == null || idempotencyKeyHeader.isBlank()) {
            throw new MissingIdempotencyKeyException();
        }
        try {
            return UUID.fromString(idempotencyKeyHeader.trim());
        } catch (IllegalArgumentException e) {
            throw new MissingIdempotencyKeyException();
        }
    }

    private String serialize(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao serializar payload para idempotência", e);
        }
    }

    private <T> T deserialize(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao desserializar resposta armazenada de idempotência", e);
        }
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 não disponível", e);
        }
    }
}
