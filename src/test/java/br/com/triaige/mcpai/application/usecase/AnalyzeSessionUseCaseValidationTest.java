package br.com.triaige.mcpai.application.usecase;

import br.com.triaige.mcpai.api.dto.request.AnalysisRequest;
import br.com.triaige.mcpai.api.dto.response.AnalysisResponse;
import br.com.triaige.mcpai.api.dto.response.RelatorioEstruturado;
import br.com.triaige.mcpai.application.service.AiToolCallService;
import br.com.triaige.mcpai.domain.entity.AiToolCall;
import br.com.triaige.mcpai.domain.enums.AiToolCallStatus;
import br.com.triaige.mcpai.domain.exception.InvalidReportFormatException;
import br.com.triaige.mcpai.infrastructure.config.AiProperties;
import br.com.triaige.mcpai.infrastructure.gemini.GeminiReasoningService;
import br.com.triaige.mcpai.infrastructure.gemini.ReasoningResult;
import br.com.triaige.mcpai.infrastructure.s3.TrustedDocumentPayload;
import br.com.triaige.mcpai.infrastructure.s3.TrustedDocumentReader;
import br.com.triaige.mcpai.shared.metrics.McpMetrics;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Cobre a extensão ao gate de retry de formato: confianca fora de
 * [0,100] e nivelAtendimentoSugerido fora do enum recebem o mesmo tratamento de
 * INVALID_REPORT_FORMAT/1 retry de geração que já existia para JSON não-parseável.
 */
class AnalyzeSessionUseCaseValidationTest {

    private static final UUID SESSION_ID = UUID.randomUUID();
    private static final UUID GROUP_ID = UUID.randomUUID();

    private TrustedDocumentReader trustedDocumentReader;
    private GeminiReasoningService geminiReasoningService;
    private AiToolCallService aiToolCallService;
    private AiProperties aiProperties;
    private McpMetrics metrics;
    private AnalyzeSessionUseCase useCase;

    @BeforeEach
    void setUp() {
        trustedDocumentReader = mock(TrustedDocumentReader.class);
        geminiReasoningService = mock(GeminiReasoningService.class);
        aiToolCallService = mock(AiToolCallService.class);
        metrics = mock(McpMetrics.class);
        aiProperties = new AiProperties();

        useCase = new AnalyzeSessionUseCase(trustedDocumentReader, geminiReasoningService, aiToolCallService,
                aiProperties, metrics, new ObjectMapper());

        when(trustedDocumentReader.read(anyString(), anyString())).thenReturn(
                TrustedDocumentPayload.builder()
                        .attachmentGroupId(GROUP_ID)
                        .textoAnonimizado("texto anonimizado de teste")
                        .partesOrigem(List.of(TrustedDocumentPayload.PartOrigem.builder()
                                .documentId(UUID.randomUUID())
                                .partNumber(1)
                                .nomeArquivoOriginal("peticao-inicial.pdf")
                                .build()))
                        .build());

        when(aiToolCallService.start(any(), any(), anyString(), any())).thenReturn(
                AiToolCall.builder().id(UUID.randomUUID()).status(AiToolCallStatus.IN_PROGRESS).build());
    }

    private AnalysisRequest buildRequest() {
        return AnalysisRequest.builder()
                .sessionId(SESSION_ID)
                .correlationId(UUID.randomUUID())
                .protocolo("PROTO-001")
                .legalCase(AnalysisRequest.LegalCaseDto.builder()
                        .id(UUID.randomUUID())
                        .titulo("Caso teste")
                        .areaJuridica("civel")
                        .tipoCaso("cobranca")
                        .build())
                .processedGroups(List.of(AnalysisRequest.ProcessedGroupDto.builder()
                        .attachmentGroupId(GROUP_ID)
                        .trustedBucket("bucket-trusted")
                        .trustedObjectKey("key.json")
                        .tipoDocumento("peticao")
                        .build()))
                .build();
    }

    private String validReportJsonWith(String extraFields) {
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
                  %s
                }
                """.formatted(GROUP_ID, extraFields.isBlank() ? "" : "," + extraFields);
    }

    @Test
    void confiancaForaDaFaixa_disparaRetryDepoisFalha() {
        String invalidJson = validReportJsonWith("""
                "avaliacaoCriticidade": { "criticidade": { "nivel": "alta", "confianca": 150, "justificativa": "j" } }
                """);
        ReasoningResult result = new ReasoningResult(invalidJson, 0, null, 10, 10, "STOP");
        when(geminiReasoningService.reason(any(), any(), anyString())).thenReturn(result);

        assertThatThrownBy(() -> useCase.execute(buildRequest()))
                .isInstanceOf(InvalidReportFormatException.class);

        verify(geminiReasoningService, times(2)).reason(any(), any(), anyString());
        verify(metrics, times(2)).incrementInvalidReportFormat();
    }

    @Test
    void nivelAtendimentoForaDoEnum_disparaRetryDepoisFalha() {
        String invalidJson = validReportJsonWith("""
                "classificacaoInicial": { "nivelAtendimentoSugerido": "estagiario" }
                """);
        ReasoningResult result = new ReasoningResult(invalidJson, 0, null, 10, 10, "STOP");
        when(geminiReasoningService.reason(any(), any(), anyString())).thenReturn(result);

        assertThatThrownBy(() -> useCase.execute(buildRequest()))
                .isInstanceOf(InvalidReportFormatException.class);

        verify(geminiReasoningService, times(2)).reason(any(), any(), anyString());
    }

    @Test
    void schemaV2Valido_produzRelatorioComSchemaVersion2ENomeArquivoInjetado() {
        String validJson = validReportJsonWith("""
                "classificacaoInicial": { "foroCompetenteEstimado": "Comarca X", "nivelAtendimentoSugerido": "pleno", "equipeResponsavelSugerida": "Equipe cível" },
                "avaliacaoCriticidade": {
                  "criticidade": { "nivel": "alta", "confianca": 80, "justificativa": "j" },
                  "complexidade": { "nivel": "media", "confianca": 60, "justificativa": "j" },
                  "impactoFinanceiroEstimado": { "valor": "não identificado nas evidências", "confianca": 0, "justificativa": "j" }
                },
                "partesExtraidas": { "poloAtivo": "[REQUERENTE_01]", "poloPassivo": "[REQUERIDO_01]", "terceirosInteressados": null },
                "controleDePrazos": { "dataFato": "não identificado nas evidências", "dataIntimacao": "não identificado nas evidências", "prazoFatalEstimado": "não identificado nas evidências", "tipoPrazo": null, "riscoPrescricao": "não_identificado" },
                "resumoEstruturado": { "sinteseFatos": "sintese", "pedidos": ["pedido 1"], "pontosCriticos": ["ponto 1"] }
                """);
        ReasoningResult result = new ReasoningResult(validJson, 1, UUID.randomUUID(), 10, 10, "STOP");
        when(geminiReasoningService.reason(any(), any(), anyString())).thenReturn(result);

        AnalysisResponse response = useCase.execute(buildRequest());

        RelatorioEstruturado relatorio = response.getRelatorioEstruturado();
        assertThat(relatorio.getSchemaVersion()).isEqualTo("2.0");
        assertThat(relatorio.getClassificacaoInicial().getNivelAtendimentoSugerido()).isEqualTo("pleno");
        assertThat(relatorio.getEvidenciasAnalisadas()).hasSize(1);
        assertThat(relatorio.getEvidenciasAnalisadas().get(0).getNomeArquivoOriginal()).isEqualTo("peticao-inicial.pdf");

        verify(geminiReasoningService, times(1)).reason(any(), any(), anyString());
    }
}
