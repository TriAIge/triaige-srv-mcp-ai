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

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * T3 (Definition of Done): ordem de concatenação correta com 1, 2 e N
 * partes, e roteamento T3->T4 configurável testado nos dois limiares (tokens e páginas).
 */
class AttachmentGroupingServiceTest {

    private ProcessingStepService processingStepService;
    private AiToolCallService aiToolCallService;
    private McpProperties mcpProperties;
    private AttachmentGroupingService service;

    @BeforeEach
    void setUp() {
        processingStepService = mock(ProcessingStepService.class);
        aiToolCallService = mock(AiToolCallService.class);
        mcpProperties = new McpProperties();
        mcpProperties.getSummarization().setThresholdTokens(6000);
        mcpProperties.getSummarization().setThresholdPages(15);

        when(processingStepService.startGroupStep(any(), any(), any()))
                .thenReturn(ProcessingStep.builder().id(UUID.randomUUID()).build());
        when(aiToolCallService.start(any(), any(), any(), any()))
                .thenReturn(AiToolCall.builder().id(UUID.randomUUID()).build());

        service = new AttachmentGroupingService(mcpProperties, processingStepService, aiToolCallService);
    }

    @Test
    @DisplayName("agrupa corretamente com 1 parte")
    void groupsSinglePart() {
        List<GroupPart> parts = List.of(part(1, "texto único"));
        GroupingResult result = service.group(UUID.randomUUID(), UUID.randomUUID(), parts);

        assertThat(result.concatenatedText()).contains("--- parte 1 ---").contains("texto único");
        assertThat(result.routeToSummarization()).isFalse();
    }

    @Test
    void groupsTwoPartsInOrderRegardlessOfInputOrder() {
        // partes fora de ordem no input — o serviço deve reordenar por partNumber
        List<GroupPart> parts = List.of(part(2, "segunda parte"), part(1, "primeira parte"));
        GroupingResult result = service.group(UUID.randomUUID(), UUID.randomUUID(), parts);

        int idxParte1 = result.concatenatedText().indexOf("primeira parte");
        int idxParte2 = result.concatenatedText().indexOf("segunda parte");
        assertThat(idxParte1).isGreaterThanOrEqualTo(0);
        assertThat(idxParte2).isGreaterThan(idxParte1);
    }

    @Test
    void groupsNPartsPreservingOrder() {
        List<GroupPart> parts = List.of(part(3, "c"), part(1, "a"), part(2, "b"), part(4, "d"));
        GroupingResult result = service.group(UUID.randomUUID(), UUID.randomUUID(), parts);

        String text = result.concatenatedText();
        int a = text.indexOf("--- parte 1"), b = text.indexOf("--- parte 2");
        int c = text.indexOf("--- parte 3"), d = text.indexOf("--- parte 4");
        assertThat(a).isLessThan(b);
        assertThat(b).isLessThan(c);
        assertThat(c).isLessThan(d);
    }

    @Test
    @DisplayName("roteia para T4 quando o limiar de tokens estimados é excedido")
    void routesToSummarizationWhenTokenThresholdExceeded() {
        mcpProperties.getSummarization().setThresholdTokens(10);
        mcpProperties.getSummarization().setThresholdPages(1000);

        String longText = "palavra ".repeat(50); // muito acima de 10 tokens estimados (chars/4)
        GroupingResult result = service.group(UUID.randomUUID(), UUID.randomUUID(), List.of(part(1, longText)));

        assertThat(result.routeToSummarization()).isTrue();
    }

    @Test
    void routesToSummarizationWhenPageThresholdExceeded() {
        mcpProperties.getSummarization().setThresholdTokens(1_000_000);
        mcpProperties.getSummarization().setThresholdPages(2);

        GroupPart heavyPart = new GroupPart(UUID.randomUUID(), 1, "texto curto", 20, "arquivo.pdf", "peticao");
        GroupingResult result = service.group(UUID.randomUUID(), UUID.randomUUID(), List.of(heavyPart));

        assertThat(result.routeToSummarization()).isTrue();
    }

    @Test
    void doesNotRouteToSummarizationWhenBelowBothThresholds() {
        GroupingResult result = service.group(UUID.randomUUID(), UUID.randomUUID(), List.of(part(1, "texto pequeno")));
        assertThat(result.routeToSummarization()).isFalse();
    }

    @Test
    @DisplayName("loga em nível ERROR quando o agrupamento falha inesperadamente")
    void logsErrorWhenGroupingFailsUnexpectedly() {
        McpProperties brokenProperties = mock(McpProperties.class);
        when(brokenProperties.getSummarization()).thenThrow(new IllegalStateException("boom"));
        AttachmentGroupingService brokenService =
                new AttachmentGroupingService(brokenProperties, processingStepService, aiToolCallService);

        UUID attachmentGroupId = UUID.randomUUID();
        List<GroupPart> parts = List.of(part(1, "texto"));

        Logger logger = (Logger) LoggerFactory.getLogger(AttachmentGroupingService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            assertThatThrownBy(() -> brokenService.group(UUID.randomUUID(), attachmentGroupId, parts))
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

    private GroupPart part(int partNumber, String text) {
        return new GroupPart(UUID.randomUUID(), partNumber, text, 1, "arquivo-" + partNumber + ".pdf", "peticao");
    }
}
