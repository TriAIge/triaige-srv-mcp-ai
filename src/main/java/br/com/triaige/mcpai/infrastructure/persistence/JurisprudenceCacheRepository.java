package br.com.triaige.mcpai.infrastructure.persistence;

import br.com.triaige.mcpai.domain.entity.JurisprudenceCache;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface JurisprudenceCacheRepository extends JpaRepository<JurisprudenceCache, UUID> {

    Optional<JurisprudenceCache> findByQueryHash(String queryHash);
}
