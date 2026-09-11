package br.com.triaige.mcpai.domain.entity;

import br.com.triaige.mcpai.domain.converter.AiToolNameConverter;
import br.com.triaige.mcpai.domain.enums.AiToolCallStatus;
import br.com.triaige.mcpai.domain.enums.AiToolName;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Fonte da verdade para toda chamada de tool (T1-T5). request_payload/response_payload
 * NUNCA contêm texto bruto/anonimizado ou PII — apenas metadados.
 */
@Entity
@Table(name = "ai_tool_calls")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiToolCall {

    @Id
    @Column(name = "id", nullable = false, updatable = false, columnDefinition = "CHAR(36)")
    private UUID id;

    @Column(name = "session_id", nullable = false, columnDefinition = "CHAR(36)")
    private UUID sessionId;

    @Convert(converter = AiToolNameConverter.class)
    @Column(name = "tool_name", nullable = false, length = 80)
    private AiToolName toolName;

    @Column(name = "provider", length = 80)
    private String provider;

    @Column(name = "request_payload", columnDefinition = "TEXT")
    private String requestPayload;

    @Column(name = "response_payload", columnDefinition = "TEXT")
    private String responsePayload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private AiToolCallStatus status;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @PrePersist
    protected void prePersist() {
        if (this.id == null) {
            this.id = UUID.randomUUID();
        }
    }
}
