package br.com.triaige.mcpai.infrastructure.gemini;

import br.com.triaige.mcpai.api.dto.request.AnalysisRequest;
import br.com.triaige.mcpai.infrastructure.config.AiProperties;
import br.com.triaige.mcpai.infrastructure.gemini.model.GeminiContent;
import br.com.triaige.mcpai.infrastructure.gemini.model.GeminiPart;
import br.com.triaige.mcpai.infrastructure.gemini.model.GeminiRequest;
import br.com.triaige.mcpai.infrastructure.gemini.model.GeminiResponse;
import br.com.triaige.mcpai.infrastructure.jurisprudence.JurisprudenceQueryTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Regressão: a API do Gemini rejeita com 400 ("Function calling with a response mime type:
 * 'application/json' is unsupported") quando {@code tools} e
 * {@code generationConfig.responseMimeType=application/json} são enviados na mesma
 * chamada — isso derrubava 100% das análises. {@code generationConfig} só pode ser anexado
 * na rodada em que {@code tools} não está presente.
 */
class GeminiReasoningServiceTest {

    private GeminiClient geminiClient;
    private JurisprudenceQueryTool jurisprudenceQueryTool;
    private AiProperties aiProperties;
    private GeminiReasoningService service;

    @BeforeEach
    void setUp() {
        geminiClient = mock(GeminiClient.class);
        jurisprudenceQueryTool = mock(JurisprudenceQueryTool.class);
        aiProperties = new AiProperties();
        aiProperties.getAnalysis().setMaxToolCalls(3);

        PromptProvider promptProvider = mock(PromptProvider.class);
        when(promptProvider.load()).thenReturn("prompt de sistema de teste");

        service = new GeminiReasoningService(geminiClient, jurisprudenceQueryTool, aiProperties, promptProvider);
    }

    @Test
    void requestComToolsAnexadoNaoEnviaResponseMimeType() {
        when(geminiClient.generateContent(any())).thenReturn(finalTextResponse("{}"));

        service.reason(UUID.randomUUID(), legalCase(), "contexto");

        GeminiRequest request = captureRequest();
        assertThat(request.getTools()).isNotNull();
        assertThat(request.getGenerationConfig()).isNull();
    }

    @Test
    void requestSemToolsNaRodadaFinalEnviaResponseMimeTypeJson() {
        aiProperties.getAnalysis().setMaxToolCalls(0);
        when(geminiClient.generateContent(any())).thenReturn(finalTextResponse("{}"));

        service.reason(UUID.randomUUID(), legalCase(), "contexto");

        GeminiRequest request = captureRequest();
        assertThat(request.getTools()).isNull();
        assertThat(request.getGenerationConfig()).containsEntry("responseMimeType", "application/json");
    }

    private GeminiRequest captureRequest() {
        var captor = org.mockito.ArgumentCaptor.forClass(GeminiRequest.class);
        org.mockito.Mockito.verify(geminiClient).generateContent(captor.capture());
        return captor.getValue();
    }

    private AnalysisRequest.LegalCaseDto legalCase() {
        return AnalysisRequest.LegalCaseDto.builder()
                .id(UUID.randomUUID())
                .titulo("Caso de teste")
                .areaJuridica("Cível")
                .tipoCaso("Indenização")
                .build();
    }

    private GeminiResponse finalTextResponse(String text) {
        GeminiResponse response = new GeminiResponse();
        GeminiResponse.Candidate candidate = new GeminiResponse.Candidate();
        candidate.setFinishReason("STOP");
        candidate.setContent(GeminiContent.builder()
                .role("model")
                .parts(List.of(GeminiPart.builder().text(text).build()))
                .build());
        response.setCandidates(List.of(candidate));
        return response;
    }
}
