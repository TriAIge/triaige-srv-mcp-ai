package br.com.triaige.mcpai.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Espelha triage_sessions (owned pelo triaige-srv-orchestrator). O MCP só lê esta tabela
 * para validar a sessão e obter lawFirmId/protocolo/correlationId (spec seção 3) — não
 * mapeia relações JPA para tabelas de outro serviço (law_firms, api_credentials).
 */
@Entity
@Table(name = "triage_sessions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TriageSession {

    @Id
    @Column(name = "id", nullable = false, updatable = false, columnDefinition = "CHAR(36)")
    private UUID id;

    @Column(name = "law_firm_id", nullable = false, columnDefinition = "CHAR(36)")
    private UUID lawFirmId;

    @Column(name = "protocolo", nullable = false, length = 30)
    private String protocolo;

    @Column(name = "correlation_id", nullable = false, columnDefinition = "CHAR(36)")
    private UUID correlationId;

    /** Status de domínio do Orchestrator (RECEIVING_DOCUMENTS, QUEUED_FOR_PROCESSING, COMPLETED, ...) — mantido como String para não acoplar o MCP à máquina de estados de outro serviço. */
    @Column(name = "status", nullable = false, length = 40)
    private String status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
