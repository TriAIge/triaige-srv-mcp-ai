package br.com.triaige.mcpai.infrastructure.sqs;

import br.com.triaige.mcpai.application.service.DocumentPipelineService;
import br.com.triaige.mcpai.infrastructure.config.McpProperties;
import br.com.triaige.mcpai.infrastructure.sqs.message.OcrRetryMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

import java.util.List;

/**
 * Consome triaige-mcp-ocr-retry. Cada mensagem já carrega o
 * attemptNumber; o próprio {@link DocumentPipelineService} decide, com base nesse número
 * e no limite configurado, se republica com o próximo delay ou marca OCR_FAILED
 * definitivamente. Mensagens não deletadas em caso de erro inesperado são cobertas pelo
 * redrive policy da fila (maxReceiveCount=3 -> triaige-mcp-ocr-retry-dlq) como rede de
 * segurança contra falha/crash do processo, não como mecanismo primário de contagem de
 * tentativas.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OcrRetryConsumer {

    private final SqsClient sqsClient;
    private final McpProperties mcpProperties;
    private final ObjectMapper objectMapper;
    private final DocumentPipelineService documentPipelineService;

    @Scheduled(fixedDelayString = "${mcp.ocr.retry-consumer.poll-interval-ms:10000}")
    public void poll() {
        if (!mcpProperties.getOcr().getRetryConsumer().isEnabled()) {
            return;
        }

        String queueUrl = mcpProperties.getOcr().getRetryQueueUrl();
        if (queueUrl == null || queueUrl.isBlank()) {
            return;
        }

        List<Message> messages = sqsClient.receiveMessage(ReceiveMessageRequest.builder()
                        .queueUrl(queueUrl)
                        .maxNumberOfMessages(mcpProperties.getOcr().getRetryConsumer().getMaxMessages())
                        .waitTimeSeconds(mcpProperties.getOcr().getRetryConsumer().getWaitTimeSeconds())
                        .build())
                .messages();

        for (Message message : messages) {
            processMessage(message, queueUrl);
        }
    }

    private void processMessage(Message message, String queueUrl) {
        try {
            OcrRetryMessage event = objectMapper.readValue(message.body(), OcrRetryMessage.class);
            documentPipelineService.handleOcrRetry(event);
            deleteMessage(queueUrl, message);
        } catch (Exception e) {
            log.error("Failed to process OCR retry message, will retry on next poll (or DLQ after "
                    + "maxReceiveCount): messageId={}", message.messageId(), e);
        }
    }

    private void deleteMessage(String queueUrl, Message message) {
        sqsClient.deleteMessage(DeleteMessageRequest.builder()
                .queueUrl(queueUrl)
                .receiptHandle(message.receiptHandle())
                .build());
    }
}
