package br.com.triaige.mcpai.domain.entity;

import br.com.triaige.mcpai.domain.enums.DocumentStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "legal_documents")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LegalDocument {

    @Id
    @Column(name = "id", nullable = false, updatable = false, columnDefinition = "CHAR(36)")
    private UUID id;

    @Column(name = "session_id", nullable = false, columnDefinition = "CHAR(36)")
    private UUID sessionId;

    @Column(name = "attachment_group_id", nullable = false, columnDefinition = "CHAR(36)")
    private UUID attachmentGroupId;

    @Column(name = "part_number", nullable = false)
    @Builder.Default
    private Integer partNumber = 1;

    @Column(name = "nome_arquivo_original", nullable = false, length = 255)
    private String nomeArquivoOriginal;

    @Column(name = "tipo_documento", nullable = false, length = 30)
    private String tipoDocumento;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "tamanho_bytes")
    private Long tamanhoBytes;

    @Column(name = "raw_bucket", nullable = false, length = 200)
    private String rawBucket;

    @Column(name = "raw_object_key", nullable = false, length = 500)
    private String rawObjectKey;

    @Column(name = "raw_deleted_at")
    private LocalDateTime rawDeletedAt;

    @Column(name = "retention_expires_at")
    private LocalDateTime retentionExpiresAt;

    @Column(name = "processed_bucket", length = 200)
    private String processedBucket;

    @Column(name = "processed_object_key", length = 500)
    private String processedObjectKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private DocumentStatus status;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
