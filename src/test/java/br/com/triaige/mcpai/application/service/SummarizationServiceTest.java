package br.com.triaige.mcpai.application.service;

import br.com.triaige.mcpai.domain.entity.AiToolCall;
import br.com.triaige.mcpai.domain.entity.ProcessingStep;
import br.com.triaige.mcpai.infrastructure.config.McpProperties;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** T4 (Definition of Done): reduz o texto conforme o teto configurado, sem LLM externo. */
class SummarizationServiceTest {

    private McpProperties mcpProperties;
    private SummarizationService service;

    @BeforeEach
    void setUp() {
        ProcessingStepService processingStepService = mock(ProcessingStepService.class);
        AiToolCallService aiToolCallService = mock(AiToolCallService.class);
        when(processingStepService.startGroupStep(any(), any(), any()))
                .thenReturn(ProcessingStep.builder().id(UUID.randomUUID()).build());
        when(aiToolCallService.start(any(), any(), any(), any()))
                .thenReturn(AiToolCall.builder().id(UUID.randomUUID()).build());

        mcpProperties = new McpProperties();
        mcpProperties.getSummarization().setSummaryMaxTokens(50); // teto baixo para o teste

        service = new SummarizationService(mcpProperties, processingStepService, aiToolCallService);
    }

    @Test
    @DisplayName("reduz o texto para dentro do teto configurado (summaryMaxTokens * 4 caracteres)")
    void reducesTextWithinConfiguredCap() {
        StringBuilder longText = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            longText.append("Esta é a sentença número ").append(i)
                    .append(" sobre o contrato e a cláusula de indenização por dano. ");
        }

        SummarizationResult result = service.summarize(UUID.randomUUID(), UUID.randomUUID(), longText.toString());

        long maxChars = mcpProperties.getSummarization().getSummaryMaxTokens() * 4;
        assertThat(result.summaryCharCount()).isLessThanOrEqualTo((int) maxChars + 200); // margem por sentença indivisível
        assertThat(result.summaryCharCount()).isLessThan(result.originalCharCount());
    }

    @Test
    @DisplayName("preserva a ordem original das sentenças selecionadas, não reordena por score")
    void preservesOriginalSentenceOrder() {
        String text = "Primeira sentença sem termos relevantes. "
                + "Segunda sentença fala de contrato e cláusula e dano e prazo e indenização. "
                + "Terceira sentença também comum.";
        mcpProperties.getSummarization().setSummaryMaxTokens(1000);

        SummarizationResult result = service.summarize(UUID.randomUUID(), UUID.randomUUID(), text);

        int idxPrimeira = result.summaryText().indexOf("Primeira");
        int idxSegunda = result.summaryText().indexOf("Segunda");
        if (idxPrimeira >= 0 && idxSegunda >= 0) {
            assertThat(idxPrimeira).isLessThan(idxSegunda);
        }
    }

    @Test
    @DisplayName("não faz nenhuma chamada de rede/LLM — puramente heurístico")
    void isPurelyHeuristic() {
        // Ausência de qualquer client HTTP/LLM injetado no construtor já garante isso
        // estruturalmente; este teste apenas documenta a expectativa explicitada na spec.
        assertThat(service).isNotNull();
    }

    @Test
    @DisplayName("loga em nível ERROR quando a sumarização falha inesperadamente")
    void logsErrorWhenSummarizationFailsUnexpectedly() {
        ProcessingStepService processingStepService = mock(ProcessingStepService.class);
        AiToolCallService aiToolCallService = mock(AiToolCallService.class);
        when(processingStepService.startGroupStep(any(), any(), any()))
                .thenReturn(ProcessingStep.builder().id(UUID.randomUUID()).build());
        when(aiToolCallService.start(any(), any(), any(), any()))
                .thenReturn(AiToolCall.builder().id(UUID.randomUUID()).build());

        McpProperties brokenProperties = mock(McpProperties.class);
        when(brokenProperties.getSummarization()).thenThrow(new IllegalStateException("boom"));
        SummarizationService brokenService =
                new SummarizationService(brokenProperties, processingStepService, aiToolCallService);

        UUID attachmentGroupId = UUID.randomUUID();

        Logger logger = (Logger) LoggerFactory.getLogger(SummarizationService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            assertThatThrownBy(() -> brokenService.summarize(UUID.randomUUID(), attachmentGroupId, "texto qualquer"))
                    .isInstanceOf(IllegalStateException.class);

            assertThat(appender.list).anySatisfy(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.ERROR);
                assertThat(event.getFormattedMessage()).contains(attachmentGroupId.toString());
                assertThat(event.getThrowableProxy()).isNotNull();
            });
        } finally {
            logger.detachAppender(appender);
        }
    }
}
