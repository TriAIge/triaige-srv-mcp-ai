package br.com.triaige.mcpai.infrastructure.sqs;

import br.com.triaige.mcpai.domain.exception.McpException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;

@Slf4j
@Component
@RequiredArgsConstructor
public class SqsQueuePublisher implements QueuePublisher {

    private final SqsClient sqsClient;
    private final ObjectMapper objectMapper;

    @Override
    public void publish(String queueUrl, Object payload) {
        publish(queueUrl, payload, 0);
    }

    @Override
    public void publish(String queueUrl, Object payload, long delaySeconds) {
        try {
            String messageBody = objectMapper.writeValueAsString(payload);

            SendMessageRequest.Builder request = SendMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .messageBody(messageBody);
            if (delaySeconds > 0) {
                request.delaySeconds((int) Math.min(delaySeconds, 900));
            }

            SendMessageResponse response = sqsClient.sendMessage(request.build());
            log.info("Message published: queue={}, messageId={}, delaySeconds={}", queueUrl, response.messageId(), delaySeconds);
        } catch (Exception e) {
            log.error("Failed to publish message to queue: {}", queueUrl, e);
            throw new QueuePublishingException(queueUrl, e);
        }
    }

    private static class QueuePublishingException extends McpException {
        QueuePublishingException(String queueUrl, Throwable cause) {
            super("QUEUE_PUBLISH_FAILED", "Falha ao publicar mensagem na fila: " + queueUrl, cause);
        }
    }
}
