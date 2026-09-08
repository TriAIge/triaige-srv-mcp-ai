package br.com.triaige.mcpai.infrastructure.persistence;

import br.com.triaige.mcpai.domain.entity.AiToolCall;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AiToolCallRepository extends JpaRepository<AiToolCall, UUID> {
}
