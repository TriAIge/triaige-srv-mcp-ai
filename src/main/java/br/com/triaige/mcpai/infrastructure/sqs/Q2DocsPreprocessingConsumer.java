package br.com.triaige.mcpai.infrastructure.sqs;

import br.com.triaige.mcpai.application.service.DocumentPipelineService;
import br.com.triaige.mcpai.infrastructure.config.McpProperties;
import br.com.triaige.mcpai.infrastructure.sqs.message.DocumentReadyMessage;
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
 * Consome Q2 (triaige-docs-preprocessing). Mensagens com schemaVersion
 * não suportada ou sem documentos NÃO são deletadas (nem tratadas em retry silencioso: o
 * erro é logado a cada tentativa) — após esgotar o maxReceiveCount configurado na fila,
 * a própria SQS as move para a DLQ via redrive policy (infraestrutura), mesma convenção
 * do Q1DocumentReceivedConsumer do triaige-srv-orchestrator.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class Q2DocsPreprocessingConsumer {

    private final SqsClient sqsClient;
    private final McpProperties mcpProperties;
    private final ObjectMapper objectMapper;
    private final DocumentPipelineService documentPipelineService;

    @Scheduled(fixedDelayString = "${mcp.queue.consumer.poll-interval-ms:5000}")
    public void poll() {
        if (!mcpProperties.getQueue().getConsumer().isEnabled()) {
            return;
        }

        String queueUrl = mcpProperties.getQueue().getDocsPreprocessingQueueUrl();
        if (queueUrl == null || queueUrl.isBlank()) {
            return;
        }

        List<Message> messages = sqsClient.receiveMessage(ReceiveMessageRequest.builder()
                        .queueUrl(queueUrl)
                        .maxNumberOfMessages(mcpProperties.getQueue().getConsumer().getMaxMessages())
                        .waitTimeSeconds(mcpProperties.getQueue().getConsumer().getWaitTimeSeconds())
                        .build())
                .messages();

        for (Message message : messages) {
            processMessage(message, queueUrl);
        }
    }

    private void processMessage(Message message, String queueUrl) {
        try {
            DocumentReadyMessage event = objectMapper.readValue(message.body(), DocumentReadyMessage.class);
            documentPipelineService.handleNewSession(event);
            deleteMessage(queueUrl, message);
        } catch (Exception e) {
            log.error("Failed to process Q2 message, will retry on next poll (or DLQ after maxReceiveCount): "
                    + "messageId={}", message.messageId(), e);
        }
    }

    private void deleteMessage(String queueUrl, Message message) {
        sqsClient.deleteMessage(DeleteMessageRequest.builder()
                .queueUrl(queueUrl)
                .receiptHandle(message.receiptHandle())
                .build());
    }
}
