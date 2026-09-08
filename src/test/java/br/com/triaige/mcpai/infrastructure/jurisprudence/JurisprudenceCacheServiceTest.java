package br.com.triaige.mcpai.infrastructure.jurisprudence;

import br.com.triaige.mcpai.infrastructure.config.McpProperties;
import br.com.triaige.mcpai.infrastructure.persistence.JurisprudenceCacheRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T5 (spec seção 7, Definition of Done): cache hit verificável — mesma consulta normalizada
 * (case/acentuação/espaços) produz o mesmo query_hash e retorna do cache sem novo provider call.
 * Perfil "test" (src/test/resources/application-test.yml): ddl-auto=create-drop contra o H2
 * embutido deste slice test.
 */
@DataJpaTest
@ActiveProfiles("test")
class JurisprudenceCacheServiceTest {

    @Autowired
    private JurisprudenceCacheRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    private JurisprudenceCacheService buildService() {
        McpProperties properties = new McpProperties();
        properties.getJurisprudence().setCacheTtlDays(7);
        return new JurisprudenceCacheService(repository, properties, new ObjectMapper());
    }

    @Test
    @DisplayName("mesma tese/área normalizada (case, acentos, espaços) produz o mesmo hash")
    void sameNormalizedQueryProducesSameHash() {
        JurisprudenceCacheService service = buildService();

        String h1 = service.hash("Dano Moral em Relação de Consumo", "Consumidor");
        String h2 = service.hash("  dano moral em relação de consumo  ", "consumidor");
        String h3 = service.hash("dano moral em relacao de consumo", "CONSUMIDOR");

        assertThat(h1).isEqualTo(h2).isEqualTo(h3);
    }

    @Test
    @DisplayName("teses diferentes produzem hashes diferentes")
    void differentQueriesProduceDifferentHashes() {
        JurisprudenceCacheService service = buildService();
        assertThat(service.hash("dano moral", "consumidor")).isNotEqualTo(service.hash("dano material", "consumidor"));
    }

    @Test
    @DisplayName("cache miss quando não há entrada persistida")
    void returnsEmptyOnCacheMiss() {
        JurisprudenceCacheService service = buildService();
        assertThat(service.get(service.hash("tese inexistente", "civel"))).isEmpty();
    }

    @Test
    @DisplayName("cache hit: consulta salva é recuperada integralmente e sem chamar o provedor")
    void cacheHitReturnsPersistedResult() {
        JurisprudenceCacheService service = buildService();
        String hash = service.hash("responsabilidade objetiva do fornecedor", "consumidor");

        List<JurisprudenceItem> items = List.of(JurisprudenceItem.builder()
                .titulo("Caso X")
                .ementa("Ementa Y")
                .fonte("STJ")
                .url("https://example.com/x")
                .build());

        service.save(hash, "responsabilidade objetiva do fornecedor", "consumidor", items);
        entityManager.flush();
        entityManager.clear();

        Optional<List<JurisprudenceItem>> cached = service.get(hash);

        assertThat(cached).isPresent();
        assertThat(cached.get()).hasSize(1);
        assertThat(cached.get().get(0).getTitulo()).isEqualTo("Caso X");
    }

    @Test
    @DisplayName("entrada expirada não é retornada (tratada como cache miss)")
    void expiredEntryIsTreatedAsCacheMiss() {
        JurisprudenceCacheService service = buildService();
        String hash = service.hash("tese expirada", "trabalhista");

        var expired = br.com.triaige.mcpai.domain.entity.JurisprudenceCache.builder()
                .queryHash(hash)
                .teseJuridica("tese expirada")
                .areaJuridica("trabalhista")
                .responsePayload("[]")
                .expiresAt(java.time.LocalDateTime.now().minusDays(1))
                .build();
        repository.save(expired);
        entityManager.flush();
        entityManager.clear();

        assertThat(service.get(hash)).isEmpty();
    }
}
