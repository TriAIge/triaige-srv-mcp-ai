package br.com.triaige.mcpai.application.service;

import br.com.triaige.mcpai.domain.entity.AiToolCall;
import br.com.triaige.mcpai.domain.entity.LegalDocument;
import br.com.triaige.mcpai.domain.entity.ProcessingStep;
import br.com.triaige.mcpai.domain.enums.DocumentStatus;
import br.com.triaige.mcpai.domain.exception.OcrProcessingException;
import br.com.triaige.mcpai.domain.exception.UnsupportedDocumentFormatException;
import br.com.triaige.mcpai.infrastructure.config.McpProperties;
import br.com.triaige.mcpai.infrastructure.persistence.LegalDocumentRepository;
import br.com.triaige.mcpai.infrastructure.s3.S3DocumentReader;
import br.com.triaige.mcpai.infrastructure.textract.TextractOcrClient;
import br.com.triaige.mcpai.shared.metrics.McpMetrics;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Regressão para o bug relatado: um documento que falha T1 (OCR) por motivo esperado
 * (formato não suportado, objeto inexistente no S3 raw, falha do Textract) só gravava a
 * causa em {@code legal_documents.error_message} — nada aparecia em {@code docker logs}, o
 * que exigia acesso direto ao banco para diagnosticar em produção.
 */
class OcrServiceTest {

    private S3DocumentReader s3DocumentReader;
    private TextractOcrClient textractOcrClient;
    private McpProperties mcpProperties;
    private LegalDocumentRepository legalDocumentRepository;
    private ProcessingStepService processingStepService;
    private AiToolCallService aiToolCallService;
    private McpMetrics metrics;
    private OcrService service;

    private Logger logger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        s3DocumentReader = mock(S3DocumentReader.class);
        textractOcrClient = mock(TextractOcrClient.class);
        mcpProperties = new McpProperties();
        legalDocumentRepository = mock(LegalDocumentRepository.class);
        processingStepService = mock(ProcessingStepService.class);
        aiToolCallService = mock(AiToolCallService.class);
        metrics = mock(McpMetrics.class);

        when(processingStepService.startDocumentStep(any(), any(), any()))
                .thenReturn(ProcessingStep.builder().id(UUID.randomUUID()).build());
        when(aiToolCallService.start(any(), any(), any(), any()))
                .thenReturn(AiToolCall.builder().id(UUID.randomUUID()).build());

        service = new OcrService(s3DocumentReader, textractOcrClient, mcpProperties, legalDocumentRepository,
                processingStepService, aiToolCallService, metrics);

        logger = (Logger) LoggerFactory.getLogger(OcrService.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
    }

    @Test
    @DisplayName("loga em WARN o motivo de rejeição quando o content-type não é suportado")
    void logsWarnWhenContentTypeUnsupported() {
        LegalDocument document = LegalDocument.builder()
                .id(UUID.randomUUID())
                .contentType("text/plain")
                .rawBucket("bucket-raw")
                .rawObjectKey("raw/some-key.txt")
                .status(DocumentStatus.QUEUED)
                .build();
        when(legalDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));

        assertThatThrownBy(() -> service.process(UUID.randomUUID(), document))
                .isInstanceOf(UnsupportedDocumentFormatException.class);

        assertThat(appender.list).anySatisfy(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(event.getFormattedMessage()).contains("UNSUPPORTED_FORMAT").contains(document.getId().toString());
        });
    }

    @Test
    @DisplayName("loga em WARN o motivo, code e retryable quando o Textract falha")
    void logsWarnWhenTextractProcessingFails() {
        LegalDocument document = LegalDocument.builder()
                .id(UUID.randomUUID())
                .contentType("application/pdf")
                .rawBucket("bucket-raw")
                .rawObjectKey("raw/some-key.pdf")
                .status(DocumentStatus.QUEUED)
                .build();
        when(legalDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));
        when(s3DocumentReader.headObject(document.getRawBucket(), document.getRawObjectKey()))
                .thenReturn(Optional.of(1024L));
        when(textractOcrClient.extractText(document.getRawBucket(), document.getRawObjectKey()))
                .thenThrow(new OcrProcessingException("TEXTRACT_TIMEOUT", "Textract nao respondeu a tempo", true));

        assertThatThrownBy(() -> service.process(UUID.randomUUID(), document))
                .isInstanceOf(OcrProcessingException.class);

        assertThat(appender.list).anySatisfy(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(event.getFormattedMessage())
                    .contains("TEXTRACT_TIMEOUT")
                    .contains("retryable=true")
                    .contains(document.getId().toString());
        });
    }
}
