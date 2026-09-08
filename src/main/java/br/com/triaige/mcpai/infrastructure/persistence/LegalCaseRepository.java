package br.com.triaige.mcpai.infrastructure.persistence;

import br.com.triaige.mcpai.domain.entity.LegalCase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface LegalCaseRepository extends JpaRepository<LegalCase, UUID> {

    Optional<LegalCase> findBySessionId(UUID sessionId);
}
