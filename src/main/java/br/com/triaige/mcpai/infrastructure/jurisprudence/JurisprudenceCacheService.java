package br.com.triaige.mcpai.infrastructure.jurisprudence;

import br.com.triaige.mcpai.domain.entity.JurisprudenceCache;
import br.com.triaige.mcpai.infrastructure.config.McpProperties;
import br.com.triaige.mcpai.infrastructure.persistence.JurisprudenceCacheRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** Cache de T5 (spec seção 7): chave = SHA-256(teseJuridica normalizada + areaJuridica normalizada). */
@Slf4j
@Service
@RequiredArgsConstructor
public class JurisprudenceCacheService {

    private final JurisprudenceCacheRepository repository;
    private final McpProperties mcpProperties;
    private final ObjectMapper objectMapper;

    public String hash(String teseJuridica, String areaJuridica) {
        String normalized = normalize(teseJuridica) + "|" + normalize(areaJuridica);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(normalized.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hashBytes.length * 2);
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 não disponível na JVM", e);
        }
    }

    @Transactional(readOnly = true)
    public Optional<List<JurisprudenceItem>> get(String queryHash) {
        return repository.findByQueryHash(queryHash)
                .filter(cache -> cache.getExpiresAt().isAfter(LocalDateTime.now()))
                .map(this::deserialize);
    }

    @Transactional
    public void save(String queryHash, String teseJuridica, String areaJuridica, List<JurisprudenceItem> items) {
        JurisprudenceCache cache = JurisprudenceCache.builder()
                .queryHash(queryHash)
                .teseJuridica(teseJuridica)
                .areaJuridica(areaJuridica)
                .responsePayload(serialize(items))
                .expiresAt(LocalDateTime.now().plusDays(mcpProperties.getJurisprudence().getCacheTtlDays()))
                .build();
        repository.save(cache);
    }

    private String normalize(String value) {
        String noAccents = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return noAccents.trim().toLowerCase();
    }

    private String serialize(List<JurisprudenceItem> items) {
        try {
            return objectMapper.writeValueAsString(items);
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao serializar resultado de jurisprudência para cache", e);
        }
    }

    private List<JurisprudenceItem> deserialize(JurisprudenceCache cache) {
        try {
            return objectMapper.readValue(cache.getResponsePayload(), new TypeReference<List<JurisprudenceItem>>() {
            });
        } catch (Exception e) {
            log.warn("Failed to deserialize cached jurisprudence payload, treating as cache miss: queryHash={}",
                    cache.getQueryHash());
            return List.of();
        }
    }
}
