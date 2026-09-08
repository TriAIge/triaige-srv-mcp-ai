package br.com.triaige.mcpai.infrastructure.persistence;

import br.com.triaige.mcpai.domain.entity.TriageSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TriageSessionRepository extends JpaRepository<TriageSession, UUID> {
}
