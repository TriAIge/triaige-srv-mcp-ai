package br.com.triaige.mcpai.infrastructure.persistence;

import br.com.triaige.mcpai.domain.entity.LegalDocument;
import br.com.triaige.mcpai.domain.enums.DocumentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LegalDocumentRepository extends JpaRepository<LegalDocument, UUID> {

    List<LegalDocument> findBySessionId(UUID sessionId);

    List<LegalDocument> findBySessionIdAndAttachmentGroupIdOrderByPartNumberAsc(UUID sessionId, UUID attachmentGroupId);

    boolean existsBySessionIdAndStatusNot(UUID sessionId, DocumentStatus status);
}
