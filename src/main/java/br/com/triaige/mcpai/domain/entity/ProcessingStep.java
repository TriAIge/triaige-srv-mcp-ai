package br.com.triaige.mcpai.domain.entity;

import br.com.triaige.mcpai.domain.converter.ProcessingStepNameConverter;
import br.com.triaige.mcpai.domain.enums.ProcessingStepName;
import br.com.triaige.mcpai.domain.enums.ProcessingStepStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Um registro por etapa (spec seção 5). Para 'ocr'/'anonymization' (nível documento):
 * documentId preenchido, attachmentGroupId nulo. Para 'attachment_grouping'/
 * 'evidence_summarization' (nível grupo): attachmentGroupId preenchido, documentId nulo
 * (gap de schema da migration V2 — ver seção 3 da spec).
 */
@Entity
@Table(name = "processing_steps")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProcessingStep {

    @Id
    @Column(name = "id", nullable = false, updatable = false, columnDefinition = "CHAR(36)")
    private UUID id;

    @Column(name = "session_id", nullable = false, columnDefinition = "CHAR(36)")
    private UUID sessionId;

    @Column(name = "document_id", columnDefinition = "CHAR(36)")
    private UUID documentId;

    @Column(name = "attachment_group_id", columnDefinition = "CHAR(36)")
    private UUID attachmentGroupId;

    @Convert(converter = ProcessingStepNameConverter.class)
    @Column(name = "step_name", nullable = false, length = 50)
    private ProcessingStepName stepName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private ProcessingStepStatus status;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void prePersist() {
        if (this.id == null) {
            this.id = UUID.randomUUID();
        }
    }
}
