package br.com.triaige.mcpai.infrastructure.persistence;

import br.com.triaige.mcpai.domain.entity.ProcessingStep;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProcessingStepRepository extends JpaRepository<ProcessingStep, UUID> {
}
