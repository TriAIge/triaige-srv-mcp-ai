package br.com.triaige.mcpai.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/** T5 — tabela nova (migration V2, gap de schema). */
@Entity
@Table(name = "jurisprudence_cache")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JurisprudenceCache {

    @Id
    @Column(name = "id", nullable = false, updatable = false, columnDefinition = "CHAR(36)")
    private UUID id;

    @Column(name = "query_hash", nullable = false, updatable = false, length = 64)
    private String queryHash;

    @Column(name = "tese_juridica", nullable = false, length = 500)
    private String teseJuridica;

    @Column(name = "area_juridica", nullable = false, length = 30)
    private String areaJuridica;

    @Column(name = "response_payload", nullable = false, columnDefinition = "TEXT")
    private String responsePayload;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @PrePersist
    protected void prePersist() {
        if (this.id == null) {
            this.id = UUID.randomUUID();
        }
    }
}
