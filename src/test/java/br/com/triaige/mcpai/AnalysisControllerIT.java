package br.com.triaige.mcpai;

import br.com.triaige.mcpai.infrastructure.gemini.GeminiReasoningService;
import br.com.triaige.mcpai.infrastructure.gemini.ReasoningResult;
import br.com.triaige.mcpai.infrastructure.s3.TrustedDocumentPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Fase 4, spec seções 5, 11: 401 sem/com token errado, 200 com token válido (Gemini
 * mockado via {@code @MockBean} — chamar o Gemini real em CI não é viável), e replay de
 * idempotência (mesmo {@code Idempotency-Key} não reprocessa).
 */
class AnalysisControllerIT extends AbstractMcpAiIntegrationTest {

    private static final String VALID_ANALYZE_TOKEN = "dev-local-analyze-token";

    @MockitoBean
    private GeminiReasoningService geminiReasoningService;

    @Test
    void analyze_withoutInternalToken_returnsUnauthorized() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", UUID.randomUUID().toString());

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/api/ai/v1/analyze", HttpMethod.POST,
                new HttpEntity<>(buildRequestPayload(UUID.randomUUID(), UUID.randomUUID()), headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void analyze_withWrongInternalToken_returnsUnauthorized() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Internal-Token", "token-errado");
        headers.set("Idempotency-Key", UUID.randomUUID().toString());

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/api/ai/v1/analyze", HttpMethod.POST,
                new HttpEntity<>(buildRequestPayload(UUID.randomUUID(), UUID.randomUUID()), headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void analyze_withValidToken_returnsCompletedReport_andIdempotencyReplayDoesNotReprocess() throws Exception {
        UUID sessionId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        seedTrustedObject(groupId);

        when(geminiReasoningService.reason(any(), any(), any())).thenReturn(
                new ReasoningResult(validGeminiReportJson(groupId), 0, null, 100, 100, "STOP"));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Internal-Token", VALID_ANALYZE_TOKEN);
        String idempotencyKey = sessionId.toString();
        headers.set("Idempotency-Key", idempotencyKey);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(buildRequestPayload(sessionId, groupId), headers);

        ResponseEntity<Map> first = restTemplate.exchange(
                baseUrl() + "/api/ai/v1/analyze", HttpMethod.POST, entity, Map.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(first.getBody().get("status")).isEqualTo("COMPLETED");

        ResponseEntity<Map> replay = restTemplate.exchange(
                baseUrl() + "/api/ai/v1/analyze", HttpMethod.POST, entity, Map.class);
        assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(replay.getBody()).isEqualTo(first.getBody());

        verify(geminiReasoningService, times(1)).reason(any(), any(), any());
    }

    private void seedTrustedObject(UUID groupId) throws Exception {
        TrustedDocumentPayload payload = TrustedDocumentPayload.builder()
                .attachmentGroupId(groupId)
                .textoAnonimizado("Texto anonimizado de teste para análise de IA.")
                .partesOrigem(List.of(TrustedDocumentPayload.PartOrigem.builder()
                        .documentId(UUID.randomUUID()).partNumber(1).nomeArquivoOriginal("peticao.pdf").build()))
                .build();
        S3Client s3Client = S3Client.builder()
                .endpointOverride(localstack.getEndpointOverride(
                        org.testcontainers.containers.localstack.LocalStackContainer.Service.S3))
                .region(Region.of(localstack.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(localstack.getAccessKey(), localstack.getSecretKey())))
                .forcePathStyle(true)
                .build();
        s3Client.putObject(PutObjectRequest.builder().bucket(TRUSTED_BUCKET).key("trusted/" + groupId + ".json").build(),
                RequestBody.fromString(new ObjectMapper().writeValueAsString(payload)));
    }

    private Map<String, Object> buildRequestPayload(UUID sessionId, UUID groupId) {
        return Map.of(
                "sessionId", sessionId.toString(),
                "correlationId", UUID.randomUUID().toString(),
                "protocolo", "PROTO-001",
                "legalCase", Map.of(
                        "id", UUID.randomUUID().toString(),
                        "titulo", "Caso teste",
                        "areaJuridica", "civel",
                        "tipoCaso", "cobranca"),
                "processedGroups", List.of(Map.of(
                        "attachmentGroupId", groupId.toString(),
                        "trustedBucket", TRUSTED_BUCKET,
                        "trustedObjectKey", "trusted/" + groupId + ".json",
                        "tipoDocumento", "PETICAO_INICIAL",
                        "resumido", false)),
                "failedDocuments", List.of());
    }

    private String validGeminiReportJson(UUID groupId) {
        return """
                {
                  "resumoExecutivo": "resumo",
                  "fundamentacaoJuridica": "fundamentacao",
                  "evidenciasAnalisadas": [
                    { "attachmentGroupId": "%s", "tipoDocumento": "peticao", "relevancia": "alta", "observacao": "obs" }
                  ],
                  "jurisprudenciaCitada": [],
                  "riscosIdentificados": [],
                  "recomendacao": { "classificacao": "viavel", "justificativa": "justificativa" }
                }
                """.formatted(groupId);
    }
}
