package br.com.triaige.mcpai.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

import java.io.Serializable;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class IdempotencyRecordId implements Serializable {

    @Column(name = "idempotency_key", nullable = false, columnDefinition = "CHAR(36)")
    private String idempotencyKey;

    @Column(name = "endpoint", nullable = false, length = 150)
    private String endpoint;
}
